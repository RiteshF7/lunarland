package lunar.land.ui.core.ui.extensions

import android.content.Context
import android.content.Intent
import lunar.land.ui.core.model.app.App

fun Context.launchApp(app: App) {
    packageManager.getLaunchIntentForPackage(app.packageName)?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(it)
    }
}

