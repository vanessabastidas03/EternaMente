#!/usr/bin/env python3
"""
train_and_export_model.py — EternaMente ML Training & Export Pipeline
======================================================================
Entrena y exporta el modelo de detección de deterioro cognitivo leve (DCL)
para EternaMente.

DEPENDENCIAS OBLIGATORIAS (siempre se requieren):
    pip install scikit-learn numpy pandas matplotlib seaborn scipy joblib

DEPENDENCIAS OPCIONALES para la exportación TFLite:
    pip install onnx skl2onnx onnxruntime               # ONNX
    pip install tensorflow                               # TFLite
    pip install onnx-tf                                  # ONNX → TF (experimental)

INPUT (en el mismo directorio, carpeta output/):
    df_features_weekly.csv   — 14 features normalizadas por usuario/semana
    df_labels.csv            — etiquetas NORMAL=0, WATCH=1, ALERT=2

OUTPUT (en output/models/):
    random_forest_model.pkl       — modelo sklearn serializado (siempre)
    random_forest_model.onnx      — modelo ONNX (si skl2onnx disponible)
    eternamente_ml_v1.tflite      — modelo TFLite INT8 (si tensorflow disponible)
    feature_importance.png        — importancia de features para la tesis
    confusion_matrix.png          — matriz de confusión del RF
    roc_curves.png                — curvas ROC por clase
    model_evaluation_report.txt   — métricas completas para el capítulo de resultados

Arquitectura de conversión:
    sklearn RandomForest
        │
        ├── skl2onnx ──────────► random_forest_model.onnx
        │                                  │
        │                          onnxruntime (validación)
        │                                  │
        │                        onnx-tf / Keras surrogate
        │                                  │
        └── tf.lite.TFLiteConverter ──► eternamente_ml_v1.tflite (INT8)

Modelo TFLite — contrato de interfaz (≡ TFLiteModelManager.kt):
    Input:  shape [1, 14], dtype FLOAT32, valores ∈ [0, 1] (pre-normalizados)
    Output: shape [1,  1], dtype FLOAT32, valor  ∈ [0, 1] (risk score)

Uso:
    python train_and_export_model.py

    # Especificar rutas alternativas:
    python train_and_export_model.py --input_dir /ruta/output --output_dir /ruta/models
"""

from __future__ import annotations

# ─── Stdlib ──────────────────────────────────────────────────────────────────
import argparse
import io
import os
import sys
import time
import warnings
from pathlib import Path
from typing import Dict, List, Optional, Tuple

warnings.filterwarnings("ignore")

# ─── Científicos (siempre disponibles) ───────────────────────────────────────
import joblib
import numpy as np
import pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns
from scipy import stats as scipy_stats

from sklearn.ensemble        import IsolationForest, RandomForestClassifier
from sklearn.model_selection import GridSearchCV, StratifiedKFold, train_test_split
from sklearn.metrics         import (
    accuracy_score, precision_score, recall_score, f1_score,
    roc_auc_score, confusion_matrix, classification_report,
    roc_curve, auc
)
from sklearn.preprocessing   import label_binarize

# ─── Opcionales — ONNX ───────────────────────────────────────────────────────
try:
    import onnx
    from skl2onnx import convert_sklearn
    from skl2onnx.common.data_types import FloatTensorType
    _HAS_ONNX = True
except ImportError:
    _HAS_ONNX = False

try:
    import onnxruntime as ort
    _HAS_ORT = True
except ImportError:
    _HAS_ORT = False

# ─── Opcionales — TensorFlow / TFLite ────────────────────────────────────────
try:
    import tensorflow as tf
    _HAS_TF = True
except ImportError:
    _HAS_TF = False

try:
    from onnx_tf.backend import prepare as onnx_tf_prepare
    _HAS_ONNX_TF = True
except ImportError:
    _HAS_ONNX_TF = False


# ─── Constantes ──────────────────────────────────────────────────────────────

SEED = 42

# Nombres de las 14 features (≡ FeatureVector.NAMES en Android)
FEATURE_NAMES: List[str] = [
    "mean_rt_memory",        # [0]  RT medio MEMORY (ms) → norma [200, 5000]
    "mean_rt_attention",     # [1]  RT medio ATTENTION
    "mean_rt_executive",     # [2]  RT medio EXECUTIVE
    "mean_rt_language",      # [3]  RT medio LANGUAGE
    "accuracy_memory",       # [4]  Accuracy MEMORY (%)   → norma [0, 100]
    "accuracy_attention",    # [5]  Accuracy ATTENTION
    "accuracy_executive",    # [6]  Accuracy EXECUTIVE
    "accuracy_language",     # [7]  Accuracy LANGUAGE
    "accuracy_orientation",  # [8]  Accuracy ORIENTATION
    "trend_memory",          # [9]  Tendencia OLS MEMORY   → norma [-10, 10]
    "trend_attention",       # [10] Tendencia OLS ATTENTION
    "session_completion_rate", # [11] Tasa completitud     → norma [0, 1]
    "rt_variability",        # [12] CV tiempos reacción   → norma [0, 1.5]
    "delta_from_baseline",   # [13] Δ baseline (z-score)  → norma [-3, 3]
]

# Columnas normalizadas correspondientes en el CSV (prefijo norm_)
NORM_FEATURE_COLS: List[str] = [f"norm_{f}" for f in FEATURE_NAMES]

# Etiquetas
LABEL_NAMES = {0: "NORMAL", 1: "WATCH", 2: "ALERT"}
LABEL_COLORS = {"NORMAL": "#27ae60", "WATCH": "#e67e22", "ALERT": "#c0392b"}

# Grupos clínicos
NORMAL_GROUP   = "NORMAL"
DCL_GROUPS     = ["DCL_LEVE", "DCL_MODERADO"]

# Hiperparámetros base
IF_PARAMS = dict(contamination=0.15, n_estimators=100, random_state=SEED, n_jobs=-1)
RF_BASE   = dict(n_estimators=200, class_weight="balanced", random_state=SEED, n_jobs=-1)
RF_GRID   = {
    "max_depth":         [5, 8, 10],
    "min_samples_leaf":  [1, 2, 3],
}

TFLITE_MAX_SIZE_MB = 3.0

# Rutas por defecto
_SCRIPT_DIR  = Path(__file__).parent
_INPUT_DIR   = _SCRIPT_DIR / "output"
_OUTPUT_DIR  = _SCRIPT_DIR / "output" / "models"


# ─── 1. Carga y preparación de datos ─────────────────────────────────────────

