package com.example.entertainment.core.domain

/**
 * Mapper interface to map the input to output
 */

interface Mapper<in I, out O> {
    suspend fun map(input: I): O
}
