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
package dev.animeplayer.sources.hianime

import java.io.File

/**
 * Loads the golden payloads captured from the live site.
 *
 * The fixtures live in the `fixtures` directory at the repository root and are handed to
 * the test JVM through the `animeplayer.fixturesDir` system property.
 */
internal object Fixtures {

    fun load(name: String): String {
        val directory = System.getProperty("animeplayer.fixturesDir")
            ?: error("The system property 'animeplayer.fixturesDir' is not set")
        val file = File(directory, name)
        check(file.isFile) { "Fixture $name not found in $directory" }
        return file.readText()
    }
}
