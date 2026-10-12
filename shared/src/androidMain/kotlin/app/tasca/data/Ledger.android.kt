package app.tasca.data

import android.content.Context
import androidx.room.Room

@Volatile private var instance: Ledger? = null

/** The app's one ledger, in the tasca.db file earlier versions created. */
fun Ledger.Companion.get(context: Context): Ledger = instance ?: synchronized(Ledger) {
    instance ?: open(Room.databaseBuilder<AppDb>(context.applicationContext, "tasca.db")).also { instance = it }
}
