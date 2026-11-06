package com.example.entertainment

import HomeScreenContent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.entertainment.core.presentation.reusable_components.MediaResults
import com.example.entertainment.detailsScreen.MediaDetailScreen
import com.example.entertainment.favourite.DiscoverScreen
import com.example.entertainment.pos.presentation.PosScreen
import com.example.entertainment.more.WatchlistScreen
import com.example.entertainment.profile.ProfileScreen
import com.example.entertainment.push.PushNotifications
import com.example.entertainment.push.RequestNotificationPermission
import com.example.presentation.presentation.performance.TrackJank
import com.example.entertainment.ui.theme.EntertainmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /* mediaId from a tapped notification, cleared once we've navigated. */
    private var pushMediaId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        /* Cold start from a tap. Skip on recreation or rotation re-opens the detail screen. */
        if (savedInstanceState == null) {
            pushMediaId = intent.getStringExtra(PushNotifications.EXTRA_MEDIA_ID)
        }
        enableEdgeToEdge()
        setContent {
            RequestNotificationPermission()
            /* Exposes testTag() as an Android resource-id so out-of-process drivers
               (Appium/UiAutomator2) can find it -- testTag alone is Compose-internal. */
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true }
            ) {
                EntertainmentTheme {
                    EntertainmentApp(
                        pushMediaId = pushMediaId,
                        onPushHandled = { pushMediaId = null },
                    )
                }
            }
        }
    }

    /* Warm tap -- activity already alive, SINGLE_TOP routes the intent here instead of onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(PushNotifications.EXTRA_MEDIA_ID)?.let { pushMediaId = it }
    }
}

private const val ROUTE_MAIN = "main"

private const val ARG_MEDIA_ID = "mediaId"
private const val ROUTE_SCREEN2_BASE = "screen2"
private const val ROUTE_SCREEN2 = "$ROUTE_SCREEN2_BASE/{$ARG_MEDIA_ID}"


@PreviewScreenSizes
@Composable
fun EntertainmentApp(
    pushMediaId: String? = null,
    onPushHandled: () -> Unit = {},
) {
    val navController = rememberNavController()

    /* Attached once at the app root -- covers every screen, not just Home. */
    TrackJank()

    /* Detail screen parses it as Int, so drop anything that isn't one. */
    LaunchedEffect(pushMediaId) {
        val id = pushMediaId ?: return@LaunchedEffect
        if (id.toIntOrNull() != null) navController.navigate("$ROUTE_SCREEN2_BASE/$id")
        onPushHandled()
    }

    NavHost(navController = navController, startDestination = ROUTE_MAIN) {
        composable(ROUTE_MAIN) {
            MainTabsScreen(
                onNavigateToScreen2 = { mediaResult ->
                    /* If mediaResult.id can contain special characters, wrap it in Uri.encode(...). */
                    navController.navigate("$ROUTE_SCREEN2_BASE/${mediaResult.id}")
                }
            )
        }
        composable(
            route = ROUTE_SCREEN2,
            arguments = listOf(
                navArgument(ARG_MEDIA_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString(ARG_MEDIA_ID).orEmpty()

            MediaDetailScreen(
                mediaId = Integer.valueOf(mediaId),
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

@Composable
fun MainTabsScreen(onNavigateToScreen2: (MediaResults) -> Unit) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreenContent(
                    modifier = Modifier.padding(innerPadding),
                    onNavigateToDetailScreen = onNavigateToScreen2
                )

                AppDestinations.DISCOVER -> DiscoverScreen(
                    modifier = Modifier.padding(
                        innerPadding
                    )
                )

                AppDestinations.POS -> PosScreen(modifier = Modifier.padding(innerPadding))

                AppDestinations.WATCHLIST -> WatchlistScreen(
                    modifier = Modifier.padding(
                        innerPadding
                    )
                )

                AppDestinations.PROFILE -> ProfileScreen(modifier = Modifier.padding(innerPadding))
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    DISCOVER("Discover", R.drawable.ic_favorite),
    POS("POS", R.drawable.ic_account_box),
    WATCHLIST("WatchList", icon = R.drawable.ic_bookmark),
    PROFILE("Profile", icon = R.drawable.ic_person),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EntertainmentTheme {
        Greeting("Android")
    }
}