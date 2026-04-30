package com.inknironapps.bagger.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.inknironapps.bagger.ui.screens.BagsScreen
import com.inknironapps.bagger.ui.screens.DiscoverScreen
import com.inknironapps.bagger.ui.screens.MoreScreen
import com.inknironapps.bagger.ui.screens.ShelfScreen

@Composable
fun BaggerNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Destination.Shelf.route) {
        composable(Destination.Shelf.route)    { ShelfScreen() }
        composable(Destination.Bags.route)     { BagsScreen() }
        composable(Destination.Discover.route) { DiscoverScreen() }
        composable(Destination.More.route)     { MoreScreen() }
    }
}
