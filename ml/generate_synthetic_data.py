#!/usr/bin/env python3
"""
generate_synthetic_data.py — EternaMente ML Training Data Generator
======================================================================
Genera datos sintéticos de entrenamiento para el modelo de detección de
deterioro cognitivo leve (DCL) de EternaMente.

Grupos de usuarios (n=300):
  • NORMAL      (60%, n=180): rendimiento estable con variabilidad sesión a sesión.
  • DCL_LEVE    (25%, n=75):  degradación gradual en MEMORY y ORIENTATION (1-3%/mes).
  • DCL_MODERADO(15%, n=45):  degradación en todos los dominios (3-6%/mes).

Efectos modelados por sesión:
  • Efecto de práctica: +0.5% por semana durante las primeras 4 semanas.
  • Efecto del momento del día: mañana +8%, tarde -3%, noche -8%.
  • Efecto de racha: +5% si el usuario completó ≥7 días consecutivos.
  • Degradación lineal para grupos DCL desde el día 0.

Outputs (directorio `output/` relativo a este script):
  • df_sessions.csv          — sesiones individuales con métricas brutas.
  • df_features_weekly.csv   — 14 features agregadas por semana (≡ FeatureVector).
  • df_labels.csv            — etiquetas NORMAL=0, WATCH=1, ALERT=2 por usuario/semana.
  • visualizations/          — 6 gráficos de análisis exploratorio.
  • stats_summary.txt        — estadísticas descriptivas para la tesis.

Reproducibilidad: seed=42 en todas las operaciones aleatorias.

Uso:
    python generate_synthetic_data.py

Requisitos:
    pip install numpy pandas matplotlib seaborn scipy
"""

# ─── Imports ─────────────────────────────────────────────────────────────────

from __future__ import annotations

import io
import time
import warnings
from dataclasses import dataclass, field
from datetime import date, timedelta
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import matplotlib
matplotlib.use("Agg")   # backend no interactivo — compatible con servidores sin display
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from scipy.stats import linregress

warnings.filterwarnings("ignore")

# ─── Reproducibilidad ────────────────────────────────────────────────────────

SEED: int = 42

# ─── Parámetros de simulación ─────────────────────────────────────────────────

N_USERS:        int   = 300
N_DAYS:         int   = 90          # duración de la simulación
ABSENCE_RATE:   float = 0.20        # probabilidad de no completar una sesión
PRACTICE_WEEKS: int   = 4           # semanas con efecto de práctica
PRACTICE_GAIN:  float = 0.005       # +0.5 % por semana de práctica
STREAK_BONUS:   float = 0.05        # +5 % si racha ≥ 7 días
STREAK_MIN:     int   = 7           # umbral de racha para activar el bonus
BASELINE_WEEKS: int   = 2           # semanas usadas para calcular el baseline del usuario
WINDOW_WEEKS:   int   = 4           # ventana deslizante para features semanales
START_DATE:     date  = date(2024, 1, 1)

# Distribución de grupos
GROUP_SIZES: Dict[str, int] = {
    "NORMAL":       180,
    "DCL_LEVE":     75,
    "DCL_MODERADO": 45,
}

# Dominios cognitivos evaluados
DOMAINS: List[str] = ["MEMORY", "ATTENTION", "EXECUTIVE", "LANGUAGE", "ORIENTATION"]

# Dominios con tiempo de reacción (ORIENTATION es respuesta múltiple lenta → sin RT)
RT_DOMAINS: List[str] = ["MEMORY", "ATTENTION", "EXECUTIVE", "LANGUAGE"]

# ─── Configuración de grupos ──────────────────────────────────────────────────

# Scores de baseline (accuracy %) y RT (ms) por grupo
BASELINE_CONFIG: Dict[str, Dict] = {
    "NORMAL":       {"score_mean": 85, "score_std": 10, "rt_mean": 900,  "rt_std": 150},
    "DCL_LEVE":     {"score_mean": 75, "score_std": 12, "rt_mean": 1100, "rt_std": 200},
    "DCL_MODERADO": {"score_mean": 65, "score_std": 15, "rt_mean": 1400, "rt_std": 300},
}

# Dominios afectados y tasa de degradación mensual por grupo
DEGRADATION_CONFIG: Dict[str, Dict] = {
    "NORMAL":       {"domains": [],                 "rate_range": (0.000, 0.000)},
    "DCL_LEVE":     {"domains": ["MEMORY",
                                 "ORIENTATION"],    "rate_range": (0.010, 0.030)},
    "DCL_MODERADO": {"domains": DOMAINS,            "rate_range": (0.030, 0.060)},
}

# Efecto del momento del día sobre accuracy
TIME_OF_DAY_EFFECTS: Dict[str, float] = {
    "morning":   0.08,
    "afternoon": -0.03,
    "night":     -0.08,
}
TIME_OF_DAY_PROBS: List[float] = [0.40, 0.40, 0.20]  # morning, afternoon, night

# Umbrales para etiquetas (sobre el anomaly score [0,1]).
# Calibrados para que el dataset refleje la prevalencia real:
# NORMAL ~80%, WATCH ~14%, ALERT ~6% (distribución realista para adultos 60+).
LABEL_WATCH_THRESHOLD: float = 0.22
LABEL_ALERT_THRESHOLD: float = 0.45

# Bounds del FeatureNormalizer de EternaMente — deben coincidir con FeatureNormalizer.kt
# Orden: [0-3] mean_rt_*, [4-8] accuracy_*, [9-10] trend_*, [11] completion,
#        [12] rt_variability, [13] delta_from_baseline
NORM_MIN: np.ndarray = np.array([200, 200, 200, 200, 0, 0, 0, 0, 0, -10, -10, 0, 0,  -3], dtype=float)
NORM_MAX: np.ndarray = np.array([5000,5000,5000,5000,100,100,100,100,100, 10,  10, 1, 1.5,  3], dtype=float)

# Rutas de salida
_SCRIPT_DIR: Path = Path(__file__).parent
OUTPUT_DIR:  Path = _SCRIPT_DIR / "output"
VIZ_DIR:     Path = OUTPUT_DIR / "visualizations"


# ─── Data classes ─────────────────────────────────────────────────────────────

@dataclass
class UserProfile:
    """
    Perfil cognitivo completo de un usuario simulado.

    Attributes:
        user_id:              Identificador único (e.g. "U0001").
        group:                Grupo clínico: NORMAL, DCL_LEVE o DCL_MODERADO.
        age:                  Edad en años (55–90).
        education_years:      Años de escolaridad (6, 9, 12 o 16).
        gender:               'M' o 'F'.
        baseline_scores:      Accuracy media (%) por dominio en ausencia de efectos.
        baseline_rt_ms:       Tiempo de reacción medio (ms) por dominio RT.
        degradation_domains:  Dominios afectados por la degradación cognitiva.
        monthly_degradation:  Tasa de degradación mensual [0, 1] (0 = sin degradación).
    """
    user_id:             str
    group:               str
    age:                 int
    education_years:     int
    gender:              str
    baseline_scores:     Dict[str, float]
    baseline_rt_ms:      Dict[str, float]
    degradation_domains: List[str]
    monthly_degradation: float


