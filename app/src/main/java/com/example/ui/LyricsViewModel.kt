package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GeminiService
import com.example.data.Song
import com.example.data.SongDatabase
import com.example.data.SongRepository
import com.example.speech.SpeechSyncManager
import com.example.speech.SyncStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: SongRepository
    
    // UI state flows
    val songs: StateFlow<List<Song>>
    
    private val _selectedSongId = MutableStateFlow<Int?>(null)
    val selectedSongId: StateFlow<Int?> = _selectedSongId

    val selectedSong: StateFlow<Song?>

    // Prompter State
    private val _currentLineIndex = MutableStateFlow(0)
    val currentLineIndex: StateFlow<Int> = _currentLineIndex

    // Custom Stage View Customization
    private val _fontSizeSp = MutableStateFlow(44f) // Adjustable display size
    val fontSizeSp: StateFlow<Float> = _fontSizeSp

    private val _matchSensitivity = MutableStateFlow(0.35f) // Adjustable fuzzy criteria
    val matchSensitivity: StateFlow<Float> = _matchSensitivity

    // Autoplay Simulator
    private val _isAutoplayActive = MutableStateFlow(false)
    val isAutoplayActive: StateFlow<Boolean> = _isAutoplayActive

    private val _autoplaySecondsPerLine = MutableStateFlow(5)
    val autoplaySecondsPerLine: StateFlow<Int> = _autoplaySecondsPerLine

    private var autoplayJob: Job? = null

    // Vocal Sync State
    private val speechSyncManager = SpeechSyncManager(application)
    val isListening = MutableStateFlow(false)
    val rmsDb: StateFlow<Float> = speechSyncManager.rmsDb
    val syncStatus: StateFlow<SyncStatus> = speechSyncManager.status
    val speechErrorMessage: StateFlow<String?> = speechSyncManager.errorMessage
    val partialSpokenText: StateFlow<String> = speechSyncManager.partialSpokenText
    val finalSpokenText: StateFlow<String> = speechSyncManager.finalSpokenText

    // Creation & Dialog States
    val showAddSongDialog = MutableStateFlow(false)
    val showEditSongDialog = MutableStateFlow(false)
    val isGeneratingWithAi = MutableStateFlow(false)
    val aiGenerationError = MutableStateFlow<String?>(null)

    // Forms
    val formTitle = MutableStateFlow("")
    val formArtist = MutableStateFlow("")
    val formLyrics = MutableStateFlow("")

    init {
        val database = SongDatabase.getDatabase(application)
        repository = SongRepository(database.songDao())
        
        songs = repository.allSongs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Reactively load selected song
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        selectedSong = _selectedSongId.flatMapLatest { id ->
            if (id != null) {
                repository.getSongById(id)
            } else {
                flowOf(null)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        // Automatically select the first song once the repertoire is loaded
        viewModelScope.launch {
            songs.collect { list ->
                if (list.isNotEmpty() && _selectedSongId.value == null) {
                    selectSong(list.first())
                }
            }
        }

        // Collect speech results to sync lyrics in real time
        viewModelScope.launch {
            partialSpokenText.collect { partial ->
                syncLyricsWithSpokenText(partial)
            }
        }
        viewModelScope.launch {
            finalSpokenText.collect { final ->
                syncLyricsWithSpokenText(final)
            }
        }
    }

    fun selectSong(song: Song) {
        Log.d("LyricsViewModel", "Selecting song: ${song.title}")
        _selectedSongId.value = song.id
        _currentLineIndex.value = 0
        stopAutoplay()
        stopListening()
    }

    fun selectNextLine() {
        val song = selectedSong.value ?: return
        val lines = song.getLines()
        if (_currentLineIndex.value < lines.lastIndex) {
            _currentLineIndex.value += 1
        }
    }

    fun selectPreviousLine() {
        if (_currentLineIndex.value > 0) {
            _currentLineIndex.value -= 1
        }
    }

    fun setCurrentLineIndex(index: Int) {
        val song = selectedSong.value ?: return
        val lines = song.getLines()
        if (index in 0..lines.lastIndex) {
            _currentLineIndex.value = index
        }
    }

    fun updateFontSize(delta: Float) {
        _fontSizeSp.value = (_fontSizeSp.value + delta).coerceIn(24f, 96f)
    }

    fun updateSensitivity(value: Float) {
        _matchSensitivity.value = value.coerceIn(0.15f, 0.70f)
    }

    // Toggle Microphone Syncing
    fun toggleVocalSync() {
        if (isListening.value) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        stopAutoplay()
        isListening.value = true
        speechSyncManager.startListening()
    }

    fun stopListening() {
        isListening.value = false
        speechSyncManager.stopListening()
    }

    // Auto Play Sync Simulator
    fun toggleAutoplay() {
        if (_isAutoplayActive.value) {
            stopAutoplay()
        } else {
            startAutoplay()
        }
    }

    private fun startAutoplay() {
        stopListening()
        _isAutoplayActive.value = true
        autoplayJob = viewModelScope.launch {
            while (_isAutoplayActive.value) {
                delay(_autoplaySecondsPerLine.value * 1000L)
                val song = selectedSong.value ?: break
                val lines = song.getLines()
                if (_currentLineIndex.value < lines.lastIndex) {
                    _currentLineIndex.value += 1
                } else {
                    _currentLineIndex.value = 0 // loop
                }
            }
        }
    }

    fun stopAutoplay() {
        _isAutoplayActive.value = false
        autoplayJob?.cancel()
        autoplayJob = null
    }

    fun updateAutoplaySpeed(seconds: Int) {
        _autoplaySecondsPerLine.value = seconds.coerceIn(2, 20)
        if (_isAutoplayActive.value) {
            stopAutoplay()
            startAutoplay()
        }
    }

    // String sync match logic
    private fun syncLyricsWithSpokenText(spokenText: String) {
        if (spokenText.isBlank()) return
        val song = selectedSong.value ?: return
        val lines = song.getLines()
        if (lines.isEmpty()) return

        val matchedIndex = SpeechSyncManager.findBestMatchIndex(
            spokenText = spokenText,
            lyricLines = lines,
            currentIndex = _currentLineIndex.value,
            sensitivity = _matchSensitivity.value
        )

        if (matchedIndex != -1 && matchedIndex != _currentLineIndex.value) {
            Log.d("LyricsViewModel", "SYNCED Lyric to index $matchedIndex based on spoken: '$spokenText'")
            _currentLineIndex.value = matchedIndex
        }
    }

    // Add Songs Forms
    fun openAddSongDialog() {
        formTitle.value = ""
        formArtist.value = ""
        formLyrics.value = ""
        aiGenerationError.value = null
        showAddSongDialog.value = true
    }

    fun openEditSongDialog() {
        val song = selectedSong.value ?: return
        formTitle.value = song.title
        formArtist.value = song.artist
        formLyrics.value = song.lyrics
        aiGenerationError.value = null
        showEditSongDialog.value = true
    }

    fun generateLyricsWithGemini() {
        val title = formTitle.value
        val artist = formArtist.value

        if (title.isBlank()) {
            aiGenerationError.value = "Informe o título do música para gerar."
            return
        }

        isGeneratingWithAi.value = true
        aiGenerationError.value = null

        viewModelScope.launch {
            val result = GeminiService.generateLyrics(title, artist)
            isGeneratingWithAi.value = false
            result.onSuccess { generated ->
                formLyrics.value = generated
            }.onFailure { error ->
                aiGenerationError.value = error.localizedMessage ?: "Erro desconhecido na I.A."
            }
        }
    }

    fun saveNewSong() {
        val title = formTitle.value
        val artist = formArtist.value
        val lyrics = formLyrics.value

        if (title.isBlank() || lyrics.isBlank()) return

        viewModelScope.launch {
            val newSong = Song(title = title, artist = artist, lyrics = lyrics, isCustom = true)
            val newId = repository.insertSong(newSong)
            showAddSongDialog.value = false
            // select the new song!
            _selectedSongId.value = newId.toInt()
            _currentLineIndex.value = 0
        }
    }

    fun saveEditedSong() {
        val song = selectedSong.value ?: return
        val title = formTitle.value
        val artist = formArtist.value
        val lyrics = formLyrics.value

        if (title.isBlank() || lyrics.isBlank()) return

        viewModelScope.launch {
            val updatedSong = song.copy(title = title, artist = artist, lyrics = lyrics)
            repository.updateSong(updatedSong)
            showEditSongDialog.value = false
            _currentLineIndex.value = 0
        }
    }

    fun deleteCurrentSong() {
        val song = selectedSong.value ?: return
        viewModelScope.launch {
            stopAutoplay()
            stopListening()
            repository.deleteSong(song)
            _selectedSongId.value = null
            
            // Auto select first remaining song
            val list = songs.value
            if (list.isNotEmpty()) {
                val nextSong = list.firstOrNull { it.id != song.id } ?: list.first()
                selectSong(nextSong)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechSyncManager.stopListening()
    }
}
