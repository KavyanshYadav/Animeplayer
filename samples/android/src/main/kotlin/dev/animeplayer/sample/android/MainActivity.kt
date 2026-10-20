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
package dev.animeplayer.sample.android

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.ui.PlayerView
import dev.animeplayer.core.model.Anime
import dev.animeplayer.core.model.Episode
import dev.animeplayer.core.model.PlayableSource
import dev.animeplayer.core.spi.PlaybackRequest
import dev.animeplayer.network.KtorHttpTransport
import dev.animeplayer.network.TransportConfig
import dev.animeplayer.network.createDefaultHttpClient
import dev.animeplayer.player.media3.Media3PlayerEngine
import dev.animeplayer.sources.hianime.HianimeConfig
import dev.animeplayer.sources.hianime.HianimeSource
import kotlinx.coroutines.launch

/**
 * A minimal Android client: search, pick a series, pick an episode, play it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var resultList: ListView
    private lateinit var playerView: PlayerView

    private lateinit var source: HianimeSource
    private lateinit var playerEngine: Media3PlayerEngine

    private val items = mutableListOf<Any>()
    private val adapter by lazy {
        ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.searchButton)
        resultList = findViewById(R.id.resultList)
        playerView = findViewById(R.id.playerView)

        val client = createDefaultHttpClient()
        val transport = KtorHttpTransport(
            client = client,
            config = TransportConfig(userAgent = HianimeConfig.DEFAULT_USER_AGENT),
        )
        source = HianimeSource(transport, HianimeConfig())
        playerEngine = Media3PlayerEngine(applicationContext, HianimeConfig.DEFAULT_USER_AGENT)

        resultList.adapter = adapter
        resultList.setOnItemClickListener { _, _, position, _ -> onItemClicked(position) }

        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) search(query)
        }
    }

    private fun search(query: String) {
        lifecycleScope.launch {
            searchButton.isEnabled = false
            try {
                val results = source.search(query)
                items.clear()
                items.addAll(results)
                adapter.notifyDataSetChanged()
            } finally {
                searchButton.isEnabled = true
            }
        }
    }

    private fun onItemClicked(position: Int) {
        when (val item = items.getOrNull(position)) {
            is Anime -> showEpisodes(item)
            is Episode -> play(item)
        }
    }

    private fun showEpisodes(anime: Anime) {
        lifecycleScope.launch {
            val episodes = source.episodes(anime.id).episodes
            items.clear()
            items.addAll(episodes)
            adapter.notifyDataSetChanged()
        }
    }

    private fun play(episode: Episode) {
        lifecycleScope.launch {
            val playable: PlayableSource = source.resolve(episode.animeId, episode)
            val session = playerEngine.prepare(
                PlaybackRequest(
                    source = playable,
                    title = "${episode.animeId.slug} episode ${episode.number}",
                ),
            )
            session.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playerView.player = null
    }
}
