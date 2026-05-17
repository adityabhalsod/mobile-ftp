package com.mobileftp.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.mobileftp.util.PermissionUtils
import java.io.File

/**
 * Utilities for resolving a Storage Access Framework tree URI to a real
 * filesystem path that Apache FtpServer (which uses java.io.File) can serve.
 *
 * SAF gives us URIs like:
 *   content://com.android.externalstorage.documents/tree/primary%3ADocuments
 * We translate the documentId (e.g. "primary:Documents") into a real path on
 * shared external storage (e.g. /storage/emulated/0/Documents).
 *
 * If translation fails (e.g. for non-primary volumes that aren't reachable as
 * regular files), the caller should fall back to the app's external files dir.
 */
object StorageUtils {

    /**
     * Translate a tree URI from ACTION_OPEN_DOCUMENT_TREE to an absolute path on
     * the shared external storage volume. Returns null if the URI doesn't refer
     * to a path the app can read as a plain file.
     */
    fun pathFromTreeUri(uri: Uri): String? {
        if (!"com.android.externalstorage.documents".equals(uri.authority, ignoreCase = true)) {
            return null
        }
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        val parts = docId.split(":", limit = 2)
        val type = parts.getOrNull(0).orEmpty()
        val rel = parts.getOrNull(1).orEmpty()

        // "primary" volume = the shared internal storage path
        if ("primary".equals(type, ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory().absolutePath
            return if (rel.isBlank()) base else "$base/$rel"
        }

        // "home" volume on some OEMs is also rooted at shared external storage
        if ("home".equals(type, ignoreCase = true)) {
            val base = Environment.getExternalStorageDirectory().absolutePath
            return if (rel.isBlank()) base else "$base/$rel"
        }

        // Removable / secondary volumes — typically not directly file-accessible.
        // Return null so the caller can fall back to the app's external dir.
        return null
    }

    /** Default app-managed FTP root inside getExternalFilesDir(). Always writable. */
    fun appPrivateRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "ftp_root").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Phone's "Internal storage" root (/storage/emulated/0/). This is what users
     * mean by "my files" — DCIM, Downloads, Documents, etc.
     * Only readable when MANAGE_EXTERNAL_STORAGE is granted on Android 11+.
     */
    fun sharedStorageRoot(): File = Environment.getExternalStorageDirectory()

    /**
     * Resolve the best default FTP root for the device, preferring shared
     * storage when All Files Access has been granted, otherwise the app's
     * private external dir (always available).
     */
    fun bestDefaultRoot(context: Context): File =
        if (PermissionUtils.hasManageStorage()) sharedStorageRoot()
        else appPrivateRoot(context)
}