def load_data(input_dir: Path) -> Tuple[pd.DataFrame, pd.DataFrame]:
    """
    Carga y une df_features_weekly.csv con df_labels.csv.

    Verifica que las 14 columnas normalizadas existan (generadas por
    ``generate_synthetic_data.py``). Las columnas ``norm_*`` son los valores
    ya escalados al rango [0, 1] con los bounds de ``FeatureNormalizer.kt``.

    Args:
        input_dir: Directorio que contiene los CSVs de entrada.

    Returns:
        Tupla (df_features, df_labels) ya fusionados y listos para entrenamiento.

    Raises:
        FileNotFoundError: Si alguno de los CSVs no existe.
        ValueError: Si faltan columnas de features normalizadas.
    """
    feat_path   = input_dir / "df_features_weekly.csv"
    labels_path = input_dir / "df_labels.csv"

    for p in [feat_path, labels_path]:
        if not p.exists():
            raise FileNotFoundError(
                f"Archivo no encontrado: {p}\n"
                "Ejecuta primero: python generate_synthetic_data.py"
            )

    df_feat   = pd.read_csv(feat_path)
    df_labels = pd.read_csv(labels_path)

    # Verificar columnas normalizadas
    missing = [c for c in NORM_FEATURE_COLS if c not in df_feat.columns]
    if missing:
        raise ValueError(
            f"Columnas norm_* faltantes en df_features_weekly.csv: {missing}\n"
            "Regenera los datos con generate_synthetic_data.py actualizado."
        )

    df = pd.merge(
        df_feat[["user_id", "group", "week_number"] + NORM_FEATURE_COLS],
        df_labels[["user_id", "week_number", "label", "label_name", "anomaly_score"]],
        on=["user_id", "week_number"],
        how="inner"
    )

    # Eliminar filas con NaN
    before = len(df)
    df = df.dropna(subset=NORM_FEATURE_COLS + ["label"])
    after = len(df)
    if before != after:
        print(f"  ⚠  {before - after} filas eliminadas por NaN")

    return df, df_labels


def extract_arrays(
    df: pd.DataFrame,
) -> Tuple[np.ndarray, np.ndarray, pd.Series]:
    """
    Extrae matrices NumPy para el entrenamiento.

    Args:
        df: DataFrame fusionado con features y labels.

    Returns:
        X: array float32 shape (N, 14) — features normalizadas.
        y: array int shape (N,) — etiquetas 0/1/2.
        groups: Series con user_id (para splits sin fuga de datos).
    """
    X      = df[NORM_FEATURE_COLS].values.astype(np.float32)
    y      = df["label"].values.astype(int)
    groups = df["user_id"]
    return X, y, groups


# ─── 2. Isolation Forest (no supervisado) ─────────────────────────────────────

def train_isolation_forest(
    df_all: pd.DataFrame,
) -> Tuple[IsolationForest, Dict]:
    """
    Entrena el Isolation Forest únicamente con datos de usuarios NORMAL.

    Filosofía one-class: el modelo aprende la distribución del rendimiento
    cognitivo normal. Cualquier desviación significativa se clasifica como
    anomalía (posible DCL).

    Configuración:
        contamination=0.15 — umbral que clasifica el 15% más extremo del
        conjunto de entrenamiento como anómalo (ajustado a la prevalencia
        esperada de DCL en adultos 60+).

    Args:
        df_all: DataFrame completo (todos los grupos y semanas).

    Returns:
        Tupla (modelo IF entrenado, dict con métricas de evaluación).
    """
    # Datos de entrenamiento: SÓLO usuarios NORMAL
    df_normal  = df_all[df_all["group"] == NORMAL_GROUP]
    X_normal   = df_normal[NORM_FEATURE_COLS].values.astype(np.float32)

    # Datos de evaluación: grupos DCL
    df_dcl     = df_all[df_all["group"].isin(DCL_GROUPS)]
    X_dcl      = df_dcl[NORM_FEATURE_COLS].values.astype(np.float32) if len(df_dcl) > 0 else None

    print(f"  IF — Entrenamiento: {len(X_normal):,} semanas de usuarios NORMAL")

    model = IsolationForest(**IF_PARAMS)
    model.fit(X_normal)

    # Evaluar en grupo NORMAL (especificidad)
    preds_normal = model.predict(X_normal)           # +1=normal, -1=anomalía
    specificity  = (preds_normal == 1).mean()

    # Evaluar en grupos DCL (sensibilidad = tasa de detección)
    metrics = {
        "n_train":      len(X_normal),
        "specificity":  round(specificity, 4),       # % NORMAL correctamente identificado
        "contamination": IF_PARAMS["contamination"],
    }

    if X_dcl is not None and len(X_dcl) > 0:
        preds_dcl   = model.predict(X_dcl)
        sensitivity = (preds_dcl == -1).mean()       # % DCL detectado como anomalía
        metrics["n_dcl"]        = len(X_dcl)
        metrics["sensitivity"]  = round(sensitivity, 4)
        print(f"  IF — Evaluación: {len(X_dcl):,} semanas DCL | "
              f"detección={sensitivity*100:.1f}% (objetivo >70%)")

        # Por subgrupo
        for group in DCL_GROUPS:
            df_g = df_all[df_all["group"] == group]
            if len(df_g) > 0:
                X_g = df_g[NORM_FEATURE_COLS].values.astype(np.float32)
                det = (model.predict(X_g) == -1).mean()
                metrics[f"sensitivity_{group}"] = round(det, 4)
                print(f"           {group:15s}: {det*100:.1f}%")

    return model, metrics


# ─── 3. Random Forest + GridSearchCV ─────────────────────────────────────────

def train_random_forest(
    X: np.ndarray,
    y: np.ndarray,
) -> Tuple[RandomForestClassifier, Dict]:
    """
    Entrena un RandomForestClassifier con búsqueda de hiperparámetros via
    GridSearchCV y validación cruzada estratificada (StratifiedKFold, k=5).

    Configuración base:
        n_estimators=200, class_weight='balanced', random_state=42
    Grid de búsqueda:
        max_depth: [5, 8, 10]
        min_samples_leaf: [1, 2, 3]

    El balanceo de clases es crítico dado el fuerte desbalance del dataset:
        NORMAL=93%, WATCH=6%, ALERT=1%.

    ``class_weight='balanced'`` pondera cada muestra inversamente
    proporcional a la frecuencia de su clase, lo que evita que el modelo
    aprenda a predecir siempre NORMAL.

    Args:
        X: Features normalizadas, shape (N, 14).
        y: Etiquetas 0/1/2, shape (N,).

    Returns:
        Tupla (mejor estimador RF, dict con resultados de GridSearchCV).
    """
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)

    base_rf = RandomForestClassifier(**RF_BASE)

    grid_search = GridSearchCV(
        estimator  = base_rf,
        param_grid = RF_GRID,
        cv         = cv,
        scoring    = "f1_weighted",     # métrica principal (robusta al desbalance)
        n_jobs     = -1,
        verbose    = 0,
        refit      = True,
        return_train_score = True,
    )
    grid_search.fit(X, y)

    best_rf = grid_search.best_estimator_
    results = {
        "best_params":       grid_search.best_params_,
        "best_f1_weighted":  round(grid_search.best_score_, 4),
        "cv_results":        grid_search.cv_results_,
    }

    print(f"  RF — Mejores params: {grid_search.best_params_}")
    print(f"  RF — F1-weighted CV: {grid_search.best_score_*100:.2f}%")

    return best_rf, results


# ─── 4. Evaluación completa del Random Forest ─────────────────────────────────

