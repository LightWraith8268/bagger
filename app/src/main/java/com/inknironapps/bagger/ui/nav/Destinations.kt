package com.inknironapps.bagger.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Shelf    : Destination("shelf",    "Shelf",    Icons.Filled.Inventory2)
    data object Bags     : Destination("bags",     "Bags",     Icons.Filled.Backpack)
    data object Discover : Destination("discover", "Discover", Icons.Filled.Search)
    data object More     : Destination("more",     "More",     Icons.Filled.MoreHoriz)
}

object DetailRoutes {
    const val OwnedDetail = "owned/{ownedId}"
    fun ownedDetail(id: String) = "owned/$id"
    const val CatalogDetail = "catalog/{discId}"
    fun catalogDetail(id: String) = "catalog/$id"
    const val BagDetail = "bag/{bagId}"
    fun bagDetail(id: String) = "bag/$id"
    const val AddDisc = "add_disc"
}

val BottomDestinations: List<Destination> = listOf(
    Destination.Shelf, Destination.Bags, Destination.Discover, Destination.More
)
