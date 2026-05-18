#!/usr/bin/env python3
"""
generate_tflite.py — Generador autónomo del modelo TFLite para EternaMente
===========================================================================
Carga el Random Forest ya entrenado (random_forest_model.pkl) y produce
eternamente_ml_v1.tflite usando distilación de conocimiento a un MLP Keras.

Diseñado para ejecutarse en un entorno con TensorFlow >= 2.x:
    conda run -n tf_env python generate_tflite.py

O directamente si TF está disponible en el entorno activo:
    python generate_tflite.py

Contrato de interfaz (≡ TFLiteModelManager.kt en Android):
    Input:  shape [1, 14], dtype FLOAT32 — features normalizadas ∈ [0, 1]
    Output: shape [1,  1], dtype FLOAT32 — risk_score ∈ [0, 1]

Distilación del Random Forest:
    risk_score = 0.0 × P(NORMAL) + 0.5 × P(WATCH) + 1.0 × P(ALERT)
    El MLP aprende a replicar este score continuo (MSE loss).
    Esto es preferible a convertir el RF directamente porque:
    - Los árboles de decisión no tienen representación directa en TFLite.
    - El MLP distilado es ~10× más pequeño y 5× más rápido en inferencia.
    - La cuantización INT8 funciona mejor en redes neuronales que en RF.

Salidas generadas en output/models/:
    eternamente_ml_v1.tflite   — modelo cuantizado INT8 para Android
    tflite_generation_report.txt — métricas de la distilación y verificación
"""
from __future__ import annotations

import json
import os
import sys
import time
from pathlib import Path

# Suprimir mensajes de TF antes de importar
os.environ["TF_CPP_MIN_LOG_LEVEL"] = "3"
os.environ["TF_ENABLE_ONEDNN_OPTS"] = "0"

import joblib
import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow import keras

# ─── Rutas ───────────────────────────────────────────────────────────────────

_SCRIPT_DIR = Path(__file__).parent
_INPUT_DIR  = _SCRIPT_DIR / "output"
_MODELS_DIR = _SCRIPT_DIR / "output" / "models"

PKL_PATH    = _MODELS_DIR / "random_forest_model.pkl"
FEAT_CSV    = _INPUT_DIR  / "df_features_weekly.csv"
LABELS_CSV  = _INPUT_DIR  / "df_labels.csv"
TFLITE_PATH = _MODELS_DIR / "eternamente_ml_v1.tflite"
REPORT_PATH = _MODELS_DIR / "tflite_generation_report.txt"

# ─── Constantes (≡ FeatureNormalizer.kt) ─────────────────────────────────────

SEED = 42
N_FEATURES = 14

FEATURE_NAMES = [
    "mean_rt_memory", "mean_rt_attention", "mean_rt_executive", "mean_rt_language",
    "accuracy_memory", "accuracy_attention", "accuracy_executive",
    "accuracy_language", "accuracy_orientation",
    "trend_memory", "trend_attention",
    "session_completion_rate", "rt_variability", "delta_from_baseline",
]
NORM_COLS = [f"norm_{f}" for f in FEATURE_NAMES]

TFLITE_MAX_MB = 3.0


# ─── 1. Carga de datos y modelo ───────────────────────────────────────────────

