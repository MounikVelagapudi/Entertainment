package com.example.presentation.presentation.performance

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.metrics.performance.JankStats
import androidx.metrics.performance.PerformanceMetricsState
import com.example.presentation.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance

/* checks real frame timings against the device's actual refresh rate, instead of the
   hardcoded 16ms guess we used in ComposePerformanceMonitoring.kt. call this once near
   the root of the activity (see MainActivity) so it covers every screen.

   debug-only for now, same reasoning as the rest of this file - this is here so we can
   learn/poke at it locally, not a real prod pipeline yet. eventually you'd leave this
   running in release too, but forward frameData somewhere real instead of just Log.w. */
@Composable
fun TrackJank() {
    if (!BuildConfig.DEBUG) return

    val view = LocalView.current
    val context = LocalContext.current

    DisposableEffect(view) {
        val activity = context as? Activity
        val window = activity?.window

        if (window == null) {
            /* nothing to attach to, e.g. a non-Activity preview host - just no-op it */
            onDispose {}
        } else {
            val jankStats = JankStats.createAndTrack(window) { frameData ->
                /* whichever screen's TrackScreenPerformanceTrace() is currently active
                   gets these numbers - this is what turns a raw JankStats callback into
                   a per-screen firebase metric */
                val activeTrace = ActiveScreenTrace.trace
                activeTrace?.incrementMetric("frame_count", 1)

                if (frameData.isJank) {
                    activeTrace?.incrementMetric("jank_frame_count", 1)

                    /* isJank already accounts for the device's real refresh rate,
                       we're not comparing against a hardcoded 16ms here */
                    val durationMs = frameData.frameDurationUiNanos / 1_000_000
                    activeTrace?.incrementMetric("jank_frame_duration_ms", durationMs)
                    Log.w("JankStats", "Dropped frame: ${durationMs}ms UI work — ${frameData.states}")
                }
            }
            onDispose { jankStats.isTrackingEnabled = false }
        }
    }
}

/* whichever screen is currently on screen parks its Trace here, so the jank listener
   in TrackJank() above knows which one to charge a frame's metrics to. only one screen
   is visible at a time in this app, so a single slot is enough - a multi-pane setup
   would need a map instead. */
private object ActiveScreenTrace {
    var trace: Trace? = null
}

/* starts a firebase trace when this screen shows up, stops (and uploads) it when it
   goes away - one trace per visit, not per frame. firebase's dashboard expects named
   traces with metrics attached to them, not a stream of per-frame events, so that's
   the shape we're matching here.

   while this is active, TrackJank()'s frame listener bumps frame_count, jank_frame_count
   and jank_frame_duration_ms on it. jank_frame_count / frame_count in the console is
   basically your jank rate for this screen.

   debug gated like everything else in this file - drop that (and probably the Log.w in
   TrackJank too) once you actually want this reporting on real users. */
@Composable
fun TrackScreenPerformanceTrace(screenName: String) {
    if (!BuildConfig.DEBUG) return

    DisposableEffect(screenName) {
        val trace = Firebase.performance.newTrace(screenName.toPerfTraceName())
        trace.start()
        ActiveScreenTrace.trace = trace

        onDispose {
            ActiveScreenTrace.trace = null
            trace.stop()
        }
    }
}

/* firebase trace names have their own rules (no leading/trailing whitespace,
   100 char max, a few reserved prefixes to avoid) - normalizing here means call
   sites can just pass something readable like "Home" */
private fun String.toPerfTraceName(): String =
    "screen_" + trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

/* tags frames rendered while this is in composition with a (key, value) pair, e.g.
   TrackJankState("screen", "Home"). shows up in frameData.states, so a dropped-frame
   log tells you which screen it happened on instead of just "some frame was slow". */
@Composable
fun TrackJankState(key: String, value: String) {
    if (!BuildConfig.DEBUG) return

    val view = LocalView.current

    DisposableEffect(view, key, value) {
        val metricsState = PerformanceMetricsState.getHolderForHierarchy(view).state
        metricsState?.putState(key, value)
        onDispose { metricsState?.removeState(key) }
    }
}
