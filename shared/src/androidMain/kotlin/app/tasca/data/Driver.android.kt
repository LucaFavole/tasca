package app.tasca.data

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver

internal actual fun sqliteDriver(): SQLiteDriver = AndroidSQLiteDriver()
