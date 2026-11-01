package com.example.toprated.domain

import com.example.entertainment.core.domain.Resource
import com.example.entertainment.core.domain.Result
import com.example.entertainment.core.domain.TRPError
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class TopRatedUseCase @Inject constructor(
    private val topRatedRepo: TopRatedRepo,
) {
    /* Always-on read from the offline cache -- Loading/Success/Error is already
       folded in by the repo, just collect this to render the screen. */
    fun observe(): Flow<Resource<TopRatedDomainModel>> = topRatedRepo.observeTopRated()

    /* Syncs the given page over the network to keep the cache fresh -- the returned
       page/totalPages tells the caller whether there's another page to load. */
    suspend fun refresh(page: Int): Result<TopRatedDomainModel, TRPError> =
        topRatedRepo.refreshTopRatedApi(page)

    /* Detail-screen lookup by id, independent of the network. */
    suspend fun getById(id: Int): TopRatedMovie? = topRatedRepo.getById(id)
}
