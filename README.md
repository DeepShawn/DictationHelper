# 听写助手 DictationHelper

一款 **液态玻璃（Liquid Glass）+ Miuix（HyperOS 风格）** 的 Android 听写 / 背词辅助应用，开源协议 **Apache-2.0**。

## 功能

| 功能 | 说明 |
| --- | --- |
| 📒 词表 | 记录中文 / 英文的**词汇、短语、句子**，自动识别语言和类型，可填写释义与备注（音标、拼音、例句） |
| 📷 拍照识别导入 | 拍照或从相册选图，调用 **DeepSeek 视觉模型** (`deepseek-v4-flash-vision-exp`) 提取图片中的词表，勾选后导入 |
| ✨ AI 自动补全 | 编辑词条时一键补全释义、音标/拼音和类型（`deepseek-v4-flash`） |
| 🗂 JSON 导入 / 导出 | 支持文件、剪贴板导入，兼容多种 JSON 结构（见下文） |
| 👂 环境聆听 | 持续聆听周围声音（老师报听写、视频、广播…），自动在词表中找出对应的词条，支持中文 / 英文；说出英文单词的**中文释义**也能命中 |
| ⌨️ 首字母检索 | 自带虚拟字母键盘，直接点击输入首字母即可查看：英文按前缀 / 单词首字母，中文按**拼音首字母** / 全拼 |
| 🎙 语音模糊检索 | 语音输入后按拼音音节对齐打分，支持**同音字**、**模糊音**（z/zh、c/ch、s/sh、n/l、f/h、an/ang、en/eng、in/ing…）和少量识别错误 |
| 🎨 多种主题 | 浅色 / 深色 / 跟随系统 × 8 种配色（经典蓝、壁纸取色 Monet、樱花粉、薄荷绿、琥珀橙、葡萄紫、深海青、水墨灰），液态玻璃效果可开关 |

## DeepSeek API 密钥

在「设置 → DeepSeek AI」中填写 [DeepSeek 开放平台](https://platform.deepseek.com/) 的 API 密钥即可，界面上只有密钥这一个输入项。
密钥只保存在本机（应用私有 SharedPreferences），只会发送到 `api.deepseek.com`。
接口地址与模型名定义在 [`DeepSeekClient.kt`](app/src/main/java/com/deepshawn/dictationhelper/ai/DeepSeekClient.kt)，需要时可在那里修改。

## JSON 格式

导出格式：

```json
{
  "app": "DictationHelper",
  "version": 1,
  "words": [
    { "id": 1, "text": "apple", "translation": "苹果", "note": "/ˈæp.əl/", "type": "word", "lang": "en", "createdAt": 0 }
  ]
}
```

导入时还兼容：

```json
["apple", "look forward to", "环境保护"]
```

```json
[{ "word": "apple", "meaning": "苹果", "type": "word" }, { "text": "How are you?", "translation": "你好吗？" }]
```

```json
{ "apple": "苹果", "banana": "香蕉" }
```

`type` 可取 `word / phrase / sentence`（或 `词汇 / 短语 / 句子`），省略时自动判断。与已有词条文本相同的项会被跳过。

## 语音识别说明

聆听与语音输入使用系统的 `SpeechRecognizer`。设备上需要有可用的语音识别服务（例如 Google 语音服务，或厂商自带的语音助手/识别引擎）；没有时应用会给出提示。
连续聆听是通过识别结束后自动重启实现的，部分系统在每次重启时会有提示音。

## 构建

- Android Studio（支持 AGP 9.2）/ JDK 17+
- `./gradlew assembleDebug`

主要依赖与版本：Kotlin 2.4.20、Android Gradle Plugin 9.2.0、Gradle 9.4.1、Jetpack Compose 1.12、
[Miuix](https://github.com/compose-miuix-ui/miuix) 0.9.4、[Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 2.0.1（液态玻璃）、pinyin4j 2.5.1。

推送到 GitHub 后，Actions 会自动构建 debug 与 release APK（release 默认使用 debug 签名，正式发布请自行配置签名）。

液态玻璃效果：Android 12+ 有实时模糊，Android 13+ 有透镜折射；更低版本自动降级为半透明磨砂效果。

## 项目结构

```
app/src/main/java/com/deepshawn/dictationhelper/
├── ai/DeepSeekClient.kt        DeepSeek 视觉识别与文本补全
├── data/                       词条模型、JSON 存储、设置
├── speech/SpeechEngine.kt      单次 / 连续语音识别
├── util/                       拼音、模糊匹配算法
└── ui/
    ├── glass/Glass.kt          液态玻璃 Modifier 与背景
    ├── components/             玻璃导航栏、虚拟键盘、词条卡片、各类面板
    └── screens/                词表 / 聆听 / 检索 / 设置
```

## License

```
Copyright 2026 DictationHelper contributors

Licensed under the Apache License, Version 2.0
```

详见 [LICENSE](LICENSE)。