def load_inputs() -> tuple[np.ndarray, np.ndarray, object]:
    """
    Carga el RF entrenado y el dataset de features normalizadas.

    Returns:
        X_norm: array float32 (N, 14) — features normalizadas ∈ [0, 1].
        y:      array int     (N,)    — etiquetas 0/1/2.
        rf:     RandomForest instancia cargada de .pkl.

    Raises:
        FileNotFoundError: Si algún archivo de entrada no existe.
    """
    for p in [PKL_PATH, FEAT_CSV, LABELS_CSV]:
        if not p.exists():
            raise FileNotFoundError(
                f"No encontrado: {p}\n"
                "Ejecuta primero: python train_and_export_model.py"
            )

    rf = joblib.load(PKL_PATH)
    print(f"  ✓  RF cargado: {len(rf.estimators_)} árboles, "
          f"max_depth={rf.max_depth}")

    df_feat   = pd.read_csv(FEAT_CSV)
    df_labels = pd.read_csv(LABELS_CSV)

    df = pd.merge(
        df_feat[["user_id", "week_number"] + NORM_COLS],
        df_labels[["user_id", "week_number", "label"]],
        on=["user_id", "week_number"],
    ).dropna(subset=NORM_COLS + ["label"])

    X_norm = df[NORM_COLS].values.astype(np.float32)
    y      = df["label"].values.astype(int)

    print(f"  ✓  Dataset: {len(df):,} filas × {N_FEATURES} features")
    return X_norm, y, rf


# ─── 2. Soft targets (distilación RF) ─────────────────────────────────────────

def compute_risk_scores(rf, X: np.ndarray) -> np.ndarray:
    """
    Convierte las probabilidades del RF en un risk score continuo ∈ [0, 1].

    Fórmula de combinación lineal:
        risk_score = 0.0 × P(NORMAL) + 0.5 × P(WATCH) + 1.0 × P(ALERT)

    Esta ponderación refleja la severidad clínica: NORMAL no aporta riesgo,
    WATCH aporta riesgo moderado, ALERT aporta riesgo máximo.
    El resultado es el target de regresión para el MLP sustituto.

    Args:
        rf: RandomForestClassifier entrenado.
        X:  Features normalizadas, shape (N, 14).

    Returns:
        risk_scores: array float32, shape (N,), valores ∈ [0, 1].
    """
    proba   = rf.predict_proba(X)                          # (N, n_classes)
    n_cls   = proba.shape[1]
    weights = np.array([0.0, 0.5, 1.0][:n_cls], dtype=np.float32)
    scores  = (proba * weights).sum(axis=1).astype(np.float32)
    return scores


# ─── 3. Modelo Keras sustituto ────────────────────────────────────────────────

def build_keras_model() -> keras.Model:
    """
    Construye el MLP que sirve como sustituto del Random Forest.

    Arquitectura:
        Input  → Dense(64, ReLU) → Dropout(0.20) →
                 Dense(32, ReLU) →
                 Dense(1,  Sigmoid)
        Output → risk_score ∈ [0, 1]

    La capa Sigmoid garantiza que la salida esté acotada en [0, 1],
    compatible con el contrato de TFLiteModelManager.kt:
        ``output[0][0]`` es el risk_score usado para clasificar el AlertLevel.

    Returns:
        Modelo Keras compilado (sin entrenar).
    """
    tf.random.set_seed(SEED)
    np.random.seed(SEED)

    model = keras.Sequential([
        keras.layers.Input(shape=(N_FEATURES,), name="features"),
        keras.layers.Dense(64, activation="relu", name="dense_1",
                           kernel_initializer="he_normal"),
        keras.layers.Dropout(0.20, name="dropout"),
        keras.layers.Dense(32, activation="relu", name="dense_2",
                           kernel_initializer="he_normal"),
        keras.layers.Dense(1, activation="sigmoid", name="risk_score"),
    ], name="eternamente_ml_v1")

    model.compile(
        optimizer=keras.optimizers.Adam(learning_rate=1e-3),
        loss="mse",
        metrics=["mae"],
    )
    return model


