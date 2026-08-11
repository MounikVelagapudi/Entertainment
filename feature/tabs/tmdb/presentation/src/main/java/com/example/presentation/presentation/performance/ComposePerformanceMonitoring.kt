package com.example.presentation.presentation.performance

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.presentation.BuildConfig

/**
 * Runtime tools for watching Compose recomposition/composition cost at
 * DEBUG time. None of this is a substitute for the build-time Compose
 * Compiler Metrics wired up in app/build.gradle.kts (composeCompiler {}) --
 * metrics tell you whether a composable *can* skip; these tools tell you
 * whether it's *actually* being skipped while the app runs.
 *
 * Strip/gate calls to these before shipping a release build; they're a
 * debugging aid, not production instrumentation (see the "> 16ms" caveat
 * on [ComposePerformanceMonitor.Measured] below).
 */

private const val TAG = "ComposePerf"

/**
 * Drop this inside any composable to log every time ITS CALL SITE recomposes.
 *
 * Why [SideEffect] and not just a log line in the composable body: the body
 * can run and then be discarded if Compose decides not to commit the result
 * (e.g. it started a recomposition that got superseded). SideEffect only
 * runs after a composition is actually committed, so the count you see here
 * matches recompositions that really happened -- not attempts.
 *
 * Usage: call `RecompositionCounter("movie_card_${result.id}")` at the top of
 * MediaMovieItem, run the app, scroll the list, and watch Logcat filtered on
 * "ComposePerf". If a card recomposes every time you scroll past it (instead
 * of only once per data change), something upstream is unstable -- that's
 * your cue to go check compose_metrics's classes.txt file for the "unstable"
 * verdict on whatever type you're passing in.
 */
@Composable
fun RecompositionCounter(tag: String) {
    val recompositionCount = remember { mutableStateOf(0) }
    SideEffect {
        recompositionCount.value++
        Log.d(TAG, "$tag recomposed ${recompositionCount.value} time(s)")
    }
}

/**
 * Wraps a chunk of UI and times how long its composition takes.
 *
 * Important honesty check: this measures composition (running the
 * @Composable function bodies), NOT the full frame. Layout and draw happen
 * afterwards and aren't included, so a "fast" measurement here doesn't
 * guarantee a smooth frame -- for that, use Android Studio's Layout
 * Inspector ("Show Recomposition Counts") or a systrace/Perfetto capture.
 * What this DOES catch well: "is my composition doing expensive work
 * (allocations, formatting, sorting a list) that it shouldn't be doing on
 * every recomposition."
 *
 * 16ms is the rough per-frame budget at 60Hz (1000ms / 60 ~= 16.6ms) --
 * composition sharing that whole budget with layout/draw/the rest of the
 * app is a warning sign, not a hard failure.
 */
class ComposePerformanceMonitor {

    @Composable
    fun <T> Measured(key: String, content: @Composable () -> T): T {
        // Release builds skip the timestamp/SideEffect/Log work entirely --
        // just run the content and return, so this monitor costs nothing
        // once BuildConfig.DEBUG is false.
        if (!BuildConfig.DEBUG) {
            return content()
        }

        // Deliberately NOT `remember`-ed: we want a fresh timestamp on every
        // single composition/recomposition pass, not one frozen at first
        // composition (remember would make every later "duration" measure
        // time-since-first-composed instead of time-for-this-pass).
        val startTimeNanos = System.nanoTime()

        val result = content()

        SideEffect {
            val durationMillis = (System.nanoTime() - startTimeNanos) / 1_000_000
            if (durationMillis > 16) {
                Log.w(TAG, "'$key' composition took ${durationMillis}ms (> one 60Hz frame)")
            } else {
                Log.d(TAG, "'$key' composition took ${durationMillis}ms")
            }
        }

        return result
    }
}
