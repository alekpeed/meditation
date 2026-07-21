package com.meditation.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.meditation.app.ui.MainActivity

/**
 * A Quick Settings tile that opens the app straight to Home (Phase 2). It intentionally opens the
 * app rather than starting a session directly, since starting a foreground service from a
 * background tile tap is restricted on modern Android.
 */
class QuickTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(intent)
        }
    }
}
