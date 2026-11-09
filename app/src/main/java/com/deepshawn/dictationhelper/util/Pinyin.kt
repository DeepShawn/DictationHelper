package com.deepshawn.dictationhelper.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import java.util.concurrent.ConcurrentHashMap

/**
 * 汉字转拼音（无声调）。一个文本被拆成“音节”序列：
 * 汉字 -> 拼音音节（多音字保留全部读音），英文单词 -> 单词本身，数字 -> 数字。
 */
object Pinyin {

    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
        vCharType = HanyuPinyinVCharType.WITH_V
    }

    private val cache = ConcurrentHashMap<Char, List<String>>()

    /** 某个汉字的全部读音（去重、无声调）；非汉字返回空 */
    fun readings(c: Char): List<String> {
        if (!TextUtils.isHan(c)) return emptyList()
        return cache.getOrPut(c) {
            runCatching { PinyinHelper.toHanyuPinyinStringArray(c, format) }
                .getOrNull()
                ?.distinct()
                ?.toList()
                .orEmpty()
        }
    }

    /** 一个音节：汉字时 readings 可能有多个，英文/数字时只有一个 */
    data class Syllable(val raw: String, val readings: List<String>, val isHan: Boolean)

    fun syllables(text: String): List<Syllable> {
        val out = ArrayList<Syllable>()
        val word = StringBuilder()
        fun flush() {
            if (word.isNotEmpty()) {
                val w = word.toString().lowercase()
                out += Syllable(w, listOf(w), false)
                word.clear()
            }
        }
        for (c in text) {
            when {
                TextUtils.isHan(c) -> {
                    flush()
                    val r = readings(c)
                    out += Syllable(c.toString(), r.ifEmpty { listOf(c.toString()) }, true)
                }
                c.isLetterOrDigit() -> word.append(c)
                else -> flush()
            }
        }
        flush()
        return out
    }

    /** 首字母串，例如 “苹果 apple” -> "pga" ；多音字取第一个读音 */
    fun initials(text: String): String = syllables(text).joinToString("") {
        it.readings.first().take(1)
    }

    /** 全拼（无空格），例如 “苹果” -> "pingguo" */
    fun full(text: String): String = syllables(text).joinToString("") { it.readings.first() }

    // ---------------- 模糊音 ----------------

    private val initialList = listOf("zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w")

    private fun split(py: String): Pair<String, String> {
        val ini = initialList.firstOrNull { py.startsWith(it) } ?: ""
        return ini to py.substring(ini.length)
    }

    private val fuzzyInitials = mapOf(
        "zh" to "z", "ch" to "c", "sh" to "s",
        "n" to "l", "f" to "h", "r" to "l",
    ).let { m -> m + m.entries.associate { it.value to it.key } }

    private val fuzzyFinals = mapOf(
        "ang" to "an", "eng" to "en", "ing" to "in", "iang" to "ian", "uang" to "uan",
    ).let { m -> m + m.entries.associate { it.value to it.key } }

    /** 两个拼音音节的相似度：完全相同 1，模糊音 0.8，其它按编辑距离 */
    fun readingSimilarity(a: String, b: String): Float {
        if (a == b) return 1f
        val (ia, fa) = split(a)
        val (ib, fb) = split(b)
        val iniOk = ia == ib || fuzzyInitials[ia] == ib
        val finOk = fa == fb || fuzzyFinals[fa] == fb
        if (iniOk && finOk) return 0.8f
        return TextUtils.similarity(a, b) * 0.7f
    }

    fun syllableSimilarity(a: Syllable, b: Syllable): Float {
        if (!a.isHan && !b.isHan) return TextUtils.similarity(a.raw, b.raw)
        if (a.raw == b.raw) return 1f
        var best = 0f
        for (x in a.readings) for (y in b.readings) {
            val s = readingSimilarity(x, y)
            if (s > best) best = s
        }
        return best
    }
}
