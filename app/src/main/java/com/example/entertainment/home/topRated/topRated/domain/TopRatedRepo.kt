package com.example.entertainment.home.topRated.topRated.domain

import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError

interface TopRatedRepo {
    suspend fun getTopRatedMovies(): Result<TopRatedDomainModel, TRPError>
}