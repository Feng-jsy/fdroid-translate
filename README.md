# F-Droid 翻译增强版

基于 F-Droid 官方客户端 2.0.3（上游 `5809dc3`）的增强版：给英文的应用简介、应用介绍、更新说明加上一键翻译。
只增加了翻译相关功能，其余与上游一致。GPLv3+，上游项目 [gitlab.com/fdroid/fdroidclient](https://gitlab.com/fdroid/fdroidclient)。

包名 `org.fdroid.translate`，**可以和官方 F-Droid 同时安装**。

## 功能

| 功能 | 说明 |
|---|---|
| 一键翻译 | 详情页的简介、应用介绍、新版本变化，以及「我的应用」里的更新说明，下方都有「翻译 / 显示原文」按钮 |
| 三引擎降级 | 腾讯交互翻译 → Google 翻译 → MyMemory，前一个不通自动换下一个 |
| 翻译设置 | 设置页新增「翻译」分类：总开关、自动翻译、首选引擎 |
| 译文缓存 | 同一段文字只请求一次，来回切换不重复联网 |
| 中文文案 | 新增的界面文案都有简体和繁体 |

## 下载安装

从 [Releases](../../releases) 下载。有两个包，功能完全一样，只差原生库：

| 文件 | 说明 |
|---|---|
| `fdroid-translate-v1.0.0-release.apk` | 通用包，含全部 ABI，不确定选哪个就用这个 |
| `fdroid-translate-v1.0.0-arm64-v8a-release.apk` | 纯 64 位，只含 arm64-v8a 原生库 |

支持 Android 7.0 及以上。

装不上时：

- 「解析包时出现问题」→ 文件没下完整，重新下载并核对 Release 里的 SHA-256
- 「与您的手机不兼容」→ 换成上面那个通用包

## 编译

需要 JDK 17+，Android SDK 要装 `platforms;android-37.2-beta3`（上游 `compileSdk = 37`，目前只有 beta 渠道提供）。
SDK 路径写进 `local.properties` 的 `sdk.dir`，或设 `ANDROID_HOME`。

国内网络已处理：Gradle 走腾讯镜像、Maven 走阿里云，并补了 macOS 版 `aapt2` 的校验哈希（上游只记了 Linux 版）。

```bash
./gradlew :app:assembleFullDefaultRelease   # 直接出包

./build-apk.sh release                      # 出包并归档到 builds/，记录 SHA-256
./build-apk.sh release arm64-v8a            # 只打 64 位
./build-apk.sh debug                        # 调试版，37 MB，滑动明显更卡，仅用于抓日志
```

签名读根目录的 `keystore.properties`，文件不存在时 release 不签名（与上游一致）。

```bash
cp keystore.properties.example keystore.properties   # 再填入 keystore 路径和密码
```

`keystore.properties` 和 `*.jks` 已在 `.gitignore` 中。

## 实现位置

- 界面组件：`app/src/main/kotlin/org/fdroid/ui/utils/TranslationUi.kt`
- 翻译引擎：`app/src/main/kotlin/org/fdroid/ui/details/DescriptionTranslator.kt`

译文跟随系统语言，原文已是系统语言时不显示翻译按钮。待翻译文字会发给所选服务，不发送其他信息。

## 已知限制

- 译文是纯文本，会丢失原介绍里的链接、列表、粗体等格式
- 应用截图、权限说明仍是英文
- 腾讯走的是网页端公开接口，可能变更或限流；Google 国内通常不通；MyMemory 匿名额度约 5000 字符/天
