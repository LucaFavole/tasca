package app.tasca.data

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

internal actual fun sqliteDriver(): SQLiteDriver = BundledSQLiteDriver()