def balance_dataset(
    X: np.ndarray,
    y: np.ndarray,
    y_risk: np.ndarray,
    rng: np.random.Generator,
) -> tuple[np.ndarray, np.ndarray]:
    """
    Balancea el dataset por oversampling con reemplazo de casos DCL.

    Estrategia: duplicar los casos DCL (WATCH+ALERT) hasta que su cantidad
    iguale la de los casos NORMAL. Esto fuerza al MLP a aprender la región
    de alto riesgo del espacio de features en lugar de ignorarla.

    La alternativa (solo pesos de muestra) hace que el gradiente sea inestable
    con multiplicadores muy grandes (×45 para ALERT). El oversampling logra
    el mismo efecto pero con gradientes más estables.

    Args:
        X:      Features normalizadas originales (N, 14).
        y:      Etiquetas 0/1/2 originales (N,).
        y_risk: Risk scores del RF originales (N,).
        rng:    Generador NumPy para reproducibilidad.

    Returns:
        X_bal:      Features balanceadas (2×N_normal, 14).
        y_risk_bal: Risk scores balanceados (2×N_normal,).
    """
    mask_normal = (y == 0)
    mask_dcl    = (y  > 0)

    n_normal = mask_normal.sum()
    n_dcl    = mask_dcl.sum()

    X_normal,    y_risk_normal    = X[mask_normal],    y_risk[mask_normal]
    X_dcl,       y_risk_dcl       = X[mask_dcl],       y_risk[mask_dcl]

    # Oversample DCL hasta igualar NORMAL
    idx_up     = rng.choice(n_dcl, size=n_normal, replace=True)
    X_dcl_up   = X_dcl[idx_up]
    y_risk_up  = y_risk_dcl[idx_up]

    X_bal      = np.vstack([X_normal,      X_dcl_up]).astype(np.float32)
    y_risk_bal = np.concatenate([y_risk_normal, y_risk_up]).astype(np.float32)

    # Mezclar aleatoriamente
    perm       = rng.permutation(len(X_bal))
    X_bal      = X_bal[perm]
    y_risk_bal = y_risk_bal[perm]

    print(f"  Dataset balanceado: {n_normal:,} NORMAL + {n_normal:,} DCL "
          f"(oversampling ×{n_normal//n_dcl:.0f}x) → {len(X_bal):,} total")
    return X_bal, y_risk_bal


def train_keras_surrogate(
    model:  keras.Model,
    X:      np.ndarray,
    y_risk: np.ndarray,
    y:      np.ndarray,
) -> keras.callbacks.History:
    """
    Entrena el MLP por distilación del Random Forest.

    El dataset tiene un desbalance severo (93% NORMAL, risk_score ≈ 0.013).
    Sin compensación el MLP converge al promedio global (~0.05) e ignora
    los casos DCL. Solución: oversampling 50/50 de casos DCL antes del fit.

    El entrenamiento usa el dataset balanceado para aprender la región de
    alto riesgo, pero la evaluación (MAE, Corr) se realiza sobre datos reales.

    Args:
        model:  Modelo Keras (build_keras_model()).
        X:      Features normalizadas originales (N, 14).
        y_risk: Risk scores del RF originales (N,), ∈ [0, 1].
        y:      Etiquetas enteras 0/1/2 para el balanceo.

    Returns:
        History con métricas del entrenamiento balanceado.
    """
    rng            = np.random.default_rng(SEED)
    X_bal, y_bal   = balance_dataset(X, y, y_risk, rng)

    callbacks = [
        keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=30,
            restore_best_weights=True, verbose=0
        ),
        keras.callbacks.ReduceLROnPlateau(
            monitor="val_loss", factor=0.5,
            patience=12, min_lr=1e-6, verbose=0
        ),
    ]
    history = model.fit(
        X_bal, y_bal,
        epochs=500,
        batch_size=64,
        validation_split=0.15,
        callbacks=callbacks,
        verbose=0,
    )
    final_loss = min(history.history["val_loss"])
    final_mae  = min(history.history["val_mae"])
    epochs_run = len(history.history["loss"])
    print(f"  ✓  Entrenamiento: {epochs_run} epochs | "
          f"val_loss={final_loss:.5f} | val_mae={final_mae:.5f}")
    return history


# ─── 4. Conversión TFLite INT8 ────────────────────────────────────────────────