def evaluate_random_forest(
    model: RandomForestClassifier,
    X: np.ndarray,
    y: np.ndarray,
) -> Dict:
    """
    Evaluación completa del Random Forest en el conjunto de test.

    Métricas calculadas:
        - Accuracy global
        - Precision / Recall / F1 por clase y macro
        - AUC-ROC por clase (One-vs-Rest) y media ponderada
        - Matriz de confusión normalizada

    Nota sobre validación por usuario:
        El dataset tiene múltiples semanas por usuario. Para una evaluación
        sin fuga de datos temporal, en producción se recomienda split por
        usuario. Aquí se usa split aleatorio estratificado por etiqueta,
        lo que es aceptable para la exploración inicial del modelo.

    Args:
        model: RF entrenado (post GridSearchCV).
        X:     Features de evaluación, shape (N, 14).
        y:     Etiquetas verdaderas, shape (N,).

    Returns:
        Dict con todas las métricas de evaluación.
    """
    y_pred  = model.predict(X)
    y_proba = model.predict_proba(X)    # shape (N, n_classes)

    # Clases presentes en el modelo
    classes = model.classes_

    # ── Métricas estándar ─────────────────────────────────────────────────────
    acc       = accuracy_score(y, y_pred)
    prec_w    = precision_score(y, y_pred, average="weighted", zero_division=0)
    recall_w  = recall_score(y, y_pred, average="weighted", zero_division=0)
    f1_w      = f1_score(y, y_pred, average="weighted", zero_division=0)
    f1_macro  = f1_score(y, y_pred, average="macro", zero_division=0)
    cm        = confusion_matrix(y, y_pred, labels=sorted(set(y)))

    # ── AUC-ROC ──────────────────────────────────────────────────────────────
    n_classes = len(classes)
    y_bin     = label_binarize(y, classes=list(range(n_classes)))

    roc_data: Dict = {}
    aucs: List[float] = []

    for i, cls in enumerate(range(n_classes)):
        if i < y_proba.shape[1]:
            fpr, tpr, _ = roc_curve(y_bin[:, i], y_proba[:, i])
            auc_score   = auc(fpr, tpr)
            roc_data[LABEL_NAMES[cls]] = {"fpr": fpr, "tpr": tpr, "auc": auc_score}
            aucs.append(auc_score)

    auc_weighted = np.average(aucs, weights=np.bincount(y, minlength=n_classes) / len(y))
    auc_macro    = np.mean(aucs)

    metrics = {
        "n_test":        len(y),
        "accuracy":      round(acc, 4),
        "precision_w":   round(prec_w, 4),
        "recall_w":      round(recall_w, 4),
        "f1_weighted":   round(f1_w, 4),
        "f1_macro":      round(f1_macro, 4),
        "auc_weighted":  round(auc_weighted, 4),
        "auc_macro":     round(auc_macro, 4),
        "confusion_matrix": cm,
        "roc_data":      roc_data,
        "y_pred":        y_pred,
        "y_proba":       y_proba,
        "class_report":  classification_report(
            y, y_pred,
            target_names=[LABEL_NAMES[c] for c in sorted(set(y))],
            zero_division=0
        ),
    }
    return metrics


# ─── 5. Conversión a ONNX ────────────────────────────────────────────────────

def convert_to_onnx(
    rf_model: RandomForestClassifier,
    output_path: Path,
) -> Optional[Path]:
    """
    Convierte el RandomForest de sklearn a formato ONNX usando ``skl2onnx``.

    El modelo ONNX generado es funcionalmente equivalente al sklearn:
        - Input:  nombre='float_input', shape=[N, 14], dtype=FLOAT
        - Output: nombre='label' (clase) + 'probabilities' (proba por clase)

    Para usar en Android directamente habría que añadir un nodo de
    post-procesamiento (weighted sum) y convertir a TFLite. Ver
    ``convert_onnx_to_tflite`` para la ruta completa.

    Args:
        rf_model:    Modelo RF entrenado.
        output_path: Ruta de salida del archivo .onnx.

    Returns:
        Ruta al archivo .onnx si la conversión tuvo éxito, None en caso contrario.
    """
    if not _HAS_ONNX:
        print("  ⚠  skl2onnx no disponible — omitiendo exportación ONNX")
        print("     Instalar: pip install onnx skl2onnx")
        return None

    try:
        initial_type = [("float_input", FloatTensorType([None, len(FEATURE_NAMES)]))]
        # zipmap=False → output como array, no diccionario (más fácil de convertir)
        options  = {RandomForestClassifier: {"zipmap": False}}
        onnx_mdl = convert_sklearn(rf_model, initial_types=initial_type, options=options)

        with open(output_path, "wb") as f:
            f.write(onnx_mdl.SerializeToString())

        # Validar con onnxruntime si está disponible
        if _HAS_ORT:
            sess = ort.InferenceSession(str(output_path))
            dummy = np.zeros((1, len(FEATURE_NAMES)), dtype=np.float32)
            out   = sess.run(None, {"float_input": dummy})
            assert len(out) == 2, "Se esperaban 2 outputs: labels + probabilities"
            print(f"  ✓  ONNX validado con onnxruntime: "
                  f"output probas shape={out[1].shape}")

        size_kb = output_path.stat().st_size / 1024
        print(f"  ✓  {output_path.name} ({size_kb:.0f} KB)")
        return output_path

    except Exception as e:
        print(f"  ✗  Conversión ONNX fallida: {e}")
        return None


# ─── 6. Conversión a TFLite ──────────────────────────────────────────────────

def _build_keras_surrogate(
    X_train: np.ndarray,
    y_risk_train: np.ndarray,
) -> "tf.keras.Model":
    """
    Construye y entrena un modelo Keras MLP como sustituto del RandomForest.

    Estrategia de *knowledge distillation*:
        - El RF genera probabilidades suavizadas por clase (soft targets).
        - El MLP aprende a replicar el risk score continuo del RF:
          risk_score = 0.0 × P(NORMAL) + 0.5 × P(WATCH) + 1.0 × P(ALERT)
        - Pérdida MSE sobre el risk score → modelo de regresión en [0, 1].

    Arquitectura (≡ TFLiteModelManager.kt contrato de interfaz):
        Input  → [None, 14]   float32 (features normalizadas)
        Dense  → 64 ReLU
        Dropout → 0.20
        Dense  → 32 ReLU
        Dense  →  1 Sigmoid   float32 risk score ∈ [0, 1]
        Output → [None, 1]

    Args:
        X_train:      Features normalizadas de entrenamiento.
        y_risk_train: Risk scores del RF, shape (N,), valores ∈ [0, 1].

    Returns:
        Modelo Keras entrenado listo para conversión TFLite.
    """
    tf.random.set_seed(SEED)

    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(len(FEATURE_NAMES),), name="features"),
        tf.keras.layers.Dense(64, activation="relu",    name="hidden1"),
        tf.keras.layers.Dropout(0.20,                   name="dropout"),
        tf.keras.layers.Dense(32, activation="relu",    name="hidden2"),
        tf.keras.layers.Dense(1,  activation="sigmoid", name="risk_score"),
    ], name="eternamente_ml_v1")

    model.compile(
        optimizer = tf.keras.optimizers.Adam(learning_rate=1e-3),
        loss      = "mse",
        metrics   = ["mae"],
    )

    early_stop = tf.keras.callbacks.EarlyStopping(
        patience=15, restore_best_weights=True, verbose=0
    )
    lr_reduce = tf.keras.callbacks.ReduceLROnPlateau(
        patience=7, factor=0.5, min_lr=1e-6, verbose=0
    )

    model.fit(
        X_train, y_risk_train,
        epochs          = 200,
        batch_size      = 64,
        validation_split = 0.15,
        callbacks       = [early_stop, lr_reduce],
        verbose         = 0,
    )

    return model


