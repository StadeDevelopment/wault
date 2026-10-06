package net.wault.importer

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import net.wault.MainActivity
import net.wault.requireAppContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

actual suspend fun pickTextFile(): PickedFile? {
    val activity = MainActivity.currentActivity ?: return null

    val uri: android.net.Uri = suspendCancellableCoroutine<android.net.Uri?> { continuation ->
        val launcher = activity.activityResultRegistry.register(
            "wault-import-${System.currentTimeMillis()}",
            ActivityResultContracts.OpenDocument()
        ) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { launcher.unregister() }
        launcher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
    } ?: return null

    val context = requireAppContext()
    val text = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull() ?: return null

    val name = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "export.csv"

    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    return PickedFile(name = name, text = text)
}
