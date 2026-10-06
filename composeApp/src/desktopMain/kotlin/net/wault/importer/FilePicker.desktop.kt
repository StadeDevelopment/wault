package net.wault.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual suspend fun pickTextFile(): PickedFile? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Choose an export", FileDialog.LOAD).apply {
        setFilenameFilter { _, name -> name.endsWith(".csv", ignoreCase = true) }
        isVisible = true
    }
    val directory = dialog.directory ?: return@withContext null
    val name = dialog.file ?: return@withContext null
    val file = File(directory, name)
    if (!file.isFile) return@withContext null
    PickedFile(name = file.name, text = file.readText())
}
