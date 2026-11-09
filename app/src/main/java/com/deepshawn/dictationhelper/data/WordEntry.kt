package com.deepshawn.dictationhelper.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 词条类型：单词 / 短语 / 句子 */
@Serializable
enum class EntryType(val label: String) {
    @SerialName("word") WORD("词汇"),
    @SerialName("phrase") PHRASE("短语"),
    @SerialName("sentence") SENTENCE("句子"),
}

/** 主要语言 */
@Serializable
enum class Lang(val label: String) {
    @SerialName("zh") ZH("中文"),
    @SerialName("en") EN("英文"),
}

@Serializable
data class WordEntry(
    val id: Long,
    /** 词条原文（中文或英文） */
    val text: String,
    /** 释义 / 翻译 */
    val translation: String = "",
    /** 备注，例如音标、例句 */
    val note: String = "",
    val type: EntryType = EntryType.WORD,
    val lang: Lang = Lang.EN,
    val createdAt: Long = System.currentTimeMillis(),
)
