package com.example.presentation.presentation.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.popular.domain.GetPopularMovieUseCase
import com.example.toprated.domain.TopRatedUseCase
import com.example.trending.domain.TrendingUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

/**
 * Keeps Room warm while the app isn't even open, so it isn't showing hours-old
 * data the moment the user comes back. Scheduled/cancelled via
 * [com.example.presentation.presentation.HomeViewModel.startHomeSync]/[com.example.presentation.presentation.HomeViewModel.cancelHomeSync],
 * both driven by a `DisposableEffect` in `HomeScreenContent` -- this has nothing
 * to do with HomeViewModel's own onScreenEntered() staleness check, which only
 * kicks in while the app is actually foregrounded. The two don't need to know
 * about each other; both just end up calling the same use cases, which persist
 * to the same Room tables the UI already observes.
 */
@HiltWorker
class HomeSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val trendingUseCase: TrendingUseCase,
    private val popularMovieUseCase: GetPopularMovieUseCase,
    private val topRatedUseCase: TopRatedUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = supervisorScope {
        /* Same shape as HomeViewModel.loadAll() -- one section failing shouldn't
           stop the others from refreshing. */
        val trending = async { runCatching { trendingUseCase.refresh() } }
        val popular = async { runCatching { popularMovieUseCase.refresh(page = FIRST_PAGE) } }
        val topRated = async { runCatching { topRatedUseCase.refresh(page = FIRST_PAGE) } }

        val results = listOf(trending, popular, topRated).map { it.await() }
        val anyFailed = results.any { it.isFailure }

        /* WorkManager's own backoff policy handles the retry timing -- we just
           say whether this attempt was good enough. */
        if (anyFailed) Result.retry() else Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "home_sync"
        private const val FIRST_PAGE = 1
    }
}
