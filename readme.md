# 需求背景
## 一句话需求
腾讯会议软件同声传译，主要做【粤语】->【普通话】（可配置）
（该场景目前腾讯会议不支持，粤语实时转文字目前不支持）

## 实现方案:≈
1.	音频采集：
    macOS 使用 blackhole-2ch 将腾讯会议的输出音频作为输入，传递给语音识别模块。 
2. 粤语语音识别为普通话文本：
    调用腾讯云语音识别 API，将实时音频流转化为普通话文本。 
3. 字幕显示：
	OBS Studio
    免费开源的直播和录屏工具，支持通过插件将字幕实时叠加在屏幕上。

腾讯会议的音频输出 -> BlackHole -> 我们的 Java 程序捕获 -> OBS字幕展示

## 具体步骤

### 1. 配置音频捕获

在 macOS 上，我们可以使用 BlackHole 作为虚拟音频设备（这是 macOS 上最常用的虚拟音频设备之一）。
首先，安装 BlackHole：
使用 Homebrew 安装：
brew install blackhole-2ch

macOS 配置步骤：
安装 BlackHole：
配置系统音频：
打开系统偏好设置 -> 声音
在输出选项卡中选择 "BlackHole 2ch"
在输入选项卡中确认可以看到 "BlackHole 2ch"
配置腾讯会议：
在腾讯会议的音频设置中
将输出设备设置为 "BlackHole 2ch"

### 2. 使用语音识别转换为普通话文本

调用腾讯云语音识别 API 进行实时粤语语音转文本。

#### 3. 字幕实时显示

OBS Studio 是一个非常适合的工具，可以用以下方法展示字幕：
1.	安装 OBS Studio：
下载并安装 OBS Studio。
2.	添加文本来源：
在 OBS 中添加一个 文本 (GDI+) 来源，用于显示字幕。

4.	OBS 配置：
    在 OBS 中，设置文本来源读取 subtitles.txt，实时显示内容。

