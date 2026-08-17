package com.shslab.leo.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ═══════════════════════════════════════════════════════════════
 *  LEO TTS MANAGER — FULLY FUNCTIONAL TEXT-TO-SPEECH
 *
 *  Uses Android's built-in TextToSpeech engine (works offline).
 *  Features:
 *  - Streaming: queues text chunks as model produces them
 *  - Multiple language support
 *  - Adjustable speed/pitch
 *  - Stop/cancel support
 *  - Callback when speech completes
 *
 *  No external API needed — uses system TTS engine.
 * ═══════════════════════════════════════════════════════════════
 */
class LeoTtsManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeaking = false

    // Queue for streaming text chunks
    private val textQueue = ConcurrentLinkedQueue<String>()
    private var currentUtteranceId = 0

    // Callbacks
    var onSpeechStart: (() -> Unit)? = null
    var onSpeechComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // Settings
    private var speechRate = 1.0f  // 0.5 to 2.0
    private var pitch = 1.0f       // 0.5 to 2.0
    private var language = Locale.getDefault()

    fun init() {
        if (isInitialized) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(language)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fall back to English
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setSpeechRate(speechRate)
                tts?.setPitch(pitch)
                isInitialized = true
                setupProgressListener()
                Log.d("LeoTts", "TTS initialized successfully")
            } else {
                Log.e("LeoTts", "TTS init failed with status: $status")
                onError?.invoke("TTS initialization failed")
            }
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                onSpeechStart?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                // Check if there's more in the queue
                val next = textQueue.poll()
                if (next != null) {
                    speakChunk(next)
                } else {
                    isSpeaking = false
                    onSpeechComplete?.invoke()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                onError?.invoke("TTS error for utterance: $utteranceId")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                onError?.invoke("TTS error code: $errorCode")
            }
        })
    }

    /**
     * Speak text immediately. If already speaking, queues this text.
     */
    fun speak(text: String) {
        if (!isInitialized) {
            init()
        }
        if (text.isBlank()) return

        if (isSpeaking) {
            // Queue for later
            textQueue.add(text)
        } else {
            speakChunk(text)
        }
    }

    /**
     * Stream text as it's being generated.
     * Call this with chunks of text as the model produces them.
     */
    fun streamText(chunk: String) {
        if (chunk.isBlank()) return
        if (isSpeaking) {
            textQueue.add(chunk)
        } else {
            speakChunk(chunk)
        }
    }

    private fun speakChunk(text: String) {
        if (!isInitialized || text.isBlank()) return
        val id = "leo_tts_${currentUtteranceId++}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Stop all speech and clear queue */
    fun stop() {
        textQueue.clear()
        tts?.stop()
        isSpeaking = false
    }

    /** Pause (stop current speech but keep queue) */
    fun pause() {
        tts?.stop()
        isSpeaking = false
    }

    /** Resume from queue */
    fun resume() {
        val next = textQueue.poll()
        if (next != null) {
            speakChunk(next)
        }
    }

    fun isSpeaking(): Boolean = isSpeaking

    fun setSpeechRate(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(speechRate)
    }

    fun setPitch(p: Float) {
        pitch = p.coerceIn(0.5f, 2.0f)
        tts?.setPitch(pitch)
    }

    fun setLanguage(locale: Locale) {
        language = locale
        tts?.setLanguage(locale)
    }

    /** Check if TTS engine is available */
    fun isAvailable(): Boolean = isInitialized

    /** Cleanup */
    fun shutdown() {
        textQueue.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isSpeaking = false
    }
}