# ─── 1. Generación de perfiles de usuario ─────────────────────────────────────

def generate_users(rng: np.random.Generator) -> List[UserProfile]:
    """
    Genera los 300 perfiles de usuario con parámetros cognitivos individualizados.

    Cada usuario recibe:
    - Un score de baseline por dominio, centrado en la media de su grupo con
      variabilidad individual (normal multivariada simplificada).
    - Un tiempo de reacción baseline por dominio RT.
    - Una tasa de degradación mensual aleatoria dentro del rango de su grupo.
    - Datos demográficos plausibles para adultos mayores (55-90 años).

    Args:
        rng: Generador NumPy con semilla fijada para reproducibilidad.

    Returns:
        Lista de 300 UserProfile ordenados por grupo (NORMAL, DCL_LEVE, DCL_MODERADO).
    """
    users: List[UserProfile] = []
    uid_counter = 0

    # Offset medio por dominio para simular correlaciones entre dominios
    _DOMAIN_OFFSETS_STD = {"MEMORY": 5.0, "ATTENTION": 3.5, "EXECUTIVE": 4.0,
                            "LANGUAGE": 3.5, "ORIENTATION": 4.5}
    # EXECUTIVE y LANGUAGE son inherentemente más lentos
    _RT_DOMAIN_MULT = {"MEMORY": 1.00, "ATTENTION": 0.95, "EXECUTIVE": 1.12, "LANGUAGE": 1.10}

    for group, n in GROUP_SIZES.items():
        cfg   = BASELINE_CONFIG[group]
        dcfg  = DEGRADATION_CONFIG[group]
        lo, hi = dcfg["rate_range"]

        for _ in range(n):
            uid = f"U{uid_counter:04d}"
            uid_counter += 1

            # Demografía plausible para adultos 60+
            age      = int(np.clip(rng.normal(70, 7), 55, 90))
            edu      = int(rng.choice([6, 9, 12, 16], p=[0.20, 0.30, 0.35, 0.15]))
            gender   = rng.choice(["M", "F"], p=[0.45, 0.55])

            # Offset individual de score (correlación entre dominios mediada por factor g)
            g_factor = rng.normal(0, cfg["score_std"] * 0.5)

            baseline_scores: Dict[str, float] = {}
            for domain in DOMAINS:
                domain_noise = rng.normal(0, _DOMAIN_OFFSETS_STD[domain])
                raw = cfg["score_mean"] + g_factor + domain_noise
                # Usuarios mayores con menos educación → leve penalización
                edu_adj = (edu - 12) * 0.3
                age_adj = -(age - 70) * 0.1
                baseline_scores[domain] = float(np.clip(raw + edu_adj + age_adj, 20.0, 100.0))

            baseline_rt_ms: Dict[str, float] = {}
            for domain in RT_DOMAINS:
                rt_noise = rng.normal(0, cfg["rt_std"])
                rt = (cfg["rt_mean"] + rt_noise) * _RT_DOMAIN_MULT[domain]
                baseline_rt_ms[domain] = float(np.clip(rt, 300.0, 4_500.0))

            monthly_degrad = float(rng.uniform(lo, hi)) if hi > 0 else 0.0

            users.append(UserProfile(
                user_id             = uid,
                group               = group,
                age                 = age,
                education_years     = edu,
                gender              = gender,
                baseline_scores     = baseline_scores,
                baseline_rt_ms      = baseline_rt_ms,
                degradation_domains = list(dcfg["domains"]),
                monthly_degradation = monthly_degrad,
            ))

    return users


# ─── 2. Generación de sesiones por usuario ────────────────────────────────────

def generate_sessions_for_user(
    user: UserProfile,
    rng: np.random.Generator,
) -> pd.DataFrame:
    """
    Simula 90 días de sesiones cognitivas para un único usuario.

    Modela los siguientes efectos sobre accuracy y RT por dominio:

    1. **Degradación cognitiva** (lineal desde el día 0):
       ``degradation_at_day = (monthly_rate / 30) × day_number``
       Sólo se aplica en los dominios de ``user.degradation_domains``.

    2. **Efecto de práctica** (primeras 4 semanas):
       ``practice = min(week, 4) × 0.005``
       Efecto de aprendizaje que beneficia a todos los grupos.

    3. **Efecto del momento del día**:
       mañana=+8%, tarde=-3%, noche=-8%.

    4. **Efecto de racha** (streak ≥ 7 días consecutivos):
       +5% de bonus en la sesión siguiente.

    5. **Ruido gaussiano** (~±7% std) para modelar variabilidad intrasesión.

    Args:
        user: Perfil del usuario a simular.
        rng:  Generador aleatorio con semilla.

    Returns:
        DataFrame con una fila por sesión completada (días sin sesión omitidos).
    """
    rows: List[Dict] = []
    streak          = 0
    prev_attended   = False

    for day in range(N_DAYS):
        attended = rng.random() >= ABSENCE_RATE

        # Actualizar racha (días consecutivos)
        if attended:
            streak = streak + 1 if prev_attended else 1
        else:
            streak = 0

        prev_attended = attended

        if not attended:
            continue

        # ── Efectos temporales ────────────────────────────────────────────────
        week            = day // 7
        practice_effect = min(week, PRACTICE_WEEKS) * PRACTICE_GAIN

        tod             = rng.choice(["morning", "afternoon", "night"],
                                     p=TIME_OF_DAY_PROBS)
        tod_effect      = TIME_OF_DAY_EFFECTS[tod]

        # Bonus de racha: aplica cuando el usuario mantiene ≥7 días consecutivos
        # (la racha actual ya incluye el día de hoy)
        streak_effect   = STREAK_BONUS if streak > STREAK_MIN else 0.0

        session_date    = (START_DATE + timedelta(days=day)).isoformat()

        row: Dict = {
            "user_id":        user.user_id,
            "group":          user.group,
            "age":            user.age,
            "education_years": user.education_years,
            "gender":         user.gender,
            "session_date":   session_date,
            "day_number":     day,
            "week_number":    week,
            "time_of_day":    tod,
            "streak":         streak,
        }

        # ── Métricas por dominio ──────────────────────────────────────────────
        daily_degrad_rate = user.monthly_degradation / 30.0

        for domain in DOMAINS:
            base_acc = user.baseline_scores[domain] / 100.0  # fracción [0, 1]

            # Degradación acumulada en este día
            degrad = (daily_degrad_rate * day
                      if domain in user.degradation_domains else 0.0)

            # Accuracy "verdadera" (sin ruido)
            true_acc = (
                base_acc
                * (1.0 - degrad)
                * (1.0 + practice_effect)
                * (1.0 + tod_effect + streak_effect)
            )
            true_acc = float(np.clip(true_acc, 0.0, 1.0))

            # Ruido gaussiano (±5-10% variabilidad intrasesión)
            noise_std = 0.06 + rng.uniform(0, 0.02)  # varía entre 6% y 8%
            accuracy  = float(np.clip(true_acc + rng.normal(0, noise_std), 0.0, 1.0))
            acc_pct   = accuracy * 100.0

            # RT (sólo para dominios con tiempo de reacción)
            if domain in RT_DOMAINS:
                base_rt      = user.baseline_rt_ms[domain]
                degrad_factor = 1.0 + degrad * 2.0         # RT aumenta al doble de la tasa de degrad.
                acc_factor    = 1.0 + (0.85 - accuracy) * 0.4  # menor accuracy → más lento
                rt_noise_mult = 1.0 + rng.normal(0, 0.12)
                rt_ms = float(np.clip(base_rt * degrad_factor * acc_factor * rt_noise_mult,
                                      200.0, 5_000.0))
                col_rt = f"rt_{domain.lower()}_ms"
                row[col_rt] = round(rt_ms, 1)

                # Speed bonus para score_normalized (≡ lógica del GamificationEngine)
                speed_bonus = max(0.0, (2_000.0 - rt_ms) / 2_000.0) * 20.0
            else:
                # ORIENTATION: sin RT medible (preguntas de opción múltiple, sin presión temporal)
                speed_bonus = 8.0   # bonus fijo moderado

            # Score normalizado [0, 100]
            score_norm = float(np.clip(acc_pct * 0.8 + speed_bonus, 0.0, 100.0))

            # Número de errores (distribución Poisson centrada en (1-accuracy)*12)
            expected_errors = max(0.05, (1.0 - accuracy) * 12.0)
            errors          = int(rng.poisson(expected_errors))

            # Nivel de dificultad adaptativa [1-5]
            difficulty      = int(np.clip(round(accuracy * 5.0), 1, 5))

            d = domain.lower()
            row[f"accuracy_{d}"] = round(acc_pct, 2)
            row[f"score_{d}"]    = round(score_norm, 2)
            row[f"errors_{d}"]   = errors
            row[f"difficulty_{d}"] = difficulty

        rows.append(row)

    return pd.DataFrame(rows)


