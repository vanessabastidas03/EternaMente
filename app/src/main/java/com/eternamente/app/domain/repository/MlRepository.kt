package com.eternamente.app.domain.repository

import com.eternamente.app.core.Result
import com.eternamente.app.domain.model.MlPrediction
import kotlinx.coroutines.flow.Flow

/**
 * Contract for on-device ML inference execution and prediction history management.
 *
 * The data layer implementation wraps the TensorFlow Lite interpreter and the
 * Room DAO for prediction persistence. All inference runs on-device; no raw
 * feature data is transmitted to any server.
 */
interface MlRepository {

    /**
     * Assembles the ML feature vector from the user's recent game history,
     * runs the TFLite cognitive risk model, persists the result, and returns it.
     *
     * This is a long-running operation (50–200 ms) and should be called from
     * a background coroutine dispatcher.
     *
     * @param userId UUID of the user to evaluate.
     * @return [Result.Success] with the new [MlPrediction], or [Result.Error] on
     *   insufficient data (fewer than 3 completed sessions) or TFLite failure.
     */
    suspend fun runPrediction(userId: String): Result<MlPrediction>

    /**
     * Retrieves the most recent [MlPrediction] for a user from the local cache.
     *
     * @param userId UUID of the target user.
     * @return [Result.Success] containing the prediction or `null` if none exists yet.
     */
    suspend fun getLatestPrediction(userId: String): Result<MlPrediction?>

    /**
     * Emits the full prediction history for a user, newest first.
     *
     * The Flow updates reactively whenever a new prediction is stored by [runPrediction].
     *
     * @param userId UUID of the target user.
     */
    fun observePredictionHistory(userId: String): Flow<List<MlPrediction>>

    /**
     * Persists a pre-computed [MlPrediction] without re-running inference.
     *
     * Useful for restoring predictions from a remote backup or testing.
     *
     * @param prediction The [MlPrediction] to store.
     * @return [Result.Success] with the stored prediction, or [Result.Error] on failure.
     */
    suspend fun savePrediction(prediction: MlPrediction): Result<MlPrediction>

    /**
     * Deletes all predictions older than [beforeEpochMs] for a given user.
     *
     * Called by the data-retention WorkManager task to enforce the 12-month
     * rolling window mandated by the privacy policy.
     *
     * @param userId UUID of the target user.
     * @param beforeEpochMs Epoch-millis cutoff; predictions before this are removed.
     * @return [Result.Success] with the number of records deleted, or [Result.Error] on failure.
     */
    suspend fun purgePredictionsBefore(userId: String, beforeEpochMs: Long): Result<Int>

    /**
     * Returns the version string of the currently loaded TFLite model
     * (e.g., `"mci_risk_v2.1"`).
     *
     * Used for telemetry and to detect when a model update requires re-running predictions.
     */
    suspend fun getCurrentModelVersion(): Result<String>
}
