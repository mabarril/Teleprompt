package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val artist: String,
    val lyrics: String, // String separated by newlines \n
    val isCustom: Boolean = false
) {
    // Utility to get lyrics as a list of clean lyric lines
    fun getLines(): List<String> {
        return lyrics.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
