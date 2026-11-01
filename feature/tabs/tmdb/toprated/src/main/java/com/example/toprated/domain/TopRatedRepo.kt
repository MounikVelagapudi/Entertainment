package com.example.toprated.domain

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import kotlinx.coroutines.flow.Flow

interface TopRatedRepo {

    fun observeTopRated(): Flow<Resource<TopRatedDomainModel>>

    suspend fun refreshTopRatedApi(page: Int): Result<TopRatedDomainModel, TRPError>

    suspend fun getById(id: Int): TopRatedMovie?
}
