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
package dev.animeplayer.cli

/**
 * The command line options, modelled after the ones of the original `ani-cli`.
 */
public data class CliOptions(
    val query: String? = null,
    val episode: String? = null,
    val quality: String? = null,
    val audioTrack: dev.animeplayer.core.model.AudioTrack =
        dev.animeplayer.core.model.AudioTrack.Sub,
    val selectNth: Int? = null,
    val continueWatching: Boolean = false,
    val listQualities: Boolean = false,
    val showSchedule: Boolean = false,
    val json: Boolean = false,
    val player: String = "mpv",
) {
    public companion object {
        public val USAGE: String = """
            Usage: animeplayer [options] [query]

            Options:
              -e, --episode <n>   The episode to play, a range (5-8) or a list ("5 8")
              -q, --quality <q>   The preferred quality, for example 720p. Defaults to best
                  --dub            Play the dubbed version
              -S, --select-nth <n> Pick the n-th search result instead of choosing
              -c, --continue      Continue with the most recent history entry
                  --list           List the available qualities of the episode
                  --schedule       Show the broadcast schedule of the series
                  --json           Print the result as JSON
                  --player <name>  The player to use, mpv or debug. Defaults to mpv
              -h, --help          Show this help
        """.trimIndent()

        /**
         * Parses the command line arguments.
         */
        public fun parse(args: Array<String>): CliOptions {
            var options = CliOptions()
            val query = StringBuilder()

            var index = 0
            while (index < args.size) {
                when (val argument = args[index]) {
                    "-h", "--help" -> {
                        println(USAGE)
                        kotlin.system.exitProcess(0)
                    }

                    "-e", "--episode", "-r", "--range" -> {
                        options = options.copy(episode = args.getOrNull(++index))
                    }

                    "-q", "--quality" -> {
                        options = options.copy(quality = args.getOrNull(++index))
                    }

                    "-S", "--select-nth" -> {
                        options = options.copy(selectNth = args.getOrNull(++index)?.toIntOrNull())
                    }

                    "--dub" -> options = options.copy(
                        audioTrack = dev.animeplayer.core.model.AudioTrack.Dub,
                    )

                    "-c", "--continue" -> options = options.copy(continueWatching = true)

                    "--list" -> options = options.copy(listQualities = true)

                    "--schedule" -> options = options.copy(showSchedule = true)

                    "--json" -> options = options.copy(json = true)

                    "--player" -> {
                        options = options.copy(player = args.getOrNull(++index) ?: "mpv")
                    }

                    else -> {
                        if (argument.startsWith("-")) {
                            System.err.println("Unknown option: $argument")
                            println(USAGE)
                            kotlin.system.exitProcess(2)
                        }
                        if (query.isNotEmpty()) query.append(' ')
                        query.append(argument)
                    }
                }
                index++
            }

            return options.copy(query = query.toString().trim().takeIf { it.isNotEmpty() })
        }
    }
}
