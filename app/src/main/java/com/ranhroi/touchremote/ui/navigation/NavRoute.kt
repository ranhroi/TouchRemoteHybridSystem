package com.ranhroi.touchremote.ui.navigation

sealed class NavRoute(val route: String) {
    // NavGraph
    object Onboarding : NavRoute("onboarding_screen")
    object Main : NavRoute("main_screen")
    object Setting : NavRoute("settings_screen")
    object SetShortKey : NavRoute("set_shortKey_screen")

    object SetQuickKeys : NavRoute("set_quickkeys_screen?quickKeyId={quickKeyId}") {
        fun createRoute(quickKeyId: Int = 0) = "set_quickkeys_screen?quickKeyId=$quickKeyId"
    }

    object SetServer : NavRoute("set_server_screen")
    object FujitsuRemote : NavRoute("fujitsu_remote_screen")
    object Loading : NavRoute("loading_screen")
}