def convert_to_tflite(
    rf_model:   RandomForestClassifier,
    onnx_path:  Optional[Path],
    X_train:    np.ndarray,
    y_train:    np.ndarray,
    output_path: Path,
) -> Optional[Path]:
    """
    Convierte el modelo a TFLite con cuantización INT8.

    Pipeline de conversión (intenta en orden, usa el primero que funcione):

    **Ruta A — ONNX → TF SavedModel → TFLite** (experimental):
        1. Carga el ONNX exportado con skl2onnx.
        2. Convierte a TF SavedModel con onnx-tf.
        3. Añade capa de post-procesamiento para risk score [0, 1].
        4. Aplica cuantización INT8 y convierte a TFLite.

    **Ruta B — Keras surrogate → TFLite** (fallback, más robusta):
        1. El RF genera risk scores suavizados (soft targets).
        2. Un MLP Keras aprende a replicar esos scores (distilación).
        3. Se cuantiza a INT8 con dataset representativo del train.
        4. Se verifica forma de entrada/salida [1, 14] → [1, 1].

    La cuantización INT8 reduce el tamaño ~75% manteniendo la precisión
    del modelo dentro de ±2% de diferencia de accuracy en producción.

    Args:
        rf_model:    Modelo RF para generar soft targets.
        onnx_path:   Ruta al ONNX exportado (None si no se generó).
        X_train:     Features normalizadas de entrenamiento.
        y_train:     Etiquetas para calcular risk scores.
        output_path: Ruta de salida del .tflite.

    Returns:
        Ruta al .tflite si la conversión tuvo éxito, None en caso contrario.
    """
    if not _HAS_TF:
        print("  ⚠  tensorflow no disponible — omitiendo exportación TFLite")
        print("     Instalar: pip install tensorflow")
        print("     (Ruta completa: pip install tensorflow onnx-tf)")
        return None

    # ── Risk scores del RF (usados como targets de distilación) ──────────────
    proba        = rf_model.predict_proba(X_train)   # (N, n_classes)
    n_classes    = proba.shape[1]
    weights      = np.array([0.0, 0.5, 1.0][:n_classes])
    y_risk       = (proba * weights[:n_classes]).sum(axis=1)   # [0, 1]

    # ── Intentar Ruta A (ONNX → TF → TFLite) ─────────────────────────────────
    tflite_model = None
    if onnx_path and onnx_path.exists() and _HAS_ONNX_TF:
        try:
            print("  → Intentando Ruta A: ONNX → onnx-tf → TFLite...")
            saved_model_dir = output_path.parent / "_tmp_saved_model"
            saved_model_dir.mkdir(exist_ok=True)

            # Cargar ONNX y exportar a TF SavedModel
            onnx_mdl = onnx.load(str(onnx_path))
            tf_rep   = onnx_tf_prepare(onnx_mdl)
            tf_rep.export_graph(str(saved_model_dir))

            # Post-procesamiento: convertir proba (N, 3) → risk_score (N, 1)
            # Se carga el SavedModel y se añade una capa de combinación lineal
            base_model = tf.saved_model.load(str(saved_model_dir))
            input_sig  = tf.TensorSpec(shape=[None, len(FEATURE_NAMES)],
                                       dtype=tf.float32, name="features")

            @tf.function(input_signature=[input_sig])
            def risk_fn(x):
                out   = base_model(x)
                proba_tf = out["probabilities"] if isinstance(out, dict) else out[1]
                weights_tf = tf.constant([0.0, 0.5, 1.0], dtype=tf.float32)
                return tf.reduce_sum(proba_tf * weights_tf, axis=1, keepdims=True)

            wrapped_dir = output_path.parent / "_tmp_wrapped"
            tf.saved_model.save(
                tf.Module(), str(wrapped_dir),
                signatures={"serving_default": risk_fn}
            )
            tflite_model = _tflite_from_saved_model(wrapped_dir, X_train)
            print("  ✓  Ruta A completada (ONNX → onnx-tf → TFLite)")

        except Exception as e:
            print(f"  ⚠  Ruta A falló: {e}")
            tflite_model = None

    # ── Ruta B: Keras surrogate → TFLite ─────────────────────────────────────
    if tflite_model is None:
        print("  → Usando Ruta B: Keras surrogate + distilación RF → TFLite")
        keras_model  = _build_keras_surrogate(X_train, y_risk)
        tflite_model = _tflite_from_keras(keras_model, X_train)

    # ── Guardar ───────────────────────────────────────────────────────────────
    output_path.write_bytes(tflite_model)
    size_mb = output_path.stat().st_size / (1024 ** 2)
    print(f"  ✓  {output_path.name} ({size_mb:.2f} MB)")

    if size_mb > TFLITE_MAX_SIZE_MB:
        print(f"  ⚠  Tamaño {size_mb:.2f} MB supera el límite de {TFLITE_MAX_SIZE_MB} MB")
        print("     Considera reducir n_estimators o usar un surrogate más pequeño.")
    else:
        print(f"  ✓  Tamaño dentro del límite ({TFLITE_MAX_SIZE_MB} MB) ✓")

    return output_path


def _tflite_from_keras(
    keras_model: "tf.keras.Model",
    X_repr: np.ndarray,
) -> bytes:
    """
    Convierte un modelo Keras a TFLite con cuantización INT8.

    La cuantización INT8 requiere un dataset representativo para calibración:
    el converter ejecuta inferencias de muestra para calcular los rangos de
    activación de cada capa y seleccionar los rangos de cuantización óptimos.

    Args:
        keras_model: Modelo Keras entrenado.
        X_repr:      Dataset representativo para calibración INT8 (200 muestras).

    Returns:
        Contenido binario del modelo TFLite cuantizado.
    """
    def representative_dataset_gen():
        """Generador de muestras de calibración para cuantización INT8."""
        for sample in X_repr[:200]:
            yield [sample.reshape(1, -1).astype(np.float32)]

    converter = tf.lite.TFLiteConverter.from_keras_model(keras_model)
    converter.optimizations             = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset    = representative_dataset_gen
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    # Mantener float32 en entrada/salida (compatibilidad con TFLiteModelManager.kt)
    converter.inference_input_type  = tf.float32
    converter.inference_output_type = tf.float32

    return converter.convert()


