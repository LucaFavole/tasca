package app.tasca.data

import androidx.room.Room

fun Ledger.Companion.open(path: String): Ledger = open(Room.databaseBuilder<AppDb>(name = path))