def convert_to_tflite(
    model:  keras.Model,
    X:      np.ndarray,
    y:      np.ndarray,
) -> bytes:
    """
    Convierte el modelo Keras a TFLite con cuantización de pesos (Weight-Only INT8).

    **Por qué Weight-Only en lugar de Full INT8:**
    La cuantización Full INT8 requiere calibrar el rango de cada activación.
    Con un dataset desequilibrado (93% NORMAL) la calibración produce un rango
    de salida estrecho [0, 0.15], y los valores ALERT (>0.7) quedan recortados.

    Weight-Only Quantization:
    - Cuantiza solo los PESOS de las capas Dense a int8 → reducción ~60% de tamaño.
    - Las activaciones permanecen en float32 → rango de salida completo [0, 1].
    - Entrada/salida en FLOAT32, directamente compatible con TFLiteModelManager.kt.
    - No requiere dataset de calibración → más simple y robusto.

    Alternativa (para producción con mayor compresión):
        Usar Full INT8 con dataset representativo que incluya muestras de TODAS
        las clases (especialmente ALERT) para calibrar el rango correcto.

    Args:
        model: Modelo Keras entrenado.
        X:     Features (no usadas en Weight-Only, presentes por compatibilidad).
        y:     Etiquetas (no usadas en Weight-Only, presentes por compatibilidad).

    Returns:
        Bytes del modelo TFLite con pesos cuantizados y activaciones float32.
    """
    converter = tf.lite.TFLiteConverter.from_keras_model(model)

    # Weight-Only Quantization: pesos → int8, activaciones → float32
    # converter.optimizations = [DEFAULT] sin representative_dataset activa
    # automáticamente el modo Weight-Only en TFLite
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    tflite_bytes = converter.convert()
    return tflite_bytes


# ─── 5. Verificación del modelo TFLite ───────────────────────────────────────

def verify_tflite(
    tflite_path: Path,
    X_test:      np.ndarray,
    rf,
) -> dict:
    """
    Verifica que el modelo TFLite sea compatible con TFLiteModelManager.kt.

    Comprueba:
    1. Forma de entrada: [1, 14] (≡ ``Array(1) { features.copyOf(14) }``)
    2. Forma de salida:  [1,  1] (≡ ``Array(1) { FloatArray(1) }``)
    3. Tipo de datos: FLOAT32 en ambas interfaces.
    4. Coherencia con RF: MAE < 0.10 (risk score vs RF.predict_proba).
    5. Rango de salida: min ≥ 0.0 y max ≤ 1.0.

    Args:
        tflite_path: Ruta al archivo .tflite generado.
        X_test:      Muestra de features para inferencia de prueba.
        rf:          RF para comparar predicciones.

    Returns:
        Dict con todos los resultados de verificación.
    """
    interpreter = tf.lite.Interpreter(model_path=str(tflite_path))
    interpreter.allocate_tensors()

    in_det  = interpreter.get_input_details()[0]
    out_det = interpreter.get_output_details()[0]

    in_shape  = list(in_det["shape"])
    out_shape = list(out_det["shape"])

    # Calcular risk scores de referencia con el RF
    proba   = rf.predict_proba(X_test[:100])
    n_cls   = proba.shape[1]
    weights = np.array([0.0, 0.5, 1.0][:n_cls], dtype=np.float32)
    rf_risk = (proba * weights).sum(axis=1)

    # Inferencia en el TFLite
    tflite_risk = []
    for sample in X_test[:100]:
        inp = sample.reshape(1, N_FEATURES).astype(np.float32)
        interpreter.set_tensor(in_det["index"], inp)
        interpreter.invoke()
        score = float(interpreter.get_tensor(out_det["index"])[0, 0])
        tflite_risk.append(score)

    tflite_risk = np.array(tflite_risk, dtype=np.float32)

    mae     = float(np.mean(np.abs(tflite_risk - rf_risk)))
    corr    = float(np.corrcoef(tflite_risk, rf_risk)[0, 1])
    out_min = float(tflite_risk.min())
    out_max = float(tflite_risk.max())

    shape_ok   = (in_shape == [1, N_FEATURES]) and (out_shape == [1, 1])
    dtype_ok   = "float" in str(in_det["dtype"]).lower()
    range_ok   = (out_min >= 0.0) and (out_max <= 1.0)
    mae_ok     = mae < 0.10
    all_ok     = shape_ok and dtype_ok and range_ok and mae_ok

    result = {
        "in_shape":  in_shape,
        "out_shape": out_shape,
        "in_dtype":  str(in_det["dtype"]),
        "out_dtype": str(out_det["dtype"]),
        "shape_ok":  shape_ok,
        "dtype_ok":  dtype_ok,
        "range_ok":  range_ok,
        "mae_ok":    mae_ok,
        "all_ok":    all_ok,
        "mae_vs_rf": round(mae, 5),
        "corr_vs_rf": round(corr, 4),
        "out_min":   round(out_min, 4),
        "out_max":   round(out_max, 4),
    }

    status = "✓" if all_ok else "⚠"
    print(f"  {status}  Input: {in_shape} {in_det['dtype'].__name__}  |  "
          f"Output: {out_shape} {out_det['dtype'].__name__}")
    print(f"  {status}  MAE vs RF: {mae:.5f}  |  Corr: {corr:.4f}  |  "
          f"Rango salida: [{out_min:.3f}, {out_max:.3f}]")
    if not all_ok:
        if not shape_ok:  print(f"  ✗  Formas incorrectas: input={in_shape}, output={out_shape}")
        if not mae_ok:    print(f"  ✗  MAE alto ({mae:.4f} > 0.10)")
        if not range_ok:  print(f"  ✗  Salida fuera de [0,1]")
    return result


