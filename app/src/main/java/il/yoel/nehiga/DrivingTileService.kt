// נכתב: 2026-10-29 02:10 | עודכן: 2026-10-29 02:35
package il.yoel.nehiga

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** הכפתור בווילון ההתראות: לחיצה אחת מדליקה/מכבה את מצב הנהיגה. */
class DrivingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        DrivingMode.set(this, !Prefs.isActive(this), DrivingMode.SRC_TILE)
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        val on = Prefs.isActive(this)
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (on) "פעיל" else "כבוי"
        tile.updateTile()
    }
}
