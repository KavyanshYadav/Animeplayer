/*
 * Copyright (C) 2026 animeplayer contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version. See <https://www.gnu.org/licenses/> for the full text.
 */
package dev.animeplayer.gui

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.animeplayer.core.spi.WatchHistoryStore
import dev.animeplayer.storage.SqlDelightWatchHistoryStore
import dev.animeplayer.storage.createDatabase
import dev.animeplayer.storage.db.AnimePlayerDatabase
import java.io.File

/**
 * Opens the watch history.
 *
 * The database lives in the application state directory. If it cannot be opened the
 * application still works — it just does not remember anything.
 */
internal fun openHistoryStore(): WatchHistoryStore? = runCatching {
    val directory = File(System.getProperty("user.home"), ".local/state/animeplayer")
    directory.mkdirs()

    val file = File(directory, "history.db")
    val isNew = !file.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")

    // SQLDelight does not create the schema on its own — but only when the database is
    // new. Running the create statements on an existing file fails.
    if (isNew) {
        AnimePlayerDatabase.Schema.create(driver)
    } else {
        AnimePlayerDatabase.Schema.migrate(driver, 0, AnimePlayerDatabase.Schema.version)
    }

    SqlDelightWatchHistoryStore(createDatabase(driver))
}.getOrElse { error ->
    System.err.println("The watch history is disabled: ${error.message}")
    null
}
