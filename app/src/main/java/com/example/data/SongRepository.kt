package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

class SongRepository(private val songDao: SongDao) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()
        .onStart {
            // Ensure pre-population if empty
            if (songDao.getSongCount() == 0) {
                SongDatabase.populateDatabase(songDao)
            }
        }

    fun getSongById(id: Int): Flow<Song?> = songDao.getSongById(id)

    suspend fun insertSong(song: Song): Long = songDao.insertSong(song)

    suspend fun updateSong(song: Song) = songDao.updateSong(song)

    suspend fun deleteSong(song: Song) = songDao.deleteSong(song)
}
