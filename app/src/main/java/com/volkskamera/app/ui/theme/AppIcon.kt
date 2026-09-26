package com.volkskamera.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.volkskamera.app.R

/** Die wählbaren App-Symbole (Art déco). Jedes gehört zu einem activity-alias im Manifest. */
enum class AppIcon(val alias: String, val label: String, val hint: String, val preview: Int) {
    AUFGANG("IconA", "Aufgang", "Filmkamera vor Sonnenstrahlen", R.drawable.icon_bg_a),
    MONOGRAMM("IconB", "Monogramm", "Gestuftes V im Achteck", R.drawable.icon_bg_b),
    KRONE("IconC", "Krone", "Objektiv unter Chrysler-Bögen", R.drawable.icon_bg_c),
    ROSETTE("IconD", "Rosette", "Filmspule mit Filmstreifen", R.drawable.icon_bg_d);

    companion object {
        private fun cn(c: Context, i: AppIcon) = ComponentName(c.packageName, "${c.packageName}.${i.alias}")

        fun current(c: Context): AppIcon = entries.firstOrNull {
            when (c.packageManager.getComponentEnabledSetting(cn(c, it))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> it == MONOGRAMM   // Vorgabe im Manifest
                else -> false
            }
        } ?: MONOGRAMM

        /** Neues Symbol aktivieren, die anderen abschalten. Der Launcher übernimmt es nach kurzer Zeit. */
        fun set(c: Context, icon: AppIcon) {
            val pm = c.packageManager
            pm.setComponentEnabledSetting(cn(c, icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(cn(c, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
