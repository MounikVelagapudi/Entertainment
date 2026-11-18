package com.example.entertainment.core.data.networking

import kotlinx.serialization.Serializable

/**
 * Error response from the network
 * @param httpStatusCode - HTTP status code
 * @param message - Error message
 * @param serviceErrorCode - Service error code
 * Sample error response:
 *              {
 *              "serviceErrorCode":75001,
 *              "httpStatusCode":401,
 *              "message":"Not Authenticated"
 *              }
 */

@Serializable
data class ErrorResponse(
    val httpStatusCode: Int,
    val message: String,
    val serviceErrorCode: Int
)