def _tflite_from_saved_model(
    saved_model_dir: Path,
    X_repr: np.ndarray,
) -> bytes:
    """
    Convierte un TF SavedModel a TFLite con cuantización INT8.

    Args:
        saved_model_dir: Directorio del TF SavedModel.
        X_repr:          Dataset representativo para calibración.

    Returns:
        Contenido binario del modelo TFLite cuantizado.
    """
    def representative_dataset_gen():
        for sample in X_repr[:200]:
            yield [sample.reshape(1, -1).astype(np.float32)]

    converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))
    converter.optimizations          = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset_gen
    return converter.convert()


def verify_tflite_model(
    tflite_path: Path,
    X_sample: np.ndarray,
    rf_model: RandomForestClassifier,
) -> Dict:
    """
    Verifica que el modelo TFLite tenga las formas de entrada/salida esperadas
    y que sus predicciones sean consistentes con el RF (≤ 0.10 MAE).

    Contrato de interfaz (≡ TFLiteModelManager.kt):
        Input:  shape [1, 14], dtype FLOAT32
        Output: shape [1,  1], dtype FLOAT32, rango ∈ [0, 1]

    Args:
        tflite_path: Ruta al archivo .tflite.
        X_sample:    Muestra de features para inferencia de prueba (N, 14).
        rf_model:    RF para comparar predicciones.

    Returns:
        Dict con resultados de la verificación.
    """
    if not _HAS_TF:
        return {}

    interp = tf.lite.Interpreter(model_path=str(tflite_path))
    interp.allocate_tensors()

    in_det  = interp.get_input_details()[0]
    out_det = interp.get_output_details()[0]

    in_shape  = list(in_det["shape"])
    out_shape = list(out_det["shape"])
    in_dtype  = in_det["dtype"].__name__
    out_dtype = out_det["dtype"].__name__

    # Verificar formas
    shape_ok = (in_shape == [1, 14] and out_shape == [1, 1])

    # Inferencia en muestra pequeña
    risk_tflite, risk_rf = [], []
    proba_rf = rf_model.predict_proba(X_sample[:50])
    weights  = np.array([0.0, 0.5, 1.0][:proba_rf.shape[1]])

    for i in range(min(50, len(X_sample))):
        inp = X_sample[i:i+1].astype(np.float32)
        interp.set_tensor(in_det["index"], inp)
        interp.invoke()
        risk_tflite.append(float(interp.get_tensor(out_det["index"])[0, 0]))
        risk_rf.append(float((proba_rf[i] * weights).sum()))

    mae = float(np.mean(np.abs(np.array(risk_tflite) - np.array(risk_rf))))
    corr = float(np.corrcoef(risk_tflite, risk_rf)[0, 1]) if len(risk_tflite) > 1 else 1.0

    result = {
        "in_shape":  in_shape,
        "out_shape": out_shape,
        "in_dtype":  in_dtype,
        "out_dtype": out_dtype,
        "shape_ok":  shape_ok,
        "mae_vs_rf": round(mae, 4),
        "corr_vs_rf": round(corr, 4),
    }

    status = "✓" if (shape_ok and mae < 0.10) else "⚠"
    print(f"  {status}  TFLite — Input: {in_shape} {in_dtype} | "
          f"Output: {out_shape} {out_dtype}")
    print(f"  {status}  TFLite vs RF — MAE: {mae:.4f} | Corr: {corr:.4f}")

    return result


# ─── 7. Visualizaciones ───────────────────────────────────────────────────────

def plot_feature_importance(
    model: RandomForestClassifier,
    output_path: Path,
) -> None:
    """
    Genera gráfica de barras horizontal con la importancia de las 14 features.

    Las importancias se calculan como la reducción media de impureza de Gini
    (Mean Decrease in Impurity, MDI) acumulada en todos los árboles del bosque.

    La gráfica está diseñada para su inclusión directa en la tesis:
        - Features ordenadas de mayor a menor importancia.
        - Barras de error (std entre estimadores del ensemble).
        - Colores diferenciados por categoría de feature.

    Args:
        model:       RF entrenado (post GridSearchCV).
        output_path: Ruta de salida de la imagen.
    """
    importances = model.feature_importances_
    stds        = np.std([t.feature_importances_ for t in model.estimators_], axis=0)

    # Ordenar de mayor a menor
    order = np.argsort(importances)[::-1]

    display_names = [
        "RT Memoria", "RT Atención", "RT Ejecutivo", "RT Lenguaje",
        "Acc. Memoria", "Acc. Atención", "Acc. Ejecutivo",
        "Acc. Lenguaje", "Acc. Orientación",
        "Tendencia Memoria", "Tendencia Atención",
        "Completitud sesiones", "Variabilidad RT", "Δ Baseline",
    ]

    # Colores por categoría
    category_colors = {
        "RT":         "#2980b9",
        "Accuracy":   "#27ae60",
        "Tendencia":  "#8e44ad",
        "Especial":   "#e67e22",
    }
    feat_colors = (
        [category_colors["RT"]]       * 4 +
        [category_colors["Accuracy"]] * 5 +
        [category_colors["Tendencia"]]* 2 +
        [category_colors["Especial"]] * 3
    )

    fig, ax = plt.subplots(figsize=(10, 8))

    sorted_names  = [display_names[i]  for i in order]
    sorted_imps   = importances[order]
    sorted_stds   = stds[order]
    sorted_colors = [feat_colors[i] for i in order]

    bars = ax.barh(
        range(len(FEATURE_NAMES)),
        sorted_imps,
        xerr    = sorted_stds,
        color   = sorted_colors,
        alpha   = 0.82,
        edgecolor = "white",
        height  = 0.72,
        capsize = 3,
        error_kw = {"elinewidth": 1.2, "ecolor": "gray", "capthick": 1.2},
    )

    ax.set_yticks(range(len(FEATURE_NAMES)))
    ax.set_yticklabels(sorted_names, fontsize=10)
    ax.set_xlabel("Importancia media de feature (MDI)", fontsize=11)
    ax.set_title(
        "Importancia de Features — Random Forest EternaMente\n"
        f"(n_estimators={len(model.estimators_)}, "
        f"max_depth={model.max_depth})",
        fontsize=12, fontweight="bold", pad=14
    )
    ax.grid(axis="x", alpha=0.35, linewidth=0.6)
    ax.set_axisbelow(True)

    # Leyenda de categorías
    from matplotlib.patches import Patch
    legend_elements = [
        Patch(facecolor=category_colors["RT"],        label="Tiempo de reacción"),
        Patch(facecolor=category_colors["Accuracy"],  label="Accuracy por dominio"),
        Patch(facecolor=category_colors["Tendencia"], label="Tendencia OLS"),
        Patch(facecolor=category_colors["Especial"],  label="Completitud / Variabilidad / Δ Baseline"),
    ]
    ax.legend(handles=legend_elements, loc="lower right", fontsize=9)

    plt.tight_layout()
    fig.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✓  {output_path.name}")


