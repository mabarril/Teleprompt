package com.example.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

enum class SyncStatus {
    IDLE,
    LISTENING,
    MATCH_FOUND,
    ERROR
}

class SpeechSyncManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizerIntent: Intent? = null
    private var isListeningLoopActive = false

    private val _partialSpokenText = MutableStateFlow("")
    val partialSpokenText: StateFlow<String> = _partialSpokenText

    private val _finalSpokenText = MutableStateFlow("")
    val finalSpokenText: StateFlow<String> = _finalSpokenText

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb

    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val mainScope = CoroutineScope(Dispatchers.Main)

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _status.value = SyncStatus.LISTENING
                        _errorMessage.value = null
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        // Rms DB typically ranges from negative values to ~12dB
                        // Normalize it between 0f (quiet) and 1f (loud) for simple bar visualizers
                        val minDb = -2f
                        val maxDb = 10f
                        val normalized = ((rmsdB - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
                        _rmsDb.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val message = getErrorMessage(error)
                        Log.w("SpeechSyncManager", "SpeechRecognizer error: $message (code $error)")
                        
                        // Error 7 (NO_MATCH) or Error 6 (SPEECH_TIMEOUT) are standard pauses in signing
                        // We do not want to show hard scary error messages for them, just continue listening
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            _rmsDb.value = 0f
                            restartListeningIfNeeded()
                        } else {
                            _status.value = SyncStatus.ERROR
                            _errorMessage.value = message
                            _rmsDb.value = 0f
                            restartListeningIfNeeded()
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            _finalSpokenText.value = recognized
                            _partialSpokenText.value = ""
                            _status.value = SyncStatus.MATCH_FOUND
                        }
                        restartListeningIfNeeded()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            _partialSpokenText.value = matches[0]
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR") // Default to Brazilian Portuguese
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Enforce continuous-like behavior triggers
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
        } else {
            _status.value = SyncStatus.ERROR
            _errorMessage.value = "Reconhecimento de voz indisponível neste dispositivo."
        }
    }

    fun startListening() {
        Log.d("SpeechSyncManager", "startListening called. speechRecognizer: $speechRecognizer")
        if (speechRecognizer == null) {
            initializeRecognizer()
        }
        isListeningLoopActive = true
        _errorMessage.value = null
        try {
            speechRecognizer?.startListening(recognizerIntent)
            _status.value = SyncStatus.LISTENING
        } catch (e: Exception) {
            _status.value = SyncStatus.ERROR
            _errorMessage.value = "Erro ao iniciar captura: ${e.localizedMessage}"
            Log.e("SpeechSyncManager", "startListening error", e)
        }
    }

    fun stopListening() {
        Log.d("SpeechSyncManager", "stopListening called.")
        isListeningLoopActive = false
        _status.value = SyncStatus.IDLE
        _rmsDb.value = 0f
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {
            Log.e("SpeechSyncManager", "stopListening/destroy error", e)
        }
    }

    private fun restartListeningIfNeeded() {
        if (isListeningLoopActive) {
            mainScope.launch {
                try {
                    speechRecognizer?.cancel()
                    speechRecognizer?.startListening(recognizerIntent)
                } catch (e: Exception) {
                    Log.e("SpeechSyncManager", "restartListening error", e)
                }
            }
        }
    }

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Erro de gravação de áudio."
            SpeechRecognizer.ERROR_CLIENT -> "Erro no aplicativo cliente."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissão para gravar áudio necessária."
            SpeechRecognizer.ERROR_NETWORK -> "Erro de conexão com a rede."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Tempo esgotado de rede."
            SpeechRecognizer.ERROR_NO_MATCH -> "Nenhuma fala detectada."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Serviço de reconhecimento ocupado."
            SpeechRecognizer.ERROR_SERVER -> "Erro no servidor de reconhecimento."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nenhum sinal detectado."
            else -> "Erro desconhecido ($errorCode)."
        }
    }

    companion object {
        /**
         * Normalizes a string for clean comparing:
         * - Converts to lowercase
         * - Strips accents/diacritics
         * - Removes punctuation
         * - Reduces multi-spaces to single
         */
        fun normalizeText(text: String): String {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            val pattern = "\\p{InCombiningDiacriticalMarks}+".toRegex()
            return pattern.replace(normalized, "")
                .lowercase()
                .replace("[^a-zA-Z0-9áéíóúâêôãõç\\s]".toRegex(), " ") // Keep alphanumeric
                .replace("\\s+".toRegex(), " ")
                .trim()
        }

        /**
         * Calculates fuzzy match score between the spoken text and a single lyric line.
         * Returns a score between 0.0 (no match) and 1.0 (perfect match).
         */
        fun calculateMatchScore(spokenNormalized: String, lineNormalized: String): Float {
            val spokenWords = spokenNormalized.split(" ").filter { it.length > 1 }.toSet()
            val lineWords = lineNormalized.split(" ").filter { it.length > 1 }.toSet()

            if (lineWords.isEmpty() || spokenWords.isEmpty()) return 0f

            // Clean common stop words to avoid bias
            val stopWords = setOf("o", "a", "os", "as", "um", "uma", "de", "do", "da", "em", "no", "na", "e", "que", "se", "por", "para", "com", "the", "a", "of", "and", "in", "to")
            val filteredLineWords = lineWords.filter { it !in stopWords }.toSet()
            val filteredSpokenWords = spokenWords.filter { it !in stopWords }.toSet()

            if (filteredLineWords.isEmpty()) {
                // Fallback to literal if line only had stop words
                val intersection = lineWords.intersect(spokenWords)
                return intersection.size.toFloat() / lineWords.size.toFloat()
            }

            val intersection = filteredLineWords.intersect(filteredSpokenWords)
            return intersection.size.toFloat() / filteredLineWords.size.toFloat()
        }

        /**
         * Finds the best matching lyric line index for the current normalized spoken phrase.
         * Prioritizes lines centered around the current index to ensure forward progress,
         * but allows scanning all lines if no close matches are found (allowing choruses jumping).
         */
        fun findBestMatchIndex(
            spokenText: String,
            lyricLines: List<String>,
            currentIndex: Int,
            sensitivity: Float = 0.35f // Lower means more lenient matching
        ): Int {
            if (spokenText.isBlank() || lyricLines.isEmpty()) return -1

            val spokenNorm = normalizeText(spokenText)
            val normalizedLines = lyricLines.map { normalizeText(it) }

            // 1. Scan ahead locally: check [currentIndex - 1] up to [currentIndex + 4]
            var bestLocalIndex = -1
            var bestLocalScore = 0f

            val lookbehind = 1
            val lookahead = 4
            val startIdx = (currentIndex - lookbehind).coerceAtLeast(0)
            val endIdx = (currentIndex + lookahead).coerceAtMost(lyricLines.lastIndex)

            for (i in startIdx..endIdx) {
                val score = calculateMatchScore(spokenNorm, normalizedLines[i])
                if (score > bestLocalScore) {
                    bestLocalScore = score
                    bestLocalIndex = i
                }
            }

            // If we found a solid local progression score, return it!
            if (bestLocalScore >= sensitivity) {
                return bestLocalIndex
            }

            // 2. Global scan fallback: Search the entire song if they jumped back/forward/chorus
            var bestGlobalIndex = -1
            var bestGlobalScore = 0f

            for (i in lyricLines.indices) {
                val score = calculateMatchScore(spokenNorm, normalizedLines[i])
                if (score > bestGlobalScore) {
                    bestGlobalScore = score
                    bestGlobalIndex = i
                }
            }

            if (bestGlobalScore >= sensitivity) {
                return bestGlobalIndex
            }

            // No matching found above sensitivity threshold
            return -1
        }
    }
}
