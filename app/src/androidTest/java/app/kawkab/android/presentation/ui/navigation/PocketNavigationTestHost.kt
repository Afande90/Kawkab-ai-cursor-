package app.kawkab.android.presentation.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.kawkab.android.presentation.state.ChatEntryRequestRelay
import app.kawkab.android.presentation.state.TransientMessageRelay
import app.kawkab.design.device.DeviceKey
import app.kawkab.design.device.DevicePage
import app.kawkab.design.device.DeviceState
import app.kawkab.design.device.KawkabDevice
import app.kawkab.design.theme.KnotworkTheme

/** Real device keys and shell over a minimal graph with production route ownership. */
@Composable
internal fun PocketNavigationTestHost(nav: NavHostController, relay: ChatEntryRequestRelay, splash: Boolean = false) {
    KnotworkTheme {
        AppShellScaffold(nav, remember { TransientMessageRelay() }, relay) {
            NavHost(nav, startDestination = if (splash) NavRoutes.SPLASH else NavRoutes.CHAT_TAB) {
                composable(NavRoutes.SPLASH) {
                    LaunchedEffect(Unit) {
                        nav.navigate(NavRoutes.CHAT_TAB) {
                            popUpTo(NavRoutes.SPLASH) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                composable(NavRoutes.CHAT_TAB) {
                    var page by remember { mutableStateOf(DevicePage.IDLE) }
                    var settings by remember { mutableStateOf(false) }
                    LaunchedEffect(relay) {
                        relay.pocketPages.collect {
                            settings = false
                            page = it
                        }
                    }
                    KawkabDevice(
                        state = DeviceState(page = page),
                        onHome = {
                            settings = false
                            page = DevicePage.IDLE
                        },
                        onShots = {
                            settings = false
                            page = DevicePage.SHOTS
                        },
                        onGrowth = {
                            settings = false
                            page = DevicePage.GROWTH
                        },
                        onSettings = { settings = true },
                        lcdContent = {
                            if (settings) {
                                DeviceKey("ADVANCED", { nav.navigateToTab(NavRoutes.MORE) })
                            } else {
                                Text(
                                    when (page) {
                                        DevicePage.SHOTS -> "shots-body"
                                        DevicePage.GROWTH -> "growth-body"
                                        else -> "chat-home-body"
                                    },
                                )
                            }
                        },
                    )
                }
                composable(NavRoutes.MORE) {
                    Column {
                        Text("more-body")
                        DeviceKey("PIPELINES", { nav.navigateToTab(NavRoutes.PIPELINES_GRAPH) })
                        DeviceKey("TOOLS", { nav.navigateToTab(NavRoutes.TOOLS) })
                        DeviceKey("ENGINE SETTINGS", { nav.navigate(NavRoutes.SETTINGS) })
                    }
                }
                composable(NavRoutes.TOOLS) { Text("tools-body") }
                composable(NavRoutes.ALLOWED_DOMAINS) { Text("allowed-domains-body") }
                navigation(startDestination = NavRoutes.PIPELINE_LIBRARY, route = NavRoutes.PIPELINES_GRAPH) {
                    composable(NavRoutes.PIPELINE_LIBRARY) { Text("pipeline-library-body") }
                }
                navigation(startDestination = NavRoutes.SETTINGS_HUB, route = NavRoutes.SETTINGS) {
                    composable(NavRoutes.SETTINGS_HUB) { Text("settings-hub-body") }
                    composable(NavRoutes.SETTINGS_TOOLS) { Text("settings-tools-body") }
                    composable(NavRoutes.SETTINGS_TOOLS_MANAGE) { Text("settings-tools-manage-body") }
                }
            }
        }
    }
}
