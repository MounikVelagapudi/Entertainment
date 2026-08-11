package com.example.entertainment

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
import com.example.presentation.presentation.HomeScreenContent
import com.example.presentation.presentation.MediaResults
import com.example.entertainment.detailsScreen.MediaDetailScreen
import com.example.entertainment.favourite.FavouriteScreen
import com.example.entertainment.more.MoreScreen
import com.example.entertainment.profile.ProfileScreen
import com.example.presentation.presentation.performance.TrackJank
import com.example.entertainment.ui.theme.EntertainmentTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Exposes Modifier.testTag() values as Android resource-ids in the
            // accessibility tree, so out-of-process drivers (Appium/UiAutomator2)
            // can locate them — testTag alone is only visible to Compose's own
            // in-process test APIs.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTagsAsResourceId = true }
            ) {
                EntertainmentTheme {
                    EntertainmentApp()
                }
            }
        }
    }
}

private const val ROUTE_MAIN = "main"
//private const val ROUTE_SCREEN2 = "screen2"

private const val ARG_MEDIA_ID = "mediaId"
private const val ROUTE_SCREEN2_BASE = "screen2"
private const val ROUTE_SCREEN2 = "$ROUTE_SCREEN2_BASE/{$ARG_MEDIA_ID}"


@PreviewScreenSizes
@Composable
fun EntertainmentApp() {
    val navController = rememberNavController()

    // Attached once at the app root -- covers every screen reachable through
    // navController, not just Home.
    TrackJank()

    NavHost(navController = navController, startDestination = ROUTE_MAIN) {
        composable(ROUTE_MAIN) {
            MainTabsScreen(
                onNavigateToScreen2 = { mediaResult ->
                    // 2. Safely construct the navigation path using an identifier (e.g., id)
                    // If your id can have special characters, wrap it in Uri.encode(mediaResult.id)
                    navController.navigate("$ROUTE_SCREEN2_BASE/${mediaResult.id}")
                }
            )
        }
        composable(
            route = ROUTE_SCREEN2,
            // 3. Declare the argument configuration so the NavHost knows to parse it
            arguments = listOf(
                navArgument(ARG_MEDIA_ID) { type = NavType.StringType }
            )
        ) { backStackEntry ->
            // 4. Extract the argument from the backStackEntry
            val mediaId = backStackEntry.arguments?.getString(ARG_MEDIA_ID).orEmpty()

            // 5. Pass it directly into your Screen2 component
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
                    onNavigateToDetail = onNavigateToScreen2
                )

                AppDestinations.DISCOVER -> FavouriteScreen(
                    modifier = Modifier.padding(
                        innerPadding
                    )
                )

                AppDestinations.SEARCH -> ProfileScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.WATCHLIST -> MoreScreen(modifier = Modifier.padding(innerPadding))
                AppDestinations.PROFILE -> MoreScreen(modifier = Modifier.padding(innerPadding))
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
    SEARCH("Search", R.drawable.ic_person),
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