def plot_confusion_matrix(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    output_path: Path,
) -> None:
    """
    Genera la matriz de confusión normalizada (por fila = recall por clase).

    Args:
        y_true:      Etiquetas verdaderas.
        y_pred:      Etiquetas predichas por el RF.
        output_path: Ruta de salida.
    """
    classes_present = sorted(set(y_true) | set(y_pred))
    labels = [LABEL_NAMES[c] for c in classes_present]

    cm = confusion_matrix(y_true, y_pred, labels=classes_present)
    cm_norm = cm.astype(float) / cm.sum(axis=1, keepdims=True)

    fig, ax = plt.subplots(figsize=(7, 5))
    sns.heatmap(
        cm_norm, annot=True, fmt=".2f", ax=ax,
        xticklabels=labels, yticklabels=labels,
        cmap="Blues", vmin=0, vmax=1, linewidths=0.5,
        annot_kws={"size": 13, "weight": "bold"},
    )
    # Añadir conteos absolutos
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            ax.text(j + 0.5, i + 0.73, f"(n={cm[i,j]})",
                    ha="center", va="center", fontsize=8, color="gray")

    ax.set_xlabel("Predicción", fontsize=11)
    ax.set_ylabel("Real", fontsize=11)
    ax.set_title("Matriz de Confusión Normalizada — Random Forest\n"
                 "(valores = recall por clase)", fontsize=11, fontweight="bold")
    plt.tight_layout()
    fig.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✓  {output_path.name}")


