package app.tasca

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import app.tasca.ui.Settings
import app.tasca.ui.get
import app.tasca.ui.theme.WidgetColors
import app.tasca.ui.theme.widgetColors

/** The app's colours for widgets, with the phone in light and in dark mode. */
internal class WidgetPaint(private val light: WidgetColors, private val dark: WidgetColors, private val nowDark: Boolean) {
    /**
     * Calls the colour setter [method] on view [id] with the colour [pick] chooses. From Android 12 the launcher
     * gets both versions and switches with the phone; before, it keeps the one for the mode the phone is in now.
     */
    fun RemoteViews.paint(id: Int, method: String, pick: (WidgetColors) -> Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setColorInt(id, method, pick(light), pick(dark))
        else setInt(id, method, pick(if (nowDark) dark else light))
    }

    fun RemoteViews.text(id: Int, pick: (WidgetColors) -> Int) = paint(id, "setTextColor", pick)

    /** Recolours the white shape of an image to [pick]'s colour, keeping its shape and transparency. */
    fun RemoteViews.tint(id: Int, pick: (WidgetColors) -> Int) = paint(id, "setColorFilter", pick)

    companion object {
        suspend fun of(context: Context): WidgetPaint {
            val (light, dark) = widgetColors(context, Settings.get(context).current())
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return WidgetPaint(light, dark, night)
        }
    }
}
