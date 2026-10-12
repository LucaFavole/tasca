package app.tasca

import app.tasca.data.Ledger

/** A ledger in a fresh in-memory database, opened as the app opens its own. */
expect fun inMemoryLedger(): Ledger
