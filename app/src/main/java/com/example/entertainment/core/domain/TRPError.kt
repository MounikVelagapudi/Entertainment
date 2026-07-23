package com.example.entertainment.core.domain

/**
 * Root Error interface - recoverable.
 * All the errors in the app should implement this interface.
 */
sealed interface TRPError

/**
 * Root Failure interface - non-recoverable.
 * All Failures in the app should implement this interface.
 */
sealed interface TRPFailure : TRPError

sealed interface RootNetworkError : TRPError, TRPFailure

/**
 * NetworkFailure types - can be recovered even by refresh.
 * IOFailure -> Network connection issues, Timeout, No internet, etc.
 * EmptyResponseFailure -> Network call was successful but API returned an empty response.
 * MalformedJsonFailure -> Network call was successful but API returned a malformed JSON response.
 */

sealed interface NetworkFailure : RootNetworkError {
    data object IOFailure : NetworkFailure

    data object EmptyResponseFailure : NetworkFailure

    data object MalformedJsonFailure : NetworkFailure

    data object UnknownFailure : NetworkFailure
}

/**
 * NetworkError types - can be recovered by refresh.
 * NoInternetError -> Network connection issues, No internet, etc.
 * TimeoutError -> Network connection timeout.
 * ConnectionError -> General network connection error.
 */
sealed interface NetworkError : RootNetworkError {
    data object NoInternetError : NetworkError

    data object TimeoutError : NetworkError

    data object ConnectionError : NetworkError

    data object EmptyResponseError : NetworkError
}

/**
 * APIError -> Network call was successful but API returned an error response.
 * UnAuthenticatedError -> 401, 403 errors.
 */
sealed class APIError(
    open val code: Int,
    open val serviceErrorCode: Int = Int.MIN_VALUE,
    open val message: String,
) : NetworkError {
    data class UnAuthenticatedError(
        override val code: Int,
        override val serviceErrorCode: Int = Int.MIN_VALUE,
        override val message: String,
    ) : APIError(code, serviceErrorCode, message) // 401, 403 errors
}

/**
 * APIFailure -> Network call was successful but API returned an error response.
 * ServerFailure -> 5xx, 4xx errors except UnAuthenticatedError.
 * NotFoundFailure -> 404 errors.
 * InternalServerFailure -> 500 errors.
 */
sealed class APIFailure(
    open val code: Int,
    open val serviceErrorCode: Int = Int.MIN_VALUE,
    open val message: String,
) : NetworkError {
    data class ServerFailure(
        override val code: Int,
        override val serviceErrorCode: Int = Int.MIN_VALUE,
        override val message: String,
    ) : APIFailure(code, serviceErrorCode, message) // 5xx, 4xx errors

    data class NotFoundFailure(
        override val code: Int,
        override val serviceErrorCode: Int = Int.MIN_VALUE,
        override val message: String,
    ) : APIFailure(code, serviceErrorCode, message) // 404 errors

    data class InternalServerFailure(
        override val code: Int,
        override val serviceErrorCode: Int = Int.MIN_VALUE,
        override val message: String,
    ) : APIFailure(code, serviceErrorCode, message) // 500 errors
}

/**
 * DataError -> Serialization error, Missing data, Parsing error, Partial data, etc.
 * BadDataError -> Serialization error, Missing data, Parsing error, Partial data, etc.
 * UnknownError -> For unknown exceptions.
 */
sealed interface DataError : TRPError {
    data object BadDataError : DataError

    data class BadDataErrorWithMessage(val message: String) : DataError

    data class UnknownError(val message: String) : DataError

    data object UserTypeNotFound : DataError

    data class NotFoundError(val message: String) : DataError

    data class InvalidArgumentError(val message: String?) : DataError
}

/**
 * DataFailure -> Data failures.
 * UnknownFailure -> Unknown failures.
 */
sealed interface DataFailure : TRPFailure {
    data class UnknownFailure(val exception: Exception) : DataFailure
}

/**
 * BusinessError -> Business logic errors.
 * ValidationError -> Validation errors.
 * UnknownError -> Unknown errors.
 */
sealed interface BusinessError : TRPError {
    data class ValidationError(val message: String) : BusinessError

    data class UnknownError(val message: String) : BusinessError
}
