package com.eternamente.app.core

/**
 * Generic wrapper for use-case outcomes, replacing exception-based control flow.
 *
 * All use cases in the domain layer return `Result<T>` so callers can handle
 * success and failure paths explicitly without try/catch at call sites.
 *
 * @param T The type of the successful result value.
 */
sealed class Result<out T> {

    /**
     * Represents a successful operation.
     *
     * @property data The result value produced by the operation.
     */
    data class Success<out T>(val data: T) : Result<T>()

    /**
     * Represents a failed operation.
     *
     * @property exception The exception that caused the failure.
     */
    data class Error(val exception: Exception) : Result<Nothing>()

    /** Returns `true` if this is a [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** Returns `true` if this is an [Error]. */
    val isError: Boolean get() = this is Error
}

/**
 * Executes [block] if this result is [Result.Success], then returns the receiver unchanged.
 * Enables fluent chaining: `result.onSuccess { ... }.onError { ... }`.
 */
inline fun <T> Result<T>.onSuccess(block: (T) -> Unit): Result<T> {
    if (this is Result.Success) block(data)
    return this
}

/**
 * Executes [block] if this result is [Result.Error], then returns the receiver unchanged.
 */
inline fun <T> Result<T>.onError(block: (Exception) -> Unit): Result<T> {
    if (this is Result.Error) block(exception)
    return this
}

/**
 * Transforms the payload of a [Result.Success] using [transform].
 * A [Result.Error] is passed through unchanged.
 */
inline fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error   -> this
}

/**
 * Returns the [Result.Success] payload, or `null` if this is a [Result.Error].
 */
fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.data

/**
 * Returns the [Result.Success] payload, or [default] if this is a [Result.Error].
 *
 * @param default Fallback value to return on error.
 */
fun <T> Result<T>.getOrDefault(default: T): T = (this as? Result.Success)?.data ?: default

/**
 * Wraps a suspending [block] in a `try/catch`, returning [Result.Success] on
 * normal completion or [Result.Error] if any [Exception] is thrown.
 *
 * Convenient for repository implementations:
 * ```kotlin
 * return runCatching { dao.insert(entity) }
 * ```
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    Result.Error(e)
}

/**
 * Unwraps the [Result.Success] payload or throws the wrapped exception.
 * Shared by all use cases that need to chain operations.
 */
fun <T> Result<T>.getOrThrow(): T = when (this) {
    is Result.Success -> data
    is Result.Error   -> throw exception
}
