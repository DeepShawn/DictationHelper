package com.deepshawn.dictationhelper.util

import com.deepshawn.dictationhelper.data.Lang
import com.deepshawn.dictationhelper.data.WordEntry

/** 预先计算好拼音/首字母等信息的词条，用于快速检索 */
class IndexedEntry(val entry: WordEntry) {
    val norm: String = TextUtils.normalize(entry.text)
    val letters: String = TextUtils.lettersOnly(entry.text)
    val enWords: List<String> = TextUtils.englishWords(entry.text)
    val syllables: List<Pinyin.Syllable> = Pinyin.syllables(entry.text)
    val initials: String = if (entry.lang == Lang.ZH) Pinyin.initials(entry.text) else enWords.joinToString("") { it.take(1) }
    val fullPinyin: String = if (entry.lang == Lang.ZH) Pinyin.full(entry.text) else letters
    val translationNorm: String = TextUtils.normalize(entry.translation)
    /** 释义的第一个义项，例如 “苹果；苹果树” -> “苹果” */
    val translationKey: String = TextUtils.normalize(entry.translation.split(Regex("[,，;；/、]")).first())
    val translationSyllables: List<Pinyin.Syllable> = Pinyin.syllables(translationKey)
}

data class Match(val entry: WordEntry, val score: Float, val viaTranslation: Boolean = false)

object FuzzyMatcher {

    fun index(entries: List<WordEntry>): List<IndexedEntry> = entries.map(::IndexedEntry)

    /**
     * 字母检索（虚拟键盘）：
     * 英文按前缀/单词首字母匹配，中文按拼音首字母/全拼匹配。
     */
    fun letterSearch(query: String, entries: List<IndexedEntry>): List<Match> {
        val q = TextUtils.lettersOnly(query)
        if (q.isEmpty()) return entries.map { Match(it.entry, 0f) }
        val allowContains = q.length >= 2
        return entries.mapNotNull { e ->
            val score = when (e.entry.lang) {
                Lang.EN -> when {
                    e.letters == q -> 4f
                    e.letters.startsWith(q) -> 3f
                    e.initials.startsWith(q) -> 2.5f
                    e.enWords.any { it.startsWith(q) } -> 2f
                    allowContains && e.letters.contains(q) -> 1f
                    else -> 0f
                }
                Lang.ZH -> when {
                    e.initials == q || e.fullPinyin == q -> 4f
                    e.initials.startsWith(q) -> 3f
                    e.fullPinyin.startsWith(q) -> 2.5f
                    allowContains && e.initials.contains(q) -> 1.5f
                    allowContains && e.fullPinyin.contains(q) -> 1f
                    else -> 0f
                }
            }
            if (score > 0f) Match(e.entry, score) else null
        }.sortedWith(compareByDescending<Match> { it.score }.thenBy { it.entry.text.length })
    }

    /**
     * 模糊检索（语音输入）：按拼音音节对齐 + 模糊音 + 编辑距离打分，
     * 既匹配词条原文，也匹配释义（说中文释义也能找到英文单词）。
     */
    fun fuzzySearch(query: String, entries: List<IndexedEntry>, threshold: Float = 0.55f): List<Match> {
        val qNorm = TextUtils.normalize(query)
        if (qNorm.isEmpty()) return emptyList()
        val qSyl = Pinyin.syllables(query)
        return entries.mapNotNull { e ->
            val direct = when {
                e.norm == qNorm -> 1f
                e.norm.isNotEmpty() && (qNorm.contains(e.norm) || e.norm.contains(qNorm)) -> 0.92f
                else -> bestAlignment(qSyl, e.syllables)
            }
            val viaTr = when {
                e.translationNorm.isEmpty() -> 0f
                e.translationNorm == qNorm -> 0.95f
                e.translationNorm.contains(qNorm) && qNorm.length >= 2 -> 0.85f
                else -> bestAlignment(qSyl, e.translationSyllables) * 0.9f
            }
            val best = maxOf(direct, viaTr)
            if (best >= threshold) Match(e.entry, best, viaTranslation = viaTr > direct) else null
        }.sortedByDescending { it.score }
    }

    /**
     * 环境聆听：在一段识别出的文字中找出出现过的词条（允许模糊音和少量识别错误）。
     */
    fun detectInTranscript(transcript: String, entries: List<IndexedEntry>): List<Match> {
        val tNorm = TextUtils.normalize(transcript)
        if (tNorm.isEmpty()) return emptyList()
        val tSyl = Pinyin.syllables(transcript)
        return entries.mapNotNull { e ->
            if (e.syllables.isEmpty()) return@mapNotNull null
            val exact = e.norm.isNotEmpty() && tNorm.contains(e.norm)
            val score = if (exact) 1f else containScore(tSyl, e.syllables)
            // 越短的词越容易误判，要求越高
            val need = when (e.syllables.size) {
                1 -> if (e.syllables[0].isHan) 0.99f else 0.8f
                2 -> 0.85f
                else -> 0.78f
            }
            var best = if (score >= need) score else 0f
            var viaTr = false
            // 英文词条：中文释义被说出来也算命中
            if (best == 0f && e.entry.lang == Lang.EN && e.translationSyllables.size >= 2) {
                val trScore = if (tNorm.contains(e.translationKey)) 0.95f
                else containScore(tSyl, e.translationSyllables) * 0.95f
                if (trScore >= 0.85f) { best = trScore; viaTr = true }
            }
            if (best > 0f) Match(e.entry, best, viaTr) else null
        }.sortedByDescending { it.score }
    }

    /** needle 完整出现在 haystack 中的最佳得分（滑动窗口，平均音节相似度） */
    fun containScore(haystack: List<Pinyin.Syllable>, needle: List<Pinyin.Syllable>): Float {
        val n = needle.size
        if (n == 0 || haystack.size < n) return 0f
        var best = 0f
        for (start in 0..haystack.size - n) {
            var sum = 0f
            for (i in 0 until n) {
                sum += Pinyin.syllableSimilarity(haystack[start + i], needle[i])
                // 提前剪枝：剩余全对也追不上
                if (sum + (n - i - 1) < best * n) break
            }
            val avg = sum / n
            if (avg > best) best = avg
        }
        return best
    }

    /** 双向对齐：较短的序列在较长序列中滑动；覆盖率不足时打折 */
    fun bestAlignment(a: List<Pinyin.Syllable>, b: List<Pinyin.Syllable>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val (short, long) = if (a.size <= b.size) a to b else b to a
        val base = containScore(long, short)
        val coverage = short.size.toFloat() / long.size
        return base * (0.6f + 0.4f * coverage)
    }
}
