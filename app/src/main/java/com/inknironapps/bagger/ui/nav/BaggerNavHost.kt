package com.inknironapps.bagger.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.inknironapps.bagger.ui.screens.add_disc.AddDiscRoute
import com.inknironapps.bagger.ui.screens.bags.BagDetailScreen
import com.inknironapps.bagger.ui.screens.bags.BagsScreen
import com.inknironapps.bagger.ui.screens.comparison.ComparisonScreen
import com.inknironapps.bagger.ui.screens.disc_detail.CatalogDiscDetailScreen
import com.inknironapps.bagger.ui.screens.disc_detail.OwnedDiscDetailScreen
import com.inknironapps.bagger.ui.screens.discover.DiscoverScreen
import com.inknironapps.bagger.ui.screens.lost_map.LostMapScreen
import com.inknironapps.bagger.ui.screens.more.MoreScreen
import com.inknironapps.bagger.ui.screens.settings.SettingsScreen
import com.inknironapps.bagger.ui.screens.shelf.ShelfScreen
import com.inknironapps.bagger.ui.screens.stats.StatsScreen
import com.inknironapps.bagger.ui.screens.wishlist.WishlistScreen

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
        composable(Destination.More.route) {
            MoreScreen(
                onWishlist = { navController.navigate(DetailRoutes.Wishlist) },
                onLostMap = { navController.navigate(DetailRoutes.LostMap) },
                onCompare = { navController.navigate(DetailRoutes.Compare) },
                onStats = { navController.navigate(DetailRoutes.Stats) },
                onSettings = { navController.navigate(DetailRoutes.Settings) }
            )
        }

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
        composable(DetailRoutes.Wishlist) {
            WishlistScreen(onBack = { navController.popBackStack() })
        }
        composable(DetailRoutes.LostMap) {
            LostMapScreen(onBack = { navController.popBackStack() })
        }
        composable(DetailRoutes.Compare) {
            ComparisonScreen(onBack = { navController.popBackStack() })
        }
        composable(DetailRoutes.Stats) {
            StatsScreen(onBack = { navController.popBackStack() })
        }
        composable(DetailRoutes.Settings) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
