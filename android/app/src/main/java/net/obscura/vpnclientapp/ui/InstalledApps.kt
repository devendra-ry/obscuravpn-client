package net.obscura.vpnclientapp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object InstalledApps {
    data class Item(
        val packageName: String,
        val label: String,
        val isSystem: Boolean,
    )

    /**
     * The apps with a launcher entry. This is exactly what the manifest's `<queries>` launcher intent makes visible on
     * Android 11+, so the list does not depend on QUERY_ALL_PACKAGES.
     */
    suspend fun query(context: Context, ownPackage: String): List<Item> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
                .asSequence()
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != ownPackage }
                .map { info ->
                    val isSystem =
                        (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
                    val label = info.loadLabel(pm).toString().ifBlank { info.packageName }
                    Item(info.packageName, label, isSystem)
                }
                .sortedWith(compareBy<Item> { it.label.lowercase() }.thenBy { it.packageName })
                .toList()
        }
}
