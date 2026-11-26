package net.obscura.vpnclientapp.ui

import android.content.Context
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

    suspend fun query(context: Context, ownPackage: String): List<Item> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                .asSequence()
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
