package app.tasca.ui

import android.content.Context
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile

@Volatile private var instance: Settings? = null

/** The app's one settings file. Its first use moves in what the SharedPreferences file of earlier versions held. */
fun Settings.Companion.get(context: Context): Settings = instance ?: synchronized(Settings) {
    instance ?: context.applicationContext.let { app ->
        Settings(
            PreferenceDataStoreFactory.create(
                migrations = listOf(SharedPreferencesMigration(app, NAME)),
                produceFile = { app.preferencesDataStoreFile(NAME) },
            ),
        )
    }.also { instance = it }
}