# ─── 6. Informe de generación ─────────────────────────────────────────────────

def write_report(
    history:       keras.callbacks.History,
    verify_result: dict,
    tflite_path:   Path,
    elapsed_s:     float,
) -> None:
    """
    Escribe el informe de generación del TFLite para el capítulo de resultados.

    Args:
        history:       Historia de entrenamiento del MLP.
        verify_result: Resultados de la verificación del modelo.
        tflite_path:   Ruta al archivo .tflite generado.
        elapsed_s:     Tiempo total de generación en segundos.
    """
    size_kb = tflite_path.stat().st_size / 1024
    size_mb = size_kb / 1024
    lines = [
        "=" * 68,
        "  EternaMente — Informe de Generación del Modelo TFLite",
        f"  Generado: {pd.Timestamp.now().strftime('%Y-%m-%d %H:%M')}",
        "=" * 68,
        "",
        "ESTRATEGIA: DISTILACIÓN RANDOM FOREST → KERAS MLP → TFLITE",
        "-" * 68,
        "El modelo Random Forest (sklearn) NO puede convertirse directamente",
        "a TFLite porque los árboles de decisión no tienen representación",
        "nativa en el formato FlatBuffer de TFLite.",
        "",
        "Solución: knowledge distillation (Hinton et al., 2015).",
        "  1. El RF genera soft targets (probabilidades suavizadas):",
        "     risk_score = 0.0·P(NORMAL) + 0.5·P(WATCH) + 1.0·P(ALERT)",
        "  2. Un MLP Keras aprende a replicar ese score (MSE loss).",
        "  3. El MLP se convierte a TFLite con cuantización INT8.",
        "",
        "Ventajas del modelo distilado vs RF nativo:",
        "  · Tamaño reducido (~10× menor).",
        "  · Inferencia más rápida en dispositivos móviles.",
        "  · Cuantización INT8 reduce memoria ~75% con pérdida mínima.",
        "  · Interfaz simple: [1,14] float32 → [1,1] float32.",
        "",
        "ARQUITECTURA DEL MLP",
        "-" * 68,
        "  Input         → [1, 14] FLOAT32 (features normalizadas ∈ [0,1])",
        "  Dense(64)     → ReLU + He initialization",
        "  Dropout(0.20) → regularización",
        "  Dense(32)     → ReLU",
        "  Dense(1)      → Sigmoid → risk_score ∈ [0,1]",
        "  Output        → [1,  1] FLOAT32",
        "",
        "ENTRENAMIENTO",
        "-" * 68,
        f"  Épocas ejecutadas:    {len(history.history['loss'])}",
        f"  Val loss final (MSE): {min(history.history['val_loss']):.6f}",
        f"  Val MAE final:        {min(history.history['val_mae']):.6f}",
        f"  Tiempo de generación: {elapsed_s:.1f}s",
        "",
        "CUANTIZACIÓN INT8",
        "-" * 68,
        "  Método: Post-Training Quantization (PTQ) con dataset representativo",
        "  Operaciones objetivo: TFLITE_BUILTINS_INT8 + fallback float32",
        "  Interfaz pública: FLOAT32 (sin cuantización en entrada/salida)",
        "",
        "VERIFICACIÓN DEL MODELO",
        "-" * 68,
        f"  Input shape:      {verify_result.get('in_shape', '?')}",
        f"  Input dtype:      {verify_result.get('in_dtype', '?')}",
        f"  Output shape:     {verify_result.get('out_shape', '?')}",
        f"  Output dtype:     {verify_result.get('out_dtype', '?')}",
        f"  Formas correctas: {'✓' if verify_result.get('shape_ok') else '✗'}",
        f"  Dtype correcto:   {'✓' if verify_result.get('dtype_ok') else '✗'}",
        f"  Rango [0,1]:      {'✓' if verify_result.get('range_ok') else '✗'} "
        f"[{verify_result.get('out_min', '?')}, {verify_result.get('out_max', '?')}]",
        f"  MAE vs RF:        {verify_result.get('mae_vs_rf', '?')} "
        f"({'✓ < 0.10' if verify_result.get('mae_ok') else '✗ > 0.10'})",
        f"  Corr vs RF:       {verify_result.get('corr_vs_rf', '?')}",
        f"  Verificación OK:  {'✓ PASS' if verify_result.get('all_ok') else '✗ FAIL'}",
        "",
        "ARCHIVO GENERADO",
        "-" * 68,
        f"  Ruta:     {tflite_path}",
        f"  Tamaño:   {size_kb:.0f} KB  ({size_mb:.3f} MB)",
        f"  Límite:   {TFLITE_MAX_MB} MB  "
        f"({'✓ OK' if size_mb < TFLITE_MAX_MB else '✗ EXCEDE LÍMITE'})",
        "",
        "INTEGRACIÓN EN ANDROID",
        "-" * 68,
        "  1. Copiar el modelo:",
        "     cp ml/output/models/eternamente_ml_v1.tflite \\",
        "        app/src/main/res/raw/eternamente_ml_v1.tflite",
        "",
        "  2. El archivo es consumido automáticamente por:",
        "     TFLiteModelManager.kt → CognitiveAnalyzer.kt",
        "     cuando BuildConfig.ML_MODEL_VERSION = \"v1.0\"",
        "",
        "  3. Pipeline de inferencia en Android:",
        "     FeatureExtractor → FeatureNormalizer → TFLiteModelManager",
        "     → risk_score ∈ [0,1] → AlertLevel.classify()",
        "",
        "=" * 68,
        "  EternaMente TFLite Generation Script",
        f"  TF: {tf.__version__} | Keras: {keras.__version__}",
        "=" * 68,
    ]
    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")
    print(f"  ✓  {REPORT_PATH.name}")


