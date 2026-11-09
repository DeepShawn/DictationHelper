package com.deepshawn.dictationhelper

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deepshawn.dictationhelper.ai.Completion
import com.deepshawn.dictationhelper.ai.DeepSeekClient
import com.deepshawn.dictationhelper.data.AppSettings
import com.deepshawn.dictationhelper.data.Draft
import com.deepshawn.dictationhelper.data.ListenLang
import com.deepshawn.dictationhelper.data.WordEntry
import com.deepshawn.dictationhelper.data.WordRepository
import com.deepshawn.dictationhelper.speech.SpeechEngine
import com.deepshawn.dictationhelper.util.FuzzyMatcher
import com.deepshawn.dictationhelper.util.IndexedEntry
import com.deepshawn.dictationhelper.util.Match
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 环境聆听命中的词条 */
data class Hit(val entry: WordEntry, val score: Float, val count: Int, val lastAt: Long, val viaTranslation: Boolean)

enum class SpeechMode { NONE, LISTEN, VOICE_SEARCH }

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dictApp = app as DictationApp
    val repository: WordRepository = dictApp.repository
    private val settingsStore = dictApp.settings

    val settings: StateFlow<AppSettings> = settingsStore.state
    val words: StateFlow<List<WordEntry>> = repository.words

    private val indexed: StateFlow<List<IndexedEntry>> = repository.words
        .map { FuzzyMatcher.index(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun toast(msg: String) { _messages.tryEmit(msg) }

    fun updateSettings(block: (AppSettings) -> AppSettings) = settingsStore.update(block)

    // ---------------- 词表编辑 ----------------

    fun save(entry: WordEntry) = repository.upsert(entry)
    fun newEntry(draft: Draft) = repository.newEntry(draft)
    fun delete(entry: WordEntry) = repository.delete(setOf(entry.id))
    fun clearAll() = repository.clear()

    fun importDrafts(drafts: List<Draft>) {
        val n = repository.addAll(drafts)
        val skipped = drafts.size - n
        toast(if (skipped > 0) "已导入 $n 条，跳过重复 $skipped 条" else "已导入 $n 条")
    }

    fun importJson(raw: String) {
        viewModelScope.launch {
            val drafts = withContext(Dispatchers.Default) { runCatching { WordRepository.parseDrafts(raw) } }
            drafts.onSuccess {
                if (it.isEmpty()) toast("文件中没有找到词条") else importDrafts(it)
            }.onFailure { toast("JSON 格式错误：${it.message?.take(80)}") }
        }
    }

    fun exportJson(): String = repository.exportJson()

    // ---------------- AI ----------------

    private val _aiBusy = MutableStateFlow(false)
    val aiBusy: StateFlow<Boolean> = _aiBusy.asStateFlow()

    fun complete(text: String, onResult: (Completion) -> Unit) {
        if (text.isBlank()) { toast("请先输入内容"); return }
        viewModelScope.launch {
            _aiBusy.value = true
            runCatching { DeepSeekClient.complete(settings.value.apiKey, text) }
                .onSuccess(onResult)
                .onFailure { toast(it.message ?: "补全失败") }
            _aiBusy.value = false
        }
    }

    fun testApiKey() {
        viewModelScope.launch {
            _aiBusy.value = true
            runCatching { DeepSeekClient.ping(settings.value.apiKey) }
                .onSuccess { toast("连接成功，密钥可用") }
                .onFailure { toast(it.message ?: "连接失败") }
            _aiBusy.value = false
        }
    }

    /** 视觉识别结果，非空时界面弹出确认导入的面板 */
    private val _visionDrafts = MutableStateFlow<List<Draft>?>(null)
    val visionDrafts: StateFlow<List<Draft>?> = _visionDrafts.asStateFlow()
    private val _visionBusy = MutableStateFlow(false)
    val visionBusy: StateFlow<Boolean> = _visionBusy.asStateFlow()

    fun recognize(bitmap: Bitmap) {
        viewModelScope.launch {
            _visionBusy.value = true
            runCatching { DeepSeekClient.recognizeImage(settings.value.apiKey, bitmap) }
                .onSuccess { if (it.isEmpty()) toast("没有识别到词条") else _visionDrafts.value = it }
                .onFailure { toast(it.message ?: "识别失败") }
            _visionBusy.value = false
        }
    }

    fun dismissVision() { _visionDrafts.value = null }

    // ---------------- 检索（虚拟键盘 + 语音模糊） ----------------

    private val _letters = MutableStateFlow("")
    val letters: StateFlow<String> = _letters.asStateFlow()
    private val _voiceQuery = MutableStateFlow<String?>(null)
    val voiceQuery: StateFlow<String?> = _voiceQuery.asStateFlow()

    fun typeLetter(c: Char) { _voiceQuery.value = null; _letters.value += c.lowercaseChar() }
    fun backspace() { _voiceQuery.value = null; _letters.value = _letters.value.dropLast(1) }
    fun clearQuery() { _letters.value = ""; _voiceQuery.value = null }

    val searchResults: StateFlow<List<Match>> =
        combine(indexed, _letters, _voiceQuery) { idx, letters, voice ->
            when {
                voice != null -> voice.split('\n')
                    .flatMap { FuzzyMatcher.fuzzySearch(it, idx) }
                    .groupBy { it.entry.id }
                    .map { (_, ms) -> ms.maxBy { it.score } }
                    .sortedByDescending { it.score }
                else -> FuzzyMatcher.letterSearch(letters, idx)
            }
        }.flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------------- 语音 ----------------

    val speech = SpeechEngine(app)
    private val _speechMode = MutableStateFlow(SpeechMode.NONE)
    val speechMode: StateFlow<SpeechMode> = _speechMode.asStateFlow()

    init {
        viewModelScope.launch {
            speech.error.filterNotNull().collect { toast(it) }
        }
        viewModelScope.launch {
            speech.status.collect { st ->
                if (st != SpeechEngine.Status.LISTENING) _speechMode.value = SpeechMode.NONE
            }
        }
    }

    fun startVoiceSearch() {
        stopSpeech()
        _speechMode.value = SpeechMode.VOICE_SEARCH
        speech.start(
            languageTag = settings.value.voiceLang.tag,
            continuous = false,
            onPartial = { _voiceQuery.value = it },
            onFinal = {
                _voiceQuery.value = it
                _speechMode.value = SpeechMode.NONE
            },
        )
    }

    private val _transcript = MutableStateFlow<List<String>>(emptyList())
    /** 最近几段识别文本（最新在最后） */
    val transcript: StateFlow<List<String>> = _transcript.asStateFlow()
    private val _hits = MutableStateFlow<List<Hit>>(emptyList())
    val hits: StateFlow<List<Hit>> = _hits.asStateFlow()
    private val _hitEvents = MutableSharedFlow<Hit>(extraBufferCapacity = 16)
    /** 每次新命中（用于振动反馈） */
    val hitEvents: SharedFlow<Hit> = _hitEvents.asSharedFlow()
    /** 本段语音中已命中过的词条，避免部分结果反复计数 */
    private val hitInSegment = HashSet<Long>()

    fun setListenLang(lang: ListenLang) {
        updateSettings { it.copy(listenLang = lang) }
        if (_speechMode.value == SpeechMode.LISTEN) startListening()
    }

    fun startListening() {
        stopSpeech()
        _speechMode.value = SpeechMode.LISTEN
        hitInSegment.clear()
        speech.start(
            languageTag = settings.value.listenLang.tag,
            continuous = true,
            onPartial = { detect(it, final = false) },
            onFinal = { text ->
                detect(text, final = true)
                _transcript.value = (_transcript.value + text.substringBefore('\n')).takeLast(6)
            },
        )
    }

    fun stopSpeech() {
        speech.stop()
        _speechMode.value = SpeechMode.NONE
    }

    fun clearHits() { _hits.value = emptyList(); _transcript.value = emptyList() }

    private fun detect(text: String, final: Boolean) {
        val idx = indexed.value
        viewModelScope.launch {
            val found = withContext(Dispatchers.Default) {
                text.split('\n').flatMap { FuzzyMatcher.detectInTranscript(it, idx) }
                    .groupBy { it.entry.id }.map { (_, ms) -> ms.maxBy { it.score } }
            }
            if (found.isEmpty()) {
                if (final) hitInSegment.clear()
                return@launch
            }
            val now = System.currentTimeMillis()
            val current = _hits.value.associateBy { it.entry.id }.toMutableMap()
            for (m in found) {
                if (m.entry.id in hitInSegment) continue
                hitInSegment += m.entry.id
                val old = current[m.entry.id]
                val hit = Hit(m.entry, m.score, (old?.count ?: 0) + 1, now, m.viaTranslation)
                current[m.entry.id] = hit
                _hitEvents.tryEmit(hit)
            }
            _hits.value = current.values.sortedByDescending { it.lastAt }
            if (final) hitInSegment.clear()
        }
    }

    override fun onCleared() {
        speech.stop()
        super.onCleared()
    }
}
