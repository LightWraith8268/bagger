package com.inknironapps.bagger.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.inknironapps.bagger.ui.screens.MoreScreen
import com.inknironapps.bagger.ui.screens.add_disc.AddDiscRoute
import com.inknironapps.bagger.ui.screens.bags.BagDetailScreen
import com.inknironapps.bagger.ui.screens.bags.BagsScreen
import com.inknironapps.bagger.ui.screens.disc_detail.CatalogDiscDetailScreen
import com.inknironapps.bagger.ui.screens.disc_detail.OwnedDiscDetailScreen
import com.inknironapps.bagger.ui.screens.discover.DiscoverScreen
import com.inknironapps.bagger.ui.screens.shelf.ShelfScreen

@Composable
fun BaggerNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Destination.Shelf.route) {
        composable(Destination.Shelf.route) {
            ShelfScreen(
                onAddDisc = { navController.navigate(DetailRoutes.AddDisc) },
                onDiscClick = { id -> navController.navigate(DetailRoutes.ownedDetail(id)) }
            )
        }
        composable(DetailRoutes.AddDisc) {
            AddDiscRoute(onDone = { navController.popBackStack() })
        }
        composable(Destination.Bags.route) {
            BagsScreen(onBagClick = { id -> navController.navigate(DetailRoutes.bagDetail(id)) })
        }
        composable(Destination.Discover.route) {
            DiscoverScreen(onDiscClick = { id -> navController.navigate(DetailRoutes.catalogDetail(id)) })
        }
        composable(Destination.More.route) { MoreScreen() }

        composable(
            DetailRoutes.OwnedDetail,
            arguments = listOf(navArgument("ownedId") { type = NavType.StringType })
        ) {
            OwnedDiscDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            DetailRoutes.CatalogDetail,
            arguments = listOf(navArgument("discId") { type = NavType.StringType })
        ) {
            CatalogDiscDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            DetailRoutes.BagDetail,
            arguments = listOf(navArgument("bagId") { type = NavType.StringType })
        ) {
            BagDetailScreen(
                onBack = { navController.popBackStack() },
                onDiscClick = { id -> navController.navigate(DetailRoutes.ownedDetail(id)) }
            )
        }
    }
}
