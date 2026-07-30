package com.example.toprated.domain

import com.example.entertainment.core.domain.TRPError
import javax.inject.Inject

class TopRatedUseCase @Inject constructor(
    private val topRatedRepo: TopRatedRepo,
) {
    suspend operator fun invoke(): Result<TopRatedDomainModel, TRPError> {
        return topRatedRepo.getTopRatedMovies()
    }
}