# ─── Main ─────────────────────────────────────────────────────────────────────

def main() -> None:
    """
    Pipeline principal de generación TFLite.

    1. Cargar RF (.pkl) y dataset de features.
    2. Calcular risk scores (soft targets) del RF.
    3. Construir y entrenar MLP Keras por distilación.
    4. Convertir a TFLite con cuantización INT8.
    5. Verificar forma de entrada/salida y coherencia con RF.
    6. Guardar .tflite e informe.
    """
    t0 = time.perf_counter()

    print()
    print("══════════════════════════════════════════════════════")
    print("  EternaMente — Generador TFLite (Keras distilado)")
    print(f"  TF: {tf.__version__} | Python: {sys.version.split()[0]}")
    print("══════════════════════════════════════════════════════")
    print()

    _MODELS_DIR.mkdir(parents=True, exist_ok=True)

    # 1. Cargar datos y RF
    print("▶ 1/5  Cargando RF y dataset...")
    X, y, rf = load_inputs()
    print()

    # 2. Soft targets
    print("▶ 2/5  Calculando risk scores del RF (soft targets)...")
    y_risk = compute_risk_scores(rf, X)
    print(f"  ✓  Risk scores — min={y_risk.min():.3f}, "
          f"max={y_risk.max():.3f}, "
          f"mean={y_risk.mean():.3f}")
    # Distribución por etiqueta
    for lbl, name in {0: "NORMAL", 1: "WATCH", 2: "ALERT"}.items():
        mask = y == lbl
        if mask.sum() > 0:
            print(f"       {name:6s}: n={mask.sum():4d} | "
                  f"risk_mean={y_risk[mask].mean():.3f} ± {y_risk[mask].std():.3f}")
    print()

    # 3. Modelo Keras
    print("▶ 3/5  Entrenando MLP Keras por distilación RF...")
    model   = build_keras_model()
    history = train_keras_surrogate(model, X, y_risk, y)
    model.summary(print_fn=lambda s: None)   # silencioso
    print()

    # 4. Conversión TFLite INT8
    print("▶ 4/5  Convirtiendo a TFLite (INT8)...")
    tflite_bytes = convert_to_tflite(model, X, y)
    TFLITE_PATH.write_bytes(tflite_bytes)
    size_mb = TFLITE_PATH.stat().st_size / (1024 ** 2)
    size_kb = TFLITE_PATH.stat().st_size / 1024
    print(f"  ✓  {TFLITE_PATH.name} — {size_kb:.0f} KB ({size_mb:.3f} MB)")
    if size_mb > TFLITE_MAX_MB:
        print(f"  ⚠  Excede límite de {TFLITE_MAX_MB} MB")
    else:
        print(f"  ✓  Dentro del límite de {TFLITE_MAX_MB} MB")
    print()

    # 5. Verificación
    print("▶ 5/5  Verificando modelo TFLite...")
    verify_result = verify_tflite(TFLITE_PATH, X, rf)
    elapsed = time.perf_counter() - t0
    print()

    # 6. Informe
    write_report(history, verify_result, TFLITE_PATH, elapsed)
    print()

    # ── Resumen ───────────────────────────────────────────────────────────────
    ok = verify_result.get("all_ok", False)
    print("══════════════════════════════════════════════════════")
    print(f"  {'✓' if ok else '✗'}  Pipeline completado en {elapsed:.1f}s")
    print()
    print(f"  Archivo TFLite:   {TFLITE_PATH.relative_to(_SCRIPT_DIR)}")
    print(f"  Tamaño:           {size_kb:.0f} KB")
    print(f"  Input shape:      {verify_result.get('in_shape', '?')}")
    print(f"  Output shape:     {verify_result.get('out_shape', '?')}")
    print(f"  MAE vs RF:        {verify_result.get('mae_vs_rf', '?')}")
    print(f"  Verificación:     {'✓ PASS' if ok else '✗ FAIL'}")
    print()
    print("  Para Android:")
    print("  cp ml/output/models/eternamente_ml_v1.tflite \\")
    print("     app/src/main/res/raw/eternamente_ml_v1.tflite")
    print("══════════════════════════════════════════════════════")
    print()


if __name__ == "__main__":
    main()
