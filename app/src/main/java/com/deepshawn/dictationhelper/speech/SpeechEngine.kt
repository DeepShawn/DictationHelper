package com.deepshawn.dictationhelper.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 对系统 SpeechRecognizer 的封装。
 * - 单次模式：用于语音输入检索；
 * - 连续模式：识别结束后自动重启，用于“环境聆听”。
 * 所有方法需在主线程调用。
 */
class SpeechEngine(private val context: Context) {

    enum class Status { IDLE, LISTENING, ERROR }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _level = MutableStateFlow(0f)
    /** 0..1 的音量，用于动画 */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var languageTag = "zh-CN"
    private var consecutiveErrors = 0
    private var onFinal: (String) -> Unit = {}
    private var onPartial: (String) -> Unit = {}

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun start(
        languageTag: String,
        continuous: Boolean,
        onPartial: (String) -> Unit = {},
        onFinal: (String) -> Unit,
    ) {
        stop()
        if (!isAvailable()) {
            _error.value = "本机没有可用的语音识别服务，请安装或启用系统语音识别（如 Google 语音服务 / 厂商语音助手）"
            _status.value = Status.ERROR
            return
        }
        this.languageTag = languageTag
        this.continuous = continuous
        this.onFinal = onFinal
        this.onPartial = onPartial
        consecutiveErrors = 0
        _error.value = null
        launch()
    }

    fun stop() {
        continuous = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.let {
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null
        _partial.value = ""
        _level.value = 0f
        if (_status.value == Status.LISTENING) _status.value = Status.IDLE
    }

    private fun createRecognizer(): SpeechRecognizer =
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else if (Build.VERSION.SDK_INT >= 31) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun launch() {
        val r = recognizer ?: createRecognizer().also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            if (continuous) {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            }
        }
        _status.value = Status.LISTENING
        runCatching { r.startListening(intent) }.onFailure {
            _error.value = "启动语音识别失败：${it.message}"
            _status.value = Status.ERROR
        }
    }

    private fun restartLater(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ if (continuous) launch() }, delay)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { _level.value = 0f }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) {
                _partial.value = text
                onPartial(text)
            }
        }

        override fun onResults(results: Bundle?) {
            consecutiveErrors = 0
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            _partial.value = ""
            // 把多个候选结果都交给上层，提高模糊匹配命中率
            val joined = list.filter { it.isNotBlank() }.distinct().joinToString("\n")
            if (joined.isNotBlank()) onFinal(joined)
            if (continuous) restartLater(150) else _status.value = Status.IDLE
        }

        override fun onError(error: Int) {
            _level.value = 0f
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    _error.value = "没有麦克风权限"
                    continuous = false
                    _status.value = Status.ERROR
                    return
                }
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    _error.value = "当前语音服务不支持 $languageTag"
                    continuous = false
                    _status.value = Status.ERROR
                    return
                }
            }
            if (!continuous) {
                _status.value = if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    _error.value = "没有听清，请再说一次"
                    Status.IDLE
                } else {
                    _error.value = "语音识别出错（$error）"
                    Status.ERROR
                }
                return
            }
            // 连续模式：静音/没听清属于正常情况，直接重启；其他错误退避重试
            val benign = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (benign) {
                consecutiveErrors = 0
                restartLater(100)
                return
            }
            consecutiveErrors++
            if (consecutiveErrors > 8) {
                _error.value = "语音识别多次出错（$error），已停止聆听"
                continuous = false
                _status.value = Status.ERROR
                return
            }
            if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) {
                // 重新创建识别器
                recognizer?.let { runCatching { it.destroy() } }
                recognizer = null
            }
            restartLater(300L * consecutiveErrors)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