# ─── 3. Features semanales (14 features ≡ FeatureVector) ─────────────────────

def compute_weekly_features(
    df_user: pd.DataFrame,
    user: UserProfile,
) -> pd.DataFrame:
    """
    Agrega las 14 features por semana para un único usuario, usando una ventana
    deslizante de 4 semanas (≡ FeatureExtractor.extractFeatures en Android).

    Las 14 features producidas coinciden exactamente con ``FeatureVector.NAMES``
    y los rangos de normalización de ``FeatureNormalizer.kt``:

    =========  =========================  =================
    Índice      Feature                    Rango bruto
    =========  =========================  =================
    [0-3]      mean_rt_* (ms)             [200, 5000]
    [4-8]      accuracy_* (%)             [0, 100]
    [9-10]     trend_* (pendiente OLS)    [-10, 10]
    [11]       session_completion_rate    [0, 1]
    [12]       rt_variability (CV)        [0, 1.5]
    [13]       delta_from_baseline        [-3, 3]
    =========  =========================  =================

    Args:
        df_user: DataFrame de sesiones de un único usuario.
        user:    Perfil del usuario (para fallback de baseline).

    Returns:
        DataFrame con una fila por semana (semanas sin sesiones omitidas).
    """
    if df_user.empty:
        return pd.DataFrame()

    # ── Baseline del usuario: primeras BASELINE_WEEKS semanas ─────────────────
    bl_df    = df_user[df_user["week_number"] < BASELINE_WEEKS]
    bl_mean  = {
        d: (bl_df[f"accuracy_{d.lower()}"].mean()
            if len(bl_df) > 0 else user.baseline_scores[d])
        for d in DOMAINS
    }
    bl_std   = {
        d: max(bl_df[f"accuracy_{d.lower()}"].std() if len(bl_df) > 1 else 0.0, 1.0)
        for d in DOMAINS
    }
    bl_rt    = {
        d: (bl_df[f"rt_{d.lower()}_ms"].mean()
            if len(bl_df) > 0 else user.baseline_rt_ms[d])
        for d in RT_DOMAINS
    }

    rows: List[Dict] = []

    for week in range(N_DAYS // 7 + 1):   # semanas 0..12
        # Ventana deslizante de WINDOW_WEEKS semanas hacia atrás (inclusive)
        w_start = max(0, week - WINDOW_WEEKS + 1)
        w_df    = df_user[
            (df_user["week_number"] >= w_start) &
            (df_user["week_number"] <= week)
        ]

        if len(w_df) == 0:
            continue

        window_days = (week - w_start + 1) * 7

        # ── Features 0-3: mean RT por dominio ────────────────────────────────
        mean_rt = {
            d: float(w_df[f"rt_{d.lower()}_ms"].mean())
            for d in RT_DOMAINS
            if f"rt_{d.lower()}_ms" in w_df.columns
        }

        # ── Features 4-8: mean accuracy por dominio ───────────────────────────
        mean_acc = {
            d: float(w_df[f"accuracy_{d.lower()}"].mean())
            for d in DOMAINS
        }

        # ── Features 9-10: pendiente OLS de accuracy por semana ──────────────
        trend_memory    = _ols_slope(w_df, "accuracy_memory",    "day_number")
        trend_attention = _ols_slope(w_df, "accuracy_attention", "day_number")

        # ── Feature 11: session_completion_rate ───────────────────────────────
        completion = float(np.clip(len(w_df) / max(window_days, 1), 0.0, 1.0))

        # ── Feature 12: rt_variability (coeficiente de variación) ─────────────
        all_rts: List[float] = []
        for d in RT_DOMAINS:
            col = f"rt_{d.lower()}_ms"
            if col in w_df.columns:
                all_rts.extend(w_df[col].dropna().tolist())

        if len(all_rts) > 1 and np.mean(all_rts) > 0:
            rt_variability = float(np.std(all_rts) / np.mean(all_rts))
        else:
            rt_variability = 0.0
        rt_variability = min(rt_variability, 1.5)

        # ── Feature 13: delta_from_baseline (z-score) ─────────────────────────
        current_avg = np.mean([mean_acc[d] for d in DOMAINS])
        baseline_avg = np.mean(list(bl_mean.values()))
        pooled_std   = np.mean(list(bl_std.values()))
        delta_from_baseline = float(
            np.clip((current_avg - baseline_avg) / max(pooled_std, 1.0), -3.0, 3.0)
        )

        rows.append({
            "user_id":              user.user_id,
            "group":                user.group,
            "week_number":          week,
            # 14 features (orden ≡ FeatureVector.NAMES)
            "mean_rt_memory":       round(mean_rt.get("MEMORY",    user.baseline_rt_ms.get("MEMORY",    900.0)), 1),
            "mean_rt_attention":    round(mean_rt.get("ATTENTION", user.baseline_rt_ms.get("ATTENTION", 900.0)), 1),
            "mean_rt_executive":    round(mean_rt.get("EXECUTIVE", user.baseline_rt_ms.get("EXECUTIVE", 1000.0)), 1),
            "mean_rt_language":     round(mean_rt.get("LANGUAGE",  user.baseline_rt_ms.get("LANGUAGE",  1000.0)), 1),
            "accuracy_memory":      round(mean_acc["MEMORY"],      2),
            "accuracy_attention":   round(mean_acc["ATTENTION"],   2),
            "accuracy_executive":   round(mean_acc["EXECUTIVE"],   2),
            "accuracy_language":    round(mean_acc["LANGUAGE"],    2),
            "accuracy_orientation": round(mean_acc["ORIENTATION"], 2),
            "trend_memory":         round(trend_memory,    5),
            "trend_attention":      round(trend_attention, 5),
            "session_completion_rate": round(completion,   4),
            "rt_variability":       round(rt_variability,  4),
            "delta_from_baseline":  round(delta_from_baseline, 4),
            # Auxiliar (no entra al modelo)
            "n_sessions_in_window": len(w_df),
        })

    return pd.DataFrame(rows)


def _ols_slope(df: pd.DataFrame, y_col: str, x_col: str = "day_number") -> float:
    """
    Calcula la pendiente OLS de ``y_col`` vs ``x_col``.

    Devuelve 0.0 si hay menos de 2 puntos o si la varianza es cero.

    Args:
        df:    DataFrame con las columnas necesarias.
        y_col: Columna dependiente (accuracy o RT).
        x_col: Columna independiente temporal (day_number).

    Returns:
        Pendiente en unidades de y_col por unidad de x_col.
    """
    valid = df[[x_col, y_col]].dropna()
    if len(valid) < 2 or valid[y_col].std() == 0:
        return 0.0
    result = linregress(valid[x_col].values, valid[y_col].values)
    return float(result.slope)


# ─── 4. Etiquetas por usuario/semana ──────────────────────────────────────────

def compute_labels(df_features: pd.DataFrame) -> pd.DataFrame:
    """
    Asigna etiquetas NORMAL=0, WATCH=1, ALERT=2 por usuario y semana.

    El *anomaly score* mide la desviación del rendimiento actual respecto al
    baseline personal del usuario (semanas 0-1). Combina:

    - Declive de accuracy media (peso 0.40).
    - Aumento de RT media (peso 0.25).
    - Fracción de dominios con accuracy < 60% (peso 0.20).
    - Tendencia negativa (pendiente OLS negativa) (peso 0.10).
    - Delta desde baseline (z-score negativo) (peso 0.05).

    Umbrales (≡ ``CognitiveAnalyzer.kt``):
    - anomaly_score < 0.30 → NORMAL (0)
    - 0.30 ≤ anomaly_score < 0.60 → WATCH  (1)
    - anomaly_score ≥ 0.60 → ALERT  (2)

    Args:
        df_features: DataFrame de features semanales de todos los usuarios.

    Returns:
        DataFrame con columnas: user_id, group, week_number, anomaly_score,
        label, label_name.
    """
    label_rows: List[Dict] = []

    acc_cols = [f"accuracy_{d.lower()}" for d in DOMAINS]
    rt_cols  = [f"mean_rt_{d.lower()}" for d in RT_DOMAINS]

    for user_id, udf in df_features.groupby("user_id"):
        group = udf["group"].iloc[0]

        # Baseline del usuario: semanas 0-(BASELINE_WEEKS-1)
        bl = udf[udf["week_number"] < BASELINE_WEEKS]
        if bl.empty:
            bl = udf.head(1)

        bl_acc_mean = float(bl[acc_cols].mean().mean()) if not bl.empty else 75.0
        bl_rt_mean  = float(bl[rt_cols].mean().mean())  if not bl.empty else 1000.0

        # Desviación estándar dentro del periodo baseline (variabilidad esperada)
        bl_acc_std = max(float(bl[acc_cols].stack().std()) if not bl.empty else 8.0, 2.0)
        bl_rt_std  = max(float(bl[rt_cols].stack().std())  if not bl.empty else 200.0, 50.0)

        for _, row in udf.iterrows():
            cur_acc = float(row[acc_cols].mean())
            cur_rt  = float(row[rt_cols].mean())

            # Componente 1: declive de accuracy en unidades de desviación estándar
            # Normalizado a [0,1]: 3 SD de declive → contribución=1.0
            acc_decline_sd = max(0.0, (bl_acc_mean - cur_acc) / bl_acc_std)
            acc_component  = min(acc_decline_sd / 3.0, 1.0)

            # Componente 2: aumento proporcional de RT
            # Normalizado: 50% de aumento → contribución=1.0
            rt_increase_pct = max(0.0, (cur_rt - bl_rt_mean) / max(bl_rt_mean, 1.0))
            rt_component    = min(rt_increase_pct / 0.50, 1.0)

            # Componente 3: fracción de dominios flaggeados (accuracy < 60%)
            flagged       = sum(1 for c in acc_cols if row[c] < 60.0)
            domain_factor = flagged / len(acc_cols)

            # Componente 4: tendencia negativa (pendiente OLS negativa normalizada)
            trend_neg     = max(0.0, -(row["trend_memory"] + row["trend_attention"]) / 2.0)
            trend_component = min(trend_neg / 2.0, 1.0)   # 2 pp/día de caída → contribución=1

            # Componente 5: delta desde baseline (z-score negativo)
            delta_penalty = max(0.0, -row["delta_from_baseline"]) / 3.0  # 3σ → contribución=1

            # Anomaly score combinado [0, 1]
            # Pesos calibrados: accuracy (dominante), RT, dominio, tendencia, delta
            anomaly = float(np.clip(
                acc_component   * 0.45 +
                rt_component    * 0.25 +
                domain_factor   * 0.18 +
                trend_component * 0.07 +
                delta_penalty   * 0.05,
                0.0, 1.0
            ))

            if anomaly < LABEL_WATCH_THRESHOLD:
                label, label_name = 0, "NORMAL"
            elif anomaly < LABEL_ALERT_THRESHOLD:
                label, label_name = 1, "WATCH"
            else:
                label, label_name = 2, "ALERT"

            label_rows.append({
                "user_id":      user_id,
                "group":        group,
                "week_number":  int(row["week_number"]),
                "anomaly_score": round(anomaly, 4),
                "label":        label,
                "label_name":   label_name,
            })

    return pd.DataFrame(label_rows)


# ─── 5. Normalización de features ─────────────────────────────────────────────

def normalize_features(df_features: pd.DataFrame) -> pd.DataFrame:
    """
    Aplica la normalización min-max de ``FeatureNormalizer.kt`` a las 14 features.

    Añade columnas ``norm_<feature_name>`` al DataFrame sin modificar las originales.
    Los valores fuera del rango de entrenamiento se recortan a [0, 1].

    Args:
        df_features: DataFrame con las 14 features brutas.

    Returns:
        DataFrame original con 14 columnas adicionales normalizadas.
    """
    feature_cols = [
        "mean_rt_memory", "mean_rt_attention", "mean_rt_executive", "mean_rt_language",
        "accuracy_memory", "accuracy_attention", "accuracy_executive",
        "accuracy_language", "accuracy_orientation",
        "trend_memory", "trend_attention",
        "session_completion_rate", "rt_variability", "delta_from_baseline",
    ]
    df_out = df_features.copy()
    for i, col in enumerate(feature_cols):
        lo, hi = NORM_MIN[i], NORM_MAX[i]
        df_out[f"norm_{col}"] = ((df_out[col] - lo) / (hi - lo)).clip(0.0, 1.0)
    return df_out


# ─── 6. Visualizaciones ───────────────────────────────────────────────────────

def generate_visualizations(
    df_sessions:  pd.DataFrame,
    df_features:  pd.DataFrame,
    df_labels:    pd.DataFrame,
    viz_dir:      Path,
) -> None:
    """
    Genera 6 visualizaciones para el análisis exploratorio del dataset.

    Figuras producidas:
        01_accuracy_distribution.png    — Histogramas de accuracy por dominio y grupo.
        02_score_trajectory.png         — Evolución media ± 1σ del score por grupo.
        03_rt_distribution.png          — Distribución de RT por dominio y grupo.
        04_label_distribution.png       — Distribución de etiquetas por grupo y semana.
        05_feature_correlation.png      — Heatmap de correlación entre las 14 features.
        06_anomaly_score_trajectory.png — Evolución del anomaly score por grupo.

    Args:
        df_sessions:  Sesiones individuales con métricas brutas.
        df_features:  Features semanales agregadas.
        df_labels:    Etiquetas por usuario y semana.
        viz_dir:      Directorio de salida para las imágenes.
    """
    viz_dir.mkdir(parents=True, exist_ok=True)
    plt.rcParams.update({"figure.dpi": 130, "font.size": 10})

    PALETTE = {
        "NORMAL":        "#27ae60",
        "DCL_LEVE":      "#e67e22",
        "DCL_MODERADO":  "#c0392b",
    }

    # ── Figura 1: Distribución de accuracy por dominio y grupo ────────────────
    fig, axes = plt.subplots(2, 3, figsize=(15, 9))
    fig.suptitle("Distribución de Accuracy por Dominio y Grupo Clínico",
                 fontsize=13, fontweight="bold", y=1.01)

    for ax, domain in zip(axes.flat[:5], DOMAINS):
        col = f"accuracy_{domain.lower()}"
        for group, color in PALETTE.items():
            vals = df_sessions[df_sessions["group"] == group][col].dropna()
            ax.hist(vals, bins=40, alpha=0.55, label=group, color=color, density=True)
        ax.set_title(f"{domain}", fontweight="bold")
        ax.set_xlabel("Accuracy (%)")
        ax.set_ylabel("Densidad")
        ax.legend(fontsize=8)
        ax.grid(alpha=0.3, linewidth=0.5)

    axes.flat[5].axis("off")
    plt.tight_layout()
    fig.savefig(viz_dir / "01_accuracy_distribution.png", bbox_inches="tight")
    plt.close(fig)

    # ── Figura 2: Evolución del score medio por grupo a lo largo de las semanas ─
    acc_cols = [f"accuracy_{d.lower()}" for d in DOMAINS]
    df_sessions = df_sessions.copy()
    df_sessions["mean_accuracy"] = df_sessions[acc_cols].mean(axis=1)

    fig, ax = plt.subplots(figsize=(12, 5))
    for group, color in PALETTE.items():
        gdf = df_sessions[df_sessions["group"] == group].groupby("week_number")["mean_accuracy"]
        mu, sigma = gdf.mean(), gdf.std()
        ax.plot(mu.index, mu.values, color=color, label=group, linewidth=2.2)
        ax.fill_between(mu.index, mu - sigma, mu + sigma, alpha=0.15, color=color)

    ax.set_xlabel("Semana")
    ax.set_ylabel("Accuracy Media (%)")
    ax.set_title("Evolución del Rendimiento Cognitivo por Grupo (media ± 1σ)",
                 fontweight="bold")
    ax.legend(fontsize=10)
    ax.grid(alpha=0.3, linewidth=0.5)
    plt.tight_layout()
    fig.savefig(viz_dir / "02_score_trajectory.png", bbox_inches="tight")
    plt.close(fig)

    # ── Figura 3: Distribución de RT por dominio y grupo ──────────────────────
    fig, axes = plt.subplots(1, 4, figsize=(16, 5))
    fig.suptitle("Distribución de Tiempo de Reacción por Dominio y Grupo",
                 fontsize=12, fontweight="bold")

    for ax, domain in zip(axes, RT_DOMAINS):
        col = f"rt_{domain.lower()}_ms"
        for group, color in PALETTE.items():
            vals = df_sessions[df_sessions["group"] == group][col].dropna()
            ax.hist(vals, bins=40, alpha=0.55, label=group, color=color, density=True)
        ax.set_title(domain, fontweight="bold")
        ax.set_xlabel("RT (ms)")
        ax.set_ylabel("Densidad" if domain == RT_DOMAINS[0] else "")
        ax.grid(alpha=0.3, linewidth=0.5)
        if domain == RT_DOMAINS[0]:
            ax.legend(fontsize=8)

    plt.tight_layout()
    fig.savefig(viz_dir / "03_rt_distribution.png", bbox_inches="tight")
    plt.close(fig)

    # ── Figura 4: Distribución de etiquetas por grupo ─────────────────────────
    label_order = ["NORMAL", "WATCH", "ALERT"]
    label_colors = ["#27ae60", "#e67e22", "#c0392b"]

    counts = (df_labels.groupby(["group", "label_name"])
              .size()
              .unstack(fill_value=0)
              .reindex(columns=label_order, fill_value=0))

    fig, ax = plt.subplots(figsize=(9, 5))
    counts.plot(kind="bar", ax=ax, color=label_colors, edgecolor="white", width=0.7)
    ax.set_xlabel("Grupo Clínico")
    ax.set_ylabel("Número de semanas-usuario")
    ax.set_title("Distribución de Etiquetas por Grupo Clínico", fontweight="bold")
    ax.legend(title="Etiqueta", fontsize=9)
    ax.set_xticklabels(ax.get_xticklabels(), rotation=0)
    ax.grid(axis="y", alpha=0.3, linewidth=0.5)
    plt.tight_layout()
    fig.savefig(viz_dir / "04_label_distribution.png", bbox_inches="tight")
    plt.close(fig)

    # ── Figura 5: Heatmap de correlación entre las 14 features ───────────────
    feat_cols = [
        "mean_rt_memory", "mean_rt_attention", "mean_rt_executive", "mean_rt_language",
        "accuracy_memory", "accuracy_attention", "accuracy_executive",
        "accuracy_language", "accuracy_orientation",
        "trend_memory", "trend_attention",
        "session_completion_rate", "rt_variability", "delta_from_baseline",
    ]
    short_names = [
        "RT_mem", "RT_att", "RT_exec", "RT_lang",
        "Acc_mem", "Acc_att", "Acc_exec", "Acc_lang", "Acc_ori",
        "Trend_mem", "Trend_att",
        "Completion", "RT_CV", "ΔBaseline",
    ]
    corr = df_features[feat_cols].corr()
    corr.index   = short_names
    corr.columns = short_names

    fig, ax = plt.subplots(figsize=(11, 9))
    sns.heatmap(corr, ax=ax, cmap="coolwarm", center=0, vmin=-1, vmax=1,
                annot=True, fmt=".2f", annot_kws={"size": 7},
                linewidths=0.4, square=True)
    ax.set_title("Correlación entre las 14 Features del Modelo (n=300 usuarios)",
                 fontsize=12, fontweight="bold", pad=12)
    plt.tight_layout()
    fig.savefig(viz_dir / "05_feature_correlation.png", bbox_inches="tight")
    plt.close(fig)

    # ── Figura 6: Evolución del anomaly score por grupo ───────────────────────
    fig, ax = plt.subplots(figsize=(12, 5))
    for group, color in PALETTE.items():
        gdf = df_labels[df_labels["group"] == group].groupby("week_number")["anomaly_score"]
        mu, sigma = gdf.mean(), gdf.std()
        ax.plot(mu.index, mu.values, color=color, label=group, linewidth=2.2)
        ax.fill_between(mu.index, mu - sigma, mu + sigma, alpha=0.12, color=color)

    ax.axhline(LABEL_WATCH_THRESHOLD, color="#e67e22", linestyle="--",
               linewidth=1.3, alpha=0.8, label=f"Umbral WATCH ({LABEL_WATCH_THRESHOLD})")
    ax.axhline(LABEL_ALERT_THRESHOLD, color="#c0392b", linestyle="--",
               linewidth=1.3, alpha=0.8, label=f"Umbral ALERT ({LABEL_ALERT_THRESHOLD})")

    ax.set_xlabel("Semana")
    ax.set_ylabel("Anomaly Score")
    ax.set_title("Evolución del Anomaly Score por Grupo (media ± 1σ)", fontweight="bold")
    ax.set_ylim(-0.05, 1.05)
    ax.legend(fontsize=9)
    ax.grid(alpha=0.3, linewidth=0.5)
    plt.tight_layout()
    fig.savefig(viz_dir / "06_anomaly_score_trajectory.png", bbox_inches="tight")
    plt.close(fig)

    print(f"  ✓  6 visualizaciones guardadas en '{viz_dir.relative_to(_SCRIPT_DIR)}/'")


# ─── 7. Resumen estadístico ───────────────────────────────────────────────────

def generate_stats_summary(
    users:       List[UserProfile],
    df_sessions: pd.DataFrame,
    df_features: pd.DataFrame,
    df_labels:   pd.DataFrame,
    output_path: Path,
) -> None:
    """
    Genera un resumen estadístico completo en formato texto para la tesis.

    Incluye:
    - Parámetros de generación de datos.
    - Distribución de usuarios por grupo.
    - Estadísticas de sesiones (completitud, distribución temporal).
    - Estadísticas de accuracy y RT por grupo y dominio.
    - Distribución de etiquetas.
    - Estadísticas de las 14 features por grupo.
    - Matriz de correlación resumida.

    Args:
        users:       Lista de perfiles generados.
        df_sessions: DataFrame de sesiones.
        df_features: DataFrame de features semanales.
        df_labels:   DataFrame de etiquetas.
        output_path: Ruta del archivo de salida .txt.
    """
    buf = io.StringIO()
    SEP = "=" * 70
    SEP2 = "-" * 70

    def w(s: str = "") -> None:
        buf.write(s + "\n")

    w(SEP)
    w("  EternaMente — Resumen Estadístico del Dataset Sintético")
    w(f"  Generado con seed={SEED} | {N_USERS} usuarios | {N_DAYS} días por usuario")
    w(SEP)
    w()

    # ── Parámetros ────────────────────────────────────────────────────────────
    w("1. PARÁMETROS DE GENERACIÓN")
    w(SEP2)
    w(f"  Seed de reproducibilidad:       {SEED}")
    w(f"  Número de usuarios:             {N_USERS}")
    w(f"  Días simulados por usuario:     {N_DAYS}")
    w(f"  Tasa de ausencia (por día):     {ABSENCE_RATE*100:.0f}%")
    w(f"  Efecto de práctica:             +{PRACTICE_GAIN*100:.1f}%/semana (primeras {PRACTICE_WEEKS} semanas)")
    w(f"  Bonus de racha:                 +{STREAK_BONUS*100:.0f}% si streak ≥ {STREAK_MIN} días")
    w(f"  Fecha de inicio simulación:     {START_DATE.isoformat()}")
    w(f"  Ventana de features:            {WINDOW_WEEKS} semanas")
    w(f"  Semanas de baseline:            {BASELINE_WEEKS}")
    w()
    w("  Efectos del momento del día:")
    for k, v in TIME_OF_DAY_EFFECTS.items():
        w(f"    {k:12s}: {v:+.0%}")
    w()
    w("  Tasa de degradación mensual por grupo:")
    for group, dcfg in DEGRADATION_CONFIG.items():
        lo, hi = dcfg["rate_range"]
        dom_str = ", ".join(dcfg["domains"]) if dcfg["domains"] else "ninguno"
        w(f"    {group:15s}: {lo*100:.0f}–{hi*100:.0f}% | dominios: {dom_str}")
    w()

    # ── Distribución de usuarios ──────────────────────────────────────────────
    w("2. DISTRIBUCIÓN DE USUARIOS")
    w(SEP2)
    for group, n in GROUP_SIZES.items():
        pct = n / N_USERS * 100
        cfg = BASELINE_CONFIG[group]
        w(f"  {group:15s}: n={n:3d} ({pct:.0f}%) | "
          f"baseline_score={cfg['score_mean']}±{cfg['score_std']} | "
          f"RT_base={cfg['rt_mean']}±{cfg['rt_std']} ms")
    w()
    w("  Demografía (todos los grupos combinados):")
    w(f"    Edad media:           {np.mean([u.age for u in users]):.1f} ± {np.std([u.age for u in users]):.1f} años")
    w(f"    Distribución género:  M={sum(1 for u in users if u.gender=='M')} / F={sum(1 for u in users if u.gender=='F')}")
    edu_vals = [u.education_years for u in users]
    for edu in [6, 9, 12, 16]:
        w(f"    {edu} años de educación: {edu_vals.count(edu)} usuarios ({edu_vals.count(edu)/N_USERS*100:.0f}%)")
    w()

    # ── Estadísticas de sesiones ──────────────────────────────────────────────
    w("3. ESTADÍSTICAS DE SESIONES")
    w(SEP2)
    total_sessions = len(df_sessions)
    avg_per_user   = total_sessions / N_USERS
    expected_max   = N_DAYS * N_USERS
    w(f"  Total sesiones generadas:       {total_sessions:,}")
    w(f"  Sesiones por usuario (media):   {avg_per_user:.1f}")
    w(f"  Tasa de completitud real:       {total_sessions/expected_max*100:.1f}%  "
      f"(esperado ~{(1-ABSENCE_RATE)*100:.0f}%)")
    w()
    w("  Sesiones por grupo:")
    for group in GROUP_SIZES:
        n = len(df_sessions[df_sessions["group"] == group])
        n_users = GROUP_SIZES[group]
        w(f"    {group:15s}: {n:6,} sesiones | {n/n_users:.1f}/usuario")
    w()
    w("  Distribución horaria:")
    for tod in ["morning", "afternoon", "night"]:
        n = (df_sessions["time_of_day"] == tod).sum()
        w(f"    {tod:12s}: {n:6,} ({n/total_sessions*100:.1f}%)")
    w()

    # ── Estadísticas de accuracy por dominio y grupo ──────────────────────────
    w("4. ACCURACY POR DOMINIO Y GRUPO (media ± std)")
    w(SEP2)
    header = f"  {'Dominio':<12}" + "".join(f"  {g:>18}" for g in GROUP_SIZES)
    w(header)
    w("  " + "-" * (12 + 20 * len(GROUP_SIZES)))
    for domain in DOMAINS:
        col = f"accuracy_{domain.lower()}"
        row_str = f"  {domain:<12}"
        for group in GROUP_SIZES:
            vals = df_sessions[df_sessions["group"] == group][col]
            row_str += f"  {vals.mean():6.1f} ± {vals.std():4.1f}%"
        w(row_str)
    w()

    # ── Estadísticas de RT por dominio y grupo ────────────────────────────────
    w("5. TIEMPO DE REACCIÓN POR DOMINIO Y GRUPO (media ± std, ms)")
    w(SEP2)
    w(header)
    w("  " + "-" * (12 + 20 * len(GROUP_SIZES)))
    for domain in RT_DOMAINS:
        col = f"rt_{domain.lower()}_ms"
        row_str = f"  {domain:<12}"
        for group in GROUP_SIZES:
            vals = df_sessions[df_sessions["group"] == group][col].dropna()
            row_str += f"  {vals.mean():6.0f} ± {vals.std():4.0f}"
        w(row_str)
    w()

    # ── Distribución de etiquetas ─────────────────────────────────────────────
    w("6. DISTRIBUCIÓN DE ETIQUETAS")
    w(SEP2)
    total_rows = len(df_labels)
    w(f"  Total filas (usuario × semana): {total_rows:,}")
    w()
    for group in GROUP_SIZES:
        gdf = df_labels[df_labels["group"] == group]
        n   = len(gdf)
        w(f"  {group}  (n_filas={n:,})")
        for lname in ["NORMAL", "WATCH", "ALERT"]:
            cnt = (gdf["label_name"] == lname).sum()
            w(f"    {lname:6s}: {cnt:4d} ({cnt/n*100:5.1f}%)")
    w()
    w("  Distribución global:")
    for lname in ["NORMAL", "WATCH", "ALERT"]:
        cnt = (df_labels["label_name"] == lname).sum()
        w(f"    {lname:6s}: {cnt:5d} ({cnt/total_rows*100:5.1f}%)")
    w()

    # ── Estadísticas de features semanales ───────────────────────────────────
    w("7. FEATURES SEMANALES — ESTADÍSTICAS POR GRUPO")
    w(SEP2)
    feat_display = {
        "mean_rt_memory":          "RT MEMORY (ms)  ",
        "mean_rt_attention":       "RT ATTENTION (ms)",
        "accuracy_memory":         "Acc MEMORY (%)  ",
        "accuracy_orientation":    "Acc ORIENT (%)  ",
        "trend_memory":            "Trend MEMORY    ",
        "session_completion_rate": "Completion rate ",
        "rt_variability":          "RT variability  ",
        "delta_from_baseline":     "ΔBaseline (z)   ",
    }
    header2 = f"  {'Feature':<22}" + "".join(f"  {g:>22}" for g in GROUP_SIZES)
    w(header2)
    w("  " + "-" * (24 + 24 * len(GROUP_SIZES)))
    for col, label in feat_display.items():
        row_str = f"  {label:<22}"
        for group in GROUP_SIZES:
            vals = df_features[df_features["group"] == group][col].dropna()
            row_str += f"  {vals.mean():8.3f} ± {vals.std():6.3f}"
        w(row_str)
    w()

    # ── Correlaciones notables ────────────────────────────────────────────────
    w("8. CORRELACIONES ENTRE FEATURES (|r| > 0.50)")
    w(SEP2)
    feat_cols = [
        "mean_rt_memory", "mean_rt_attention", "mean_rt_executive", "mean_rt_language",
        "accuracy_memory", "accuracy_attention", "accuracy_executive",
        "accuracy_language", "accuracy_orientation",
        "trend_memory", "trend_attention",
        "session_completion_rate", "rt_variability", "delta_from_baseline",
    ]
    corr = df_features[feat_cols].corr()
    printed = set()
    for i, c1 in enumerate(feat_cols):
        for j, c2 in enumerate(feat_cols):
            if i >= j:
                continue
            r = corr.loc[c1, c2]
            if abs(r) > 0.50:
                pair = tuple(sorted([c1, c2]))
                if pair not in printed:
                    w(f"  {c1:<30} × {c2:<30} r = {r:+.3f}")
                    printed.add(pair)
    if not printed:
        w("  (Ninguna correlación supera 0.50)")
    w()

    # ── Notas metodológicas ───────────────────────────────────────────────────
    w("9. NOTAS METODOLÓGICAS")
    w(SEP2)
    w("  • Los scores de baseline por usuario siguen una distribución normal con")
    w("    media y desviación estándar según el grupo clínico, más un factor g")
    w("    individual que introduce correlación entre dominios.")
    w("  • La degradación es lineal desde el día 0; en la realidad el DCL tiene")
    w("    un inicio insidioso y variable — este modelo es una aproximación.")
    w("  • El anomaly score de etiquetado NO es el mismo que el del IsolationForest")
    w("    (el modelo Android); es un proxy heurístico para la generación de labels.")
    w("  • Los datos están desequilibrados por diseño (60/25/15%) para reflejar")
    w("    la prevalencia real del DCL en adultos mayores (Petersen et al., 2018).")
    w("  • Para usarse como ground truth, las etiquetas deben validarse por un")
    w("    neuropsicólogo clínico con acceso a evaluaciones formales.")
    w()
    w(SEP)
    w("  Generado automáticamente por generate_synthetic_data.py (EternaMente)")
    w(SEP)

    output_path.write_text(buf.getvalue(), encoding="utf-8")
    print(f"  ✓  Resumen estadístico guardado en '{output_path.relative_to(_SCRIPT_DIR)}'")


# ─── 8. Main ──────────────────────────────────────────────────────────────────

def main() -> None:
    """
    Punto de entrada principal.

    Ejecuta el pipeline completo:
    1. Generar perfiles de usuario.
    2. Simular sesiones día a día para cada usuario.
    3. Agregar features semanales (14 features ≡ FeatureVector).
    4. Asignar etiquetas NORMAL/WATCH/ALERT.
    5. Normalizar features (coincide con FeatureNormalizer.kt).
    6. Guardar los 3 CSVs principales.
    7. Generar visualizaciones.
    8. Generar resumen estadístico.
    """
    t0 = time.perf_counter()
    rng = np.random.default_rng(SEED)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    VIZ_DIR.mkdir(parents=True, exist_ok=True)

    print()
    print("══════════════════════════════════════════════════")
    print("  EternaMente — Generador de Datos Sintéticos ML")
    print(f"  seed={SEED} | n_users={N_USERS} | n_days={N_DAYS}")
    print("══════════════════════════════════════════════════")
    print()

    # ── Paso 1: Perfiles de usuario ───────────────────────────────────────────
    print("▶ Paso 1/6  Generando perfiles de usuario...")
    users = generate_users(rng)
    print(f"  ✓  {len(users)} perfiles creados: "
          f"NORMAL={GROUP_SIZES['NORMAL']}, "
          f"DCL_LEVE={GROUP_SIZES['DCL_LEVE']}, "
          f"DCL_MODERADO={GROUP_SIZES['DCL_MODERADO']}")
    print()

    # ── Paso 2: Sesiones ──────────────────────────────────────────────────────
    print(f"▶ Paso 2/6  Simulando sesiones ({N_USERS} usuarios × {N_DAYS} días)...")
    all_sessions: List[pd.DataFrame] = []

    for i, user in enumerate(users):
        if (i + 1) % 60 == 0 or (i + 1) == N_USERS:
            print(f"  ... {i + 1}/{N_USERS} usuarios procesados")
        df_u = generate_sessions_for_user(user, rng)
        all_sessions.append(df_u)

    df_sessions = pd.concat(all_sessions, ignore_index=True)
    n_sessions  = len(df_sessions)
    print(f"  ✓  {n_sessions:,} sesiones generadas "
          f"(completitud={n_sessions/(N_USERS*N_DAYS)*100:.1f}%)")
    print()

    # ── Paso 3: Features semanales ────────────────────────────────────────────
    print("▶ Paso 3/6  Calculando features semanales (14 features × usuario × semana)...")
    all_features: List[pd.DataFrame] = []

    # Mapear user_id → UserProfile para acceso rápido
    user_map = {u.user_id: u for u in users}

    for user_id, df_u in df_sessions.groupby("user_id"):
        user    = user_map[user_id]
        df_feat = compute_weekly_features(df_u, user)
        all_features.append(df_feat)

    df_features_raw = pd.concat(all_features, ignore_index=True)
    df_features     = normalize_features(df_features_raw)
    print(f"  ✓  {len(df_features):,} filas de features "
          f"({df_features['week_number'].nunique()} semanas × {N_USERS} usuarios)")
    print()

    # ── Paso 4: Etiquetas ─────────────────────────────────────────────────────
    print("▶ Paso 4/6  Asignando etiquetas NORMAL / WATCH / ALERT...")
    df_labels = compute_labels(df_features_raw)
    label_dist = df_labels["label_name"].value_counts()
    total_lb   = len(df_labels)
    print(f"  ✓  {total_lb:,} etiquetas asignadas: "
          f"NORMAL={label_dist.get('NORMAL',0)} ({label_dist.get('NORMAL',0)/total_lb*100:.0f}%), "
          f"WATCH={label_dist.get('WATCH',0)} ({label_dist.get('WATCH',0)/total_lb*100:.0f}%), "
          f"ALERT={label_dist.get('ALERT',0)} ({label_dist.get('ALERT',0)/total_lb*100:.0f}%)")
    print()

    # ── Paso 5: Guardar CSVs ─────────────────────────────────────────────────
    print("▶ Paso 5/6  Guardando archivos CSV...")

    sessions_path  = OUTPUT_DIR / "df_sessions.csv"
    features_path  = OUTPUT_DIR / "df_features_weekly.csv"
    labels_path    = OUTPUT_DIR / "df_labels.csv"

    df_sessions.to_csv(sessions_path,  index=False, encoding="utf-8")
    df_features.to_csv(features_path,  index=False, encoding="utf-8")
    df_labels.to_csv(labels_path,      index=False, encoding="utf-8")

    def fsize(p: Path) -> str:
        kb = p.stat().st_size / 1024
        return f"{kb:.0f} KB" if kb < 1024 else f"{kb/1024:.1f} MB"

    print(f"  ✓  df_sessions.csv        ({fsize(sessions_path)},  {len(df_sessions):,} filas)")
    print(f"  ✓  df_features_weekly.csv ({fsize(features_path)}, {len(df_features):,} filas)")
    print(f"  ✓  df_labels.csv          ({fsize(labels_path)},  {len(df_labels):,} filas)")
    print()

    # ── Paso 6: Visualizaciones y resumen ─────────────────────────────────────
    print("▶ Paso 6/6  Generando visualizaciones y resumen estadístico...")
    generate_visualizations(df_sessions, df_features_raw, df_labels, VIZ_DIR)

    stats_path = OUTPUT_DIR / "stats_summary.txt"
    generate_stats_summary(users, df_sessions, df_features_raw, df_labels, stats_path)
    print()

    elapsed = time.perf_counter() - t0
    print("══════════════════════════════════════════════════")
    print(f"  ✓  Generación completada en {elapsed:.1f}s")
    print()
    print("  Archivos generados:")
    print(f"    {OUTPUT_DIR.relative_to(_SCRIPT_DIR)}/")
    print(f"    ├── df_sessions.csv")
    print(f"    ├── df_features_weekly.csv")
    print(f"    ├── df_labels.csv")
    print(f"    ├── stats_summary.txt")
    print(f"    └── visualizations/")
    print(f"        ├── 01_accuracy_distribution.png")
    print(f"        ├── 02_score_trajectory.png")
    print(f"        ├── 03_rt_distribution.png")
    print(f"        ├── 04_label_distribution.png")
    print(f"        ├── 05_feature_correlation.png")
    print(f"        └── 06_anomaly_score_trajectory.png")
    print("══════════════════════════════════════════════════")
    print()


if __name__ == "__main__":
    main()
