package app.tasca

import androidx.room.Room
import app.tasca.data.AppDb
import app.tasca.data.Ledger

actual fun inMemoryLedger(): Ledger = Ledger.open(Room.inMemoryDatabaseBuilder<AppDb>())
