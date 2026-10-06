package net.wault.folder

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.ui.graphics.vector.ImageVector

data class FolderIcon(val id: String, val image: ImageVector)

object FolderIcons {

    const val DEFAULT_ID = "folder"

    val all: List<FolderIcon> = listOf(
        FolderIcon(DEFAULT_ID, Icons.Default.Folder),
        FolderIcon("bank", Icons.Default.AccountBalance),
        FolderIcon("work", Icons.Default.Work),
        FolderIcon("home", Icons.Default.Home),
        FolderIcon("shopping", Icons.Default.ShoppingCart),
        FolderIcon("gaming", Icons.Default.SportsEsports),
        FolderIcon("school", Icons.Default.School),
        FolderIcon("family", Icons.Default.FamilyRestroom),
        FolderIcon("cloud", Icons.Default.Cloud),
        FolderIcon("personal", Icons.Default.Favorite)
    )

    fun imageFor(id: String): ImageVector =
        all.firstOrNull { it.id == id }?.image ?: Icons.Default.Folder
}
