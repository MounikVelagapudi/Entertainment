package com.example.entertainment.core.data.networking

import android.util.Log
import com.example.entertainment.core.data.networking.safeCall
import com.example.entertainment.core.domain.APIError
import com.example.entertainment.core.domain.APIFailure
import com.example.entertainment.core.domain.Mapper
import com.example.entertainment.core.domain.NetworkError
import com.example.entertainment.core.domain.NetworkFailure
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.RootNetworkError
import com.example.entertainment.core.domain.TRPError
import com.example.entertainment.core.domain.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.IOException
import retrofit2.Response
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Safe call to the network
 *
 * If the response is successful, then it is a success response
 * If the response is not successful, then it is an error response
 * Uses a mapper to map the response to the required type
 *
 * */

suspend inline fun <reified T, R> mappedApiCall(
    networkDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mapper: Mapper<T, R>,
    crossinline execute: suspend () -> Response<T>
): Result<R, TRPError> {
    return safeCall(networkDispatcher) {
        execute.invoke()
    }.map {
        mapper.map(it)
    }
}

suspend inline fun <reified T, R> mappedSDKCall(
    networkDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mapper: Mapper<T, R>,
    crossinline execute: suspend () -> Result<T, TRPError>
): Result<R, TRPError> {
    return withContext(networkDispatcher) {
        execute.invoke().map {
            mapper.map(it)
        }
    }
}

/**
 * Maps a throwable to the appropriate TRP Result type.
 *
 * This is the single source of truth for exception-to-result mapping,
 * ensuring consistent error handling across all network call wrappers.
 * Used by both standard Retrofit calls and Sandwich API calls.
 *
 * @param throwable The throwable to map
 * @return A [Result.Error] or [Result.Failure] with the appropriate error type
 */
fun <T> mapExceptionToResult(throwable: Throwable): Result<T, RootNetworkError> {
    return when (throwable) {
        is UnknownHostException -> Result.Error(NetworkError.NoInternetError)
        is SSLHandshakeException, is ConnectException -> Result.Error(NetworkError.ConnectionError)
        is SocketTimeoutException, is InterruptedIOException -> Result.Error(NetworkError.TimeoutError)
        is SerializationException, is EOFException -> Result.Failure(NetworkFailure.MalformedJsonFailure)
        is IOException -> Result.Failure(NetworkFailure.IOFailure)
        else -> Result.Failure(NetworkFailure.UnknownFailure)
    }
}

/**
 * Safe call to the network
 *
 * If the response is successful, then it is a success response
 * If the response is not successful, then it is an error response
 *
 * */

suspend inline fun <reified T> safeCall(
    networkDispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline execute: suspend () -> Response<T>
): Result<T, RootNetworkError> {
    val response = try {
        withContext(networkDispatcher) {
            execute.invoke()
        }
    } catch (exception: Exception) {
        Log.d("SafeNetwork", "Exception", exception)
        return if (exception is CancellationException) {
            throw exception
        } else {
            mapExceptionToResult(exception)
        }
    }
    return responseToResult<T>(response)
}

/**
 * Convert the response to Result
 *
 * If the response code is in 200..299, then it is a success response
 * If the response code is 401 or 403, then it is an unauthenticated error
 * If the response code is 404, then it is a not found error
 * If the response code is 500, then it is an internal server error
 * Else it is a unknown error
 *
 * */

inline fun <reified T> responseToResult(response: Response<T>): Result<T, RootNetworkError> {
    if (response.code() in 200..299) {
        return response.body()?.let { body ->
            Result.Success(body as T)
        } ?: run {
            Result.Error(NetworkError.EmptyResponseError)
        }
    } else {
        parseErrorResponse(response)?.let { serverError ->
            runCatching {
                when (response.code()) {
                    401, 403 -> {
                        return Result.Error(
                            APIError.UnAuthenticatedError(
                                code = serverError.httpStatusCode,
                                serviceErrorCode = serverError.serviceErrorCode,
                                message = serverError.message
                            )
                        )
                    }

                    404 -> {
                        return Result.Failure(
                            APIFailure.NotFoundFailure(
                                code = serverError.httpStatusCode,
                                serviceErrorCode = serverError.serviceErrorCode,
                                message = serverError.message
                            )
                        )
                    }

                    500 -> {
                        return Result.Failure(
                            APIFailure.InternalServerFailure(
                                code = serverError.httpStatusCode,
                                serviceErrorCode = serverError.serviceErrorCode,
                                message = serverError.message
                            )
                        )
                    }

                    else -> {
                        return Result.Failure(
                            APIFailure.ServerFailure(
                                code = serverError.httpStatusCode,
                                serviceErrorCode = serverError.serviceErrorCode,
                                message = serverError.message
                            )
                        )
                    }
                }
            }.getOrElse {
                return Result.Failure(NetworkFailure.UnknownFailure)
            }
        } ?: run {
            return Result.Failure(NetworkFailure.EmptyResponseFailure)
        }
    }
}

/**
 *
 * Parse the error response from the server
 * Sample error response: {"serviceErrorCode":75001,"httpStatusCode":401,"message":"Not Authenticated"}
 *
 * */

@PublishedApi
internal val errorResponseJson = Json { ignoreUnknownKeys = true }

inline fun <reified T> parseErrorResponse(response: Response<T>): ErrorResponse? {
    return response.errorBody()?.let { errorBody ->
        val errorResponse = errorBody.string()
        try {
            errorResponseJson.decodeFromString<ErrorResponse>(errorResponse)
        } catch (exception: SerializationException) {
            ErrorResponse(
                serviceErrorCode = response.code(),
                httpStatusCode = response.code(),
                message = "Error parsing error response: $errorResponse Exception: ${exception.message}"
            )
        }
    }
}

/**
 * Safe call to the network without mapping
 *
 * If the response is successful, then it is a success response
 * If the response is not successful, then it is an error response
 */
suspend inline fun <reified T> apiCall(
    networkDispatcher: CoroutineDispatcher = Dispatchers.IO,
    crossinline execute: suspend () -> Response<T>
): Result<T, TRPError> {
    return when (val result = safeCall(networkDispatcher, execute)) {
        is Result.Success -> Result.Success(result.data)
        is Result.Error -> Result.Failure(result.error)
        is Result.Failure -> Result.Failure(result.failure)
    }
}
