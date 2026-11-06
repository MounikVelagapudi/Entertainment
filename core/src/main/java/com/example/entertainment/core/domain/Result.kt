package com.example.entertainment.core.domain

/**
 * Sealed interface to handle either left or right values
 * https://apidocs.arrow-kt.io/arrow-core/arrow.core/-either/index.html
 */

sealed interface Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>

    data class Right<out R>(val value: R) : Either<Nothing, R>
}

/***
 * Result class to handle the success and error responses
 */

sealed interface Result<out D, out E : TRPError> {
    data class Success<out D, out E : TRPError>(val data: D) : Result<D, E>

    data class Error<out D, out E : TRPError>(val error: E) : Result<D, E>

    data class Failure<out D, out E : TRPError>(val failure: TRPFailure) : Result<D, E>
}

/**
 * Extension functions to handle the success response
 */

suspend inline fun <T> Result<T, TRPError>.onSuccess(
    crossinline action: suspend (T) -> Unit,
): Result<T, TRPError> {
    if (this is Result.Success) {
        action(data)
    }
    return this
}

/**
 * Extension functions to handle the error response
 */

suspend inline fun <T> Result<T, TRPError>.onError(
    crossinline action: suspend (TRPError) -> Unit,
): Result<T, TRPError> {
    if (this is Result.Error) {
        action(error)
    }
    return this
}

/**
 * Extension functions to handle the failure
 */

suspend inline fun <T> Result<T, TRPError>.onFailure(
    crossinline action: suspend (TRPFailure) -> Unit,
): Result<T, TRPError> {
    if (this is Result.Failure) {
        action(failure)
    }
    return this
}

/**
 * Extension functions to handle the success response and map the data to another type using the transform function
 */

suspend inline fun <T, R> Result<T, TRPError>.map(
    crossinline transform: suspend (T) -> R,
): Result<R, TRPError> {
    return when (this) {
        is Result.Success -> Result.Success(transform(data))
        is Result.Error -> Result.Error(error)
        is Result.Failure -> Result.Failure(failure)
    }
}

/**
 * Extension functions to handle the success response and flatMap the data to another type using the transform function
 */

inline fun <T, R> Result<T, TRPError>.flatMap(
    crossinline transform: (T) -> Result<R, TRPError>,
): Result<R, TRPError> {
    return when (this) {
        is Result.Success -> transform(data)
        is Result.Error -> Result.Error(error)
        is Result.Failure -> Result.Failure(failure)
    }
}

/**
 * Extension functions to handle the success response and validate the data using the predicate function
 */

inline fun <D> Result<D, TRPError>.validate(
    crossinline predicate: (D) -> Boolean,
): Result<D, TRPError> {
    return when (this) {
        is Result.Success -> if (predicate(data)) this else Result.Error(DataError.BadDataError)
        is Result.Error -> this
        is Result.Failure -> this
    }
}

/**
 * Extension functions to handle the success response and fold the data to another type using the transform function
 */

inline fun <T, R> Result<T, TRPError>.fold(
    onSuccess: (T) -> R,
    onError: (TRPError) -> R,
    onFailure: (TRPFailure) -> R,
): R {
    return when (this) {
        is Result.Success -> onSuccess(data)
        is Result.Error -> onError(error)
        is Result.Failure -> onFailure(failure)
    }
}
