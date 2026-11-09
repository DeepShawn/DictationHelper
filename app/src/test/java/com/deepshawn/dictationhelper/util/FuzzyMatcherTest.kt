package com.deepshawn.dictationhelper.util

import com.deepshawn.dictationhelper.data.EntryType
import com.deepshawn.dictationhelper.data.WordEntry
import com.deepshawn.dictationhelper.data.WordRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private val idx = FuzzyMatcher.index(
        listOf(
            "apple" to "苹果",
            "苹果" to "apple",
            "环境保护" to "environmental protection",
            "look forward to" to "期待",
            "四十" to "forty",
            "知识" to "knowledge",
            "How are you?" to "你好吗",
        ).mapIndexed { i, (t, tr) ->
            WordEntry(i.toLong(), t, tr, type = TextUtils.detectType(t), lang = TextUtils.detectLang(t))
        },
    )

    private fun texts(ms: List<Match>) = ms.map { it.entry.text }

    @Test fun letterSearchByPinyinInitials() {
        assertEquals("苹果", FuzzyMatcher.letterSearch("pg", idx).first().entry.text)
        assertEquals("环境保护", FuzzyMatcher.letterSearch("hjbh", idx).first().entry.text)
    }

    @Test fun letterSearchByEnglishWordInitials() {
        assertEquals("look forward to", FuzzyMatcher.letterSearch("lft", idx).first().entry.text)
        assertEquals("apple", FuzzyMatcher.letterSearch("app", idx).first().entry.text)
    }

    @Test fun fuzzyVoiceHandlesFuzzyFinalsAndTypos() {
        assertEquals("环境保护", FuzzyMatcher.fuzzySearch("环金保户", idx).first().entry.text)
        assertEquals("apple", FuzzyMatcher.fuzzySearch("apples", idx).first().entry.text)
        assertEquals("知识", FuzzyMatcher.fuzzySearch("资私", idx).first().entry.text)
    }

    @Test fun fuzzyVoiceMatchesTranslation() {
        val m = FuzzyMatcher.fuzzySearch("期待", idx).first()
        assertEquals("look forward to", m.entry.text)
        assertTrue(m.viaTranslation)
    }

    @Test fun detectsEntriesInTranscript() {
        val hits = texts(FuzzyMatcher.detectInTranscript("下面听写第一个词环境保护然后是苹果", idx))
        assertTrue("环境保护" in hits)
        assertTrue("苹果" in hits)
        assertTrue("look forward to" in texts(FuzzyMatcher.detectInTranscript("next one, look forward to", idx)))
        assertTrue(FuzzyMatcher.detectInTranscript("我们要保护自然环境", idx).isEmpty())
    }

    @Test fun detectsType() {
        assertEquals(EntryType.WORD, TextUtils.detectType("apple"))
        assertEquals(EntryType.PHRASE, TextUtils.detectType("look forward to"))
        assertEquals(EntryType.SENTENCE, TextUtils.detectType("How are you?"))
        assertEquals(EntryType.SENTENCE, TextUtils.detectType("书山有路勤为径，学海无涯苦作舟。"))
    }

    @Test fun parsesVariousJsonShapes() {
        assertEquals(2, WordRepository.parseDrafts("""["apple","苹果"]""").size)
        val objs = WordRepository.parseDrafts("""{"items":[{"word":"apple","meaning":"苹果","type":"word"}]}""")
        assertEquals("苹果", objs.single().translation)
        assertEquals(EntryType.WORD, objs.single().type)
        assertEquals("香蕉", WordRepository.parseDrafts("""{"banana":"香蕉"}""").single().translation)
        assertEquals(1, WordRepository.parseDrafts("```json\n[\"x\"]\n```").size)
    }
}