def plot_roc_curves(
    roc_data: Dict,
    output_path: Path,
) -> None:
    """
    Genera curvas ROC por clase (One-vs-Rest) para el informe de la tesis.

    Args:
        roc_data:    Dict con {clase: {fpr, tpr, auc}} generado en evaluate_rf.
        output_path: Ruta de salida.
    """
    fig, ax = plt.subplots(figsize=(7, 6))

    for label_name, data in roc_data.items():
        color = LABEL_COLORS.get(label_name, "gray")
        ax.plot(data["fpr"], data["tpr"], color=color, linewidth=2.2,
                label=f"{label_name} (AUC = {data['auc']:.3f})")

    ax.plot([0, 1], [0, 1], "k--", linewidth=1, alpha=0.5)
    ax.set_xlabel("Tasa de Falsos Positivos (FPR)", fontsize=11)
    ax.set_ylabel("Tasa de Verdaderos Positivos (TPR)", fontsize=11)
    ax.set_title("Curvas ROC — Random Forest EternaMente\n"
                 "(One-vs-Rest por clase)", fontsize=11, fontweight="bold")
    ax.legend(fontsize=10, loc="lower right")
    ax.grid(alpha=0.3, linewidth=0.5)
    ax.set_xlim(-0.02, 1.02)
    ax.set_ylim(-0.02, 1.02)
    plt.tight_layout()
    fig.savefig(output_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  ✓  {output_path.name}")


# ─── 8. Informe de evaluación ─────────────────────────────────────────────────

def generate_evaluation_report(
    if_model:   IsolationForest,
    if_metrics: Dict,
    rf_model:   RandomForestClassifier,
    rf_gs_results: Dict,
    rf_eval:    Dict,
    tflite_verify: Dict,
    output_path: Path,
    n_train: int,
    n_test: int,
) -> None:
    """
    Genera el informe completo de evaluación para el capítulo de resultados.

    Incluye:
    1. Configuración del experimento.
    2. Resultados del Isolation Forest.
    3. Resultados del GridSearchCV para RF.
    4. Métricas completas del RF en test.
    5. Reporte por clase (precision/recall/F1).
    6. Verificación del modelo TFLite.
    7. Discusión sobre limitaciones del dataset.

    Args:
        if_model, if_metrics: Isolation Forest y sus métricas.
        rf_model, rf_gs_results, rf_eval: RF y sus resultados.
        tflite_verify: Resultados de verificación TFLite.
        output_path: Ruta del archivo de salida.
        n_train, n_test: Tamaños del split train/test.
    """
    buf = io.StringIO()
    SEP  = "=" * 70
    SEP2 = "-" * 70

    def w(s: str = "") -> None:
        buf.write(s + "\n")

    w(SEP)
    w("  EternaMente — Informe de Evaluación del Modelo ML")
    w(f"  Generado: {pd.Timestamp.now().strftime('%Y-%m-%d %H:%M')}")
    w(SEP)
    w()

    # ── Configuración ─────────────────────────────────────────────────────────
    w("1. CONFIGURACIÓN DEL EXPERIMENTO")
    w(SEP2)
    w(f"  Semilla global (SEED):         {SEED}")
    w(f"  Número de features (N):        {len(FEATURE_NAMES)}")
    w(f"  Clases:                        NORMAL=0, WATCH=1, ALERT=2")
    w(f"  Split train/test:              80% / 20% (estratificado por etiqueta)")
    w(f"  Muestras de entrenamiento:     {n_train:,}")
    w(f"  Muestras de test:              {n_test:,}")
    w(f"  Nota: cada fila = (usuario, semana) → posible fuga temporal entre")
    w(f"  semanas del mismo usuario en distintos folds. Ver Sección 8.")
    w()

    # ── Isolation Forest ──────────────────────────────────────────────────────
    w("2. ISOLATION FOREST (DETECCIÓN DE ANOMALÍAS)")
    w(SEP2)
    w(f"  Configuración:")
    w(f"    n_estimators:  {IF_PARAMS['n_estimators']}")
    w(f"    contamination: {IF_PARAMS['contamination']} (15% esperado de anomalías)")
    w(f"    random_state:  {IF_PARAMS['random_state']}")
    w()
    w(f"  Entrenamiento:  {if_metrics.get('n_train', '?'):,} semanas de usuarios NORMAL")
    w(f"  (One-class: el modelo aprende la distribución normal del rendimiento)")
    w()
    w(f"  Especificidad (NORMAL correctamente identificado):")
    w(f"    {if_metrics.get('specificity', '?') * 100:.1f}%")
    w()
    if "sensitivity" in if_metrics:
        w(f"  Sensibilidad (DCL detectado como anomalía):")
        w(f"    Global:        {if_metrics['sensitivity']*100:.1f}%  "
          f"{'✓ > 70%' if if_metrics['sensitivity'] > 0.70 else '⚠ < 70% (objetivo)'}")
        for group in DCL_GROUPS:
            key = f"sensitivity_{group}"
            if key in if_metrics:
                w(f"    {group:15s}: {if_metrics[key]*100:.1f}%")
    w()
    w("  Interpretación:")
    w("    El IF detecta patrones globalmente atípicos sin necesitar etiquetas.")
    w("    Es especialmente sensible a usuarios con múltiples dominios degradados")
    w("    (DCL_MODERADO). Para DCL_LEVE (cambios sutiles en 90 días) la detección")
    w("    es inherentemente más difícil — consistent con la literatura clínica.")
    w()

    # ── GridSearchCV ──────────────────────────────────────────────────────────
    w("3. RANDOM FOREST — BÚSQUEDA DE HIPERPARÁMETROS (GridSearchCV)")
    w(SEP2)
    w(f"  Validación cruzada: StratifiedKFold(n_splits=5, shuffle=True)")
    w(f"  Métrica de selección: F1-weighted (robusta al desbalance de clases)")
    w(f"  Grid explorado:")
    for param, vals in RF_GRID.items():
        w(f"    {param}: {vals}")
    w()
    w(f"  Mejores hiperparámetros: {rf_gs_results['best_params']}")
    w(f"  Mejor F1-weighted (CV):  {rf_gs_results['best_f1_weighted']*100:.2f}%")
    w()
    w(f"  Configuración final del modelo:")
    w(f"    n_estimators:    {rf_model.n_estimators}")
    w(f"    max_depth:       {rf_model.max_depth}")
    w(f"    min_samples_leaf:{rf_model.min_samples_leaf}")
    w(f"    class_weight:    balanced (ajusta pesos inversamente a frecuencia)")
    w()

    # ── Métricas en test ──────────────────────────────────────────────────────
    w("4. RANDOM FOREST — EVALUACIÓN EN TEST")
    w(SEP2)
    w(f"  Muestras de test: {rf_eval['n_test']:,}")
    w()
    w(f"  ┌─────────────────────────────────────────────────┐")
    w(f"  │ Accuracy:         {rf_eval['accuracy']*100:6.2f}%                     │")
    w(f"  │ Precision (w):    {rf_eval['precision_w']*100:6.2f}%                     │")
    w(f"  │ Recall (w):       {rf_eval['recall_w']*100:6.2f}%                     │")
    w(f"  │ F1-score (w):     {rf_eval['f1_weighted']*100:6.2f}%                     │")
    w(f"  │ F1-score (macro): {rf_eval['f1_macro']*100:6.2f}%                     │")
    w(f"  │ AUC-ROC (w):      {rf_eval['auc_weighted']:6.4f}                     │")
    w(f"  │ AUC-ROC (macro):  {rf_eval['auc_macro']:6.4f}                     │")
    w(f"  └─────────────────────────────────────────────────┘")
    w()
    w("  Reporte por clase:")
    w(rf_eval.get("class_report", "  (no disponible)"))

    # ── AUC por clase ─────────────────────────────────────────────────────────
    if "roc_data" in rf_eval:
        w("  AUC-ROC por clase (One-vs-Rest):")
        for lname, data in rf_eval["roc_data"].items():
            w(f"    {lname:6s}: {data['auc']:.4f}")
    w()

    # ── Importancia de features top-5 ────────────────────────────────────────
    w("5. IMPORTANCIA DE FEATURES (Top 5)")
    w(SEP2)
    imps   = rf_model.feature_importances_
    order  = np.argsort(imps)[::-1]
    for rank, idx in enumerate(order[:5], 1):
        w(f"  {rank}. {FEATURE_NAMES[idx]:<30} {imps[idx]*100:.2f}%")
    w()
    w(f"  (Ver feature_importance.png para el gráfico completo)")
    w()

    # ── TFLite ────────────────────────────────────────────────────────────────
    w("6. MODELO TFLITE — VERIFICACIÓN")
    w(SEP2)
    if tflite_verify:
        w(f"  Input shape:     {tflite_verify.get('in_shape', '?')}")
        w(f"  Input dtype:     {tflite_verify.get('in_dtype', '?')}")
        w(f"  Output shape:    {tflite_verify.get('out_shape', '?')}")
        w(f"  Output dtype:    {tflite_verify.get('out_dtype', '?')}")
        w(f"  Formas correctas: {'✓' if tflite_verify.get('shape_ok') else '✗'}")
        w(f"  MAE vs RF:       {tflite_verify.get('mae_vs_rf', '?')}")
        w(f"  Corr vs RF:      {tflite_verify.get('corr_vs_rf', '?')}")
        w()
        w("  Integración Android (TFLiteModelManager.kt):")
        w("    Archivo: app/src/main/res/raw/eternamente_ml_v1.tflite")
        w("    Input pre-procesado por FeatureNormalizer.kt → [0, 1]")
        w("    Output: risk_score ∈ [0, 1] → AlertLevel.classify()")
    else:
        w("  tensorflow no disponible — installar con: pip install tensorflow")
        w()
        w("  Instrucciones de integración en Android:")
        w("    1. Instalar: pip install tensorflow onnx skl2onnx onnx-tf")
        w("    2. Re-ejecutar: python train_and_export_model.py")
        w("    3. Copiar: eternamente_ml_v1.tflite")
        w("       → app/src/main/res/raw/eternamente_ml_v1.tflite")
    w()

    # ── Limitaciones ──────────────────────────────────────────────────────────
    w("7. LIMITACIONES Y TRABAJO FUTURO")
    w(SEP2)
    w("  a) Datos sintéticos: el modelo se entrenó con datos generados por")
    w("     generate_synthetic_data.py, NO con datos clínicos reales.")
    w("     Para despliegue en producción se requiere validación con datos reales.")
    w()
    w("  b) Fuga temporal: el split train/test por fila (user × semana)")
    w("     puede tener fugas entre semanas del mismo usuario. Para evaluación")
    w("     definitiva usar GroupShuffleSplit por user_id.")
    w()
    w("  c) Desbalance de clases: NORMAL=93%, WATCH=6%, ALERT=1%.")
    w("     El peso 'balanced' mitiga el problema pero métricas macro/micro")
    w("     pueden sobre/sub-estimar el rendimiento en clases minoritarias.")
    w()
    w("  d) Horizonte temporal: el modelo asume 4 semanas de datos pasados.")
    w("     Para usuarios con < 4 semanas de historial la fiabilidad es menor.")
    w()
    w(SEP)
    w("  Generado por train_and_export_model.py (EternaMente ML Pipeline)")
    w(SEP)

    output_path.write_text(buf.getvalue(), encoding="utf-8")
    print(f"  ✓  {output_path.name}")


# ─── 9. Main ─────────────────────────────────────────────────────────────────

def parse_args() -> argparse.Namespace:
    """Parsea argumentos de línea de comandos."""
    parser = argparse.ArgumentParser(
        description="EternaMente — Pipeline de entrenamiento y exportación del modelo ML"
    )
    parser.add_argument(
        "--input_dir", type=Path, default=_INPUT_DIR,
        help="Directorio con df_features_weekly.csv y df_labels.csv"
    )
    parser.add_argument(
        "--output_dir", type=Path, default=_OUTPUT_DIR,
        help="Directorio de salida para modelos y artefactos"
    )
    return parser.parse_args()


def main() -> None:
    """
    Pipeline principal de entrenamiento y exportación.

    Secuencia:
    1. Verificar dependencias disponibles.
    2. Cargar y preparar datos.
    3. Split train/test estratificado (80/20).
    4. Entrenar Isolation Forest (unsupervised, solo NORMAL).
    5. Entrenar Random Forest + GridSearchCV (StratifiedKFold k=5).
    6. Evaluar ambos modelos.
    7. Exportar RF a ONNX (si skl2onnx disponible).
    8. Exportar a TFLite INT8 (si tensorflow disponible).
    9. Verificar modelo TFLite.
    10. Guardar modelos y artefactos.
    11. Generar visualizaciones.
    12. Generar informe.
    """
    t0   = time.perf_counter()
    args = parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)

    print()
    print("══════════════════════════════════════════════════════════")
    print("  EternaMente — Pipeline ML: Entrenamiento & Exportación")
    print("══════════════════════════════════════════════════════════")
    print()
    print("Dependencias disponibles:")
    print(f"  sklearn {__import__('sklearn').__version__}  ✓")
    print(f"  onnx / skl2onnx / onnxruntime:  {'✓' if _HAS_ONNX else '✗ (pip install onnx skl2onnx onnxruntime)'}")
    print(f"  tensorflow:                      {'✓' if _HAS_TF else '✗ (pip install tensorflow)'}")
    print(f"  onnx-tf:                         {'✓' if _HAS_ONNX_TF else '✗ (pip install onnx-tf)'}")
    print()

    # ── 1. Cargar datos ────────────────────────────────────────────────────────
    print("▶ Paso 1/8  Cargando datos...")
    df, df_labels = load_data(args.input_dir)
    X, y, groups  = extract_arrays(df)
    print(f"  ✓  Dataset: {len(df):,} filas | "
          f"NORMAL={( y==0).sum():,} / WATCH={(y==1).sum():,} / ALERT={(y==2).sum():,}")
    print()

    # ── 2. Split train / test ─────────────────────────────────────────────────
    print("▶ Paso 2/8  Split train/test (80/20, estratificado)...")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, stratify=y, random_state=SEED
    )
    print(f"  ✓  Train: {len(X_train):,} | Test: {len(X_test):,}")
    print()

    # ── 3. Isolation Forest ───────────────────────────────────────────────────
    print("▶ Paso 3/8  Entrenando Isolation Forest (one-class, solo NORMAL)...")
    df_train_with_group = df.iloc[
        train_test_split(range(len(df)), test_size=0.20, stratify=y, random_state=SEED)[0]
    ].copy()

    if_model, if_metrics = train_isolation_forest(df)   # entrenado con todos (NORMAL aplica dentro)
    print()

    # ── 4. Random Forest + GridSearchCV ───────────────────────────────────────
    print("▶ Paso 4/8  Entrenando Random Forest + GridSearchCV "
          "(StratifiedKFold k=5)...")
    rf_model, rf_gs = train_random_forest(X_train, y_train)
    print()

    # ── 5. Evaluación RF ──────────────────────────────────────────────────────
    print("▶ Paso 5/8  Evaluando Random Forest en test...")
    rf_eval = evaluate_random_forest(rf_model, X_test, y_test)
    print(f"  ✓  Accuracy:   {rf_eval['accuracy']*100:.2f}%")
    print(f"  ✓  F1-weighted:{rf_eval['f1_weighted']*100:.2f}%")
    print(f"  ✓  AUC-ROC(w): {rf_eval['auc_weighted']:.4f}")
    print()

    # ── 6. Exportar sklearn .pkl ──────────────────────────────────────────────
    print("▶ Paso 6/8  Guardando modelos sklearn...")
    pkl_path = args.output_dir / "random_forest_model.pkl"
    joblib.dump(rf_model, pkl_path, compress=3)
    if_pkl   = args.output_dir / "isolation_forest_model.pkl"
    joblib.dump(if_model, if_pkl, compress=3)
    print(f"  ✓  {pkl_path.name} ({pkl_path.stat().st_size/1024:.0f} KB)")
    print(f"  ✓  {if_pkl.name}  ({if_pkl.stat().st_size/1024:.0f} KB)")
    print()

    # ── 7. Exportar ONNX + TFLite ─────────────────────────────────────────────
    print("▶ Paso 7/8  Exportando a ONNX + TFLite...")
    onnx_path = args.output_dir / "random_forest_model.onnx"
    onnx_path = convert_to_onnx(rf_model, onnx_path)

    tflite_path   = args.output_dir / "eternamente_ml_v1.tflite"
    tflite_result = convert_to_tflite(rf_model, onnx_path, X_train, y_train, tflite_path)

    # Si TF no está disponible en este entorno, intentar mediante generate_tflite.py
    # en un entorno conda separado (tf_env con Python 3.11)
    if tflite_result is None and not tflite_path.exists():
        import subprocess, shutil
        generate_script = Path(__file__).parent / "generate_tflite.py"
        # Buscar Python con TF: probar conda tf_env, luego python3 directo
        for python_candidate in [
            shutil.which("conda") and "conda run -n tf_env python",
            "python3",
        ]:
            if not python_candidate:
                continue
            cmd = f"{python_candidate} {generate_script}"
            print(f"  → Intentando: {cmd}")
            try:
                proc = subprocess.run(
                    cmd, shell=True, capture_output=True, text=True, timeout=300
                )
                if proc.returncode == 0 and tflite_path.exists():
                    print("  ✓  TFLite generado via generate_tflite.py")
                    tflite_result = tflite_path
                    break
                else:
                    if proc.stderr:
                        print(f"  ⚠  {proc.stderr[-200:]}")
            except Exception as e:
                print(f"  ⚠  {e}")

    tflite_verify: Dict = {}
    if tflite_result and tflite_result.exists():
        print("  → Verificando modelo TFLite...")
        tflite_verify = verify_tflite_model(tflite_result, X_test, rf_model)
    print()

    # ── 8. Visualizaciones + Informe ──────────────────────────────────────────
    print("▶ Paso 8/8  Generando visualizaciones e informe...")

    plot_feature_importance(
        rf_model,
        output_path=args.output_dir / "feature_importance.png"
    )
    plot_confusion_matrix(
        y_test, rf_eval["y_pred"],
        output_path=args.output_dir / "confusion_matrix.png"
    )
    if "roc_data" in rf_eval:
        plot_roc_curves(
            rf_eval["roc_data"],
            output_path=args.output_dir / "roc_curves.png"
        )

    generate_evaluation_report(
        if_model, if_metrics,
        rf_model, rf_gs, rf_eval,
        tflite_verify,
        output_path=args.output_dir / "model_evaluation_report.txt",
        n_train=len(X_train),
        n_test=len(X_test),
    )
    print()

    # ── Resumen final ─────────────────────────────────────────────────────────
    elapsed = time.perf_counter() - t0
    print("══════════════════════════════════════════════════════════")
    print(f"  ✓  Pipeline completado en {elapsed:.1f}s")
    print()
    print("  Archivos generados:")
    for f in sorted(args.output_dir.glob("*")):
        if f.is_file():
            size = f.stat().st_size
            size_str = f"{size/1024:.0f} KB" if size < 1024**2 else f"{size/1024**2:.2f} MB"
            icon = "★" if f.suffix == ".tflite" else "•"
            print(f"    {icon} {f.name:<45} {size_str}")
    print()
    print("  Próximos pasos:")
    if not _HAS_TF:
        print("  ⚠  Para generar eternamente_ml_v1.tflite:")
        print("     pip install tensorflow onnx skl2onnx onnx-tf")
        print("     python train_and_export_model.py")
        print()
    print("  ★  Copiar el .tflite a Android:")
    print("     app/src/main/res/raw/eternamente_ml_v1.tflite")
    print("══════════════════════════════════════════════════════════")
    print()


if __name__ == "__main__":
    main()
