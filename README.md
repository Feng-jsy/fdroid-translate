> **本仓库是 F-Droid 官方客户端的翻译增强版**，基于上游 `5809dc3`（F-Droid 2.0.3）。
> 上游项目：[gitlab.com/fdroid/fdroidclient](https://gitlab.com/fdroid/fdroidclient)，GPLv3+。
> 本仓库只增加了翻译相关功能，其余代码与上游一致。以下内容为上游 README 原文。

---

# 翻译增强版说明

## 新增了什么

| 功能 | 说明 |
|---|---|
| 一键翻译 | 应用详情页的**一句话简介**、**应用介绍**、「新版本变化」，以及「我的应用」里更新说明，下方都有「翻译 / 显示原文」按钮 |
| 三引擎自动降级 | 腾讯交互翻译 → Google 翻译 → MyMemory，前一个不通自动换下一个，用户无感知 |
| 翻译设置 | 设置页新增「翻译」分类：总开关、进入页面自动翻译、首选引擎（自动 / 腾讯 / Google / MyMemory） |
| 译文缓存 | 同一段文字只请求一次，来回切换原文译文不再联网 |
| 中文文案 | 新增界面文案均提供简体、繁体中文 |

## 下载安装

从本仓库的 Releases 页面下载 `fdroid-translate-*.apk`。

安装前**必须先卸载官方版 F-Droid**：本包与官方版包名相同（`org.fdroid.fdroid`）但签名不同，
安卓不允许互相覆盖。卸载官方版不影响已安装的应用，只会丢失 F-Droid 自身的仓库列表和更新记录。
后续升级本仓库的新版本时签名相同，可直接覆盖安装。

## 从源码编译

环境要求：

- JDK 17 或更高
- Android SDK，需装 `platforms;android-37.2-beta3`（上游 `compileSdk = 37`，目前仅 beta 渠道提供）
- `ANDROID_HOME` 指向 SDK，或在根目录写 `local.properties`：`sdk.dir=/path/to/android-sdk`

国内网络说明：本仓库已把 Maven 仓库换成阿里云镜像、Gradle 下载换成腾讯镜像，
并补上了 macOS 版 `aapt2` 的校验哈希（上游 `gradle/verification-metadata.xml` 只记录了 Linux 版），
否则在国内直连会超时或校验失败。

```bash
# 方式一：直接出 APK
./gradlew :app:assembleFullDefaultRelease

# 方式二：归档脚本（同时把 SHA-256 记录到 builds/MANIFEST.md）
./build-apk.sh release     # 默认，R8 压缩，约 11 MB
./build-apk.sh debug       # 调试版，约 37 MB，滑动明显更卡，仅用于抓日志
```

### 签名

`app/build.gradle.kts` 读取根目录的 `keystore.properties` 来签名 release 包；
该文件不存在时 release 包不签名（与上游一致，便于可复现构建）。自己编译时：

```bash
cp keystore.properties.example keystore.properties   # 再填入你的 keystore 路径和密码
```

`keystore.properties` 与 `*.jks` 均已在 `.gitignore` 中。

## 翻译实现

- 界面组件：`app/src/main/kotlin/org/fdroid/ui/utils/TranslationUi.kt`
  （`TranslationState` + `rememberTranslationState` + 按钮，四个页面共用）
- 引擎实现：`app/src/main/kotlin/org/fdroid/ui/details/DescriptionTranslator.kt`，
  按段落切分长文本，依次尝试各引擎，成功后写内存缓存
- 目标语言跟随系统语言；文本本身就是系统语言时不显示翻译按钮
- 待翻译文本会发送到所选翻译服务（腾讯 / Google / MyMemory），不发送其他信息

**注意**：腾讯交互翻译调用的是网页端公开接口，不是正式开放 API，可能随时变更或限流；
Google 翻译在国内通常无法直连；MyMemory 匿名额度约 5000 字符/天。三者互为兜底。

## 已知限制

- 翻译结果为纯文本，会丢失原介绍中的链接、列表、粗体等 HTML 格式
- 应用截图、权限说明仍为英文，暂未处理

---

# F-Droid Client

[![build status](https://gitlab.com/fdroid/fdroidclient/badges/master/pipeline.svg)](https://gitlab.com/fdroid/fdroidclient/-/jobs)
[![Translation status](https://hosted.weblate.org/widgets/f-droid/-/svg-badge.svg)](https://hosted.weblate.org/engage/f-droid/)

Client for [F-Droid](https://f-droid.org), the Free Software repository system
for Android.

## Building with Gradle

    ./gradlew assembleRelease

## Direct download

You can [download the application](https://f-droid.org/F-Droid.apk) directly
from our site or [browse it in the repo](https://f-droid.org/app/org.fdroid.fdroid).

## Coding style

This project uses [ktfmt](https://github.com/facebook/ktfmt) and enforces it via CI.
You can run the following to auto-format your changes:

    ./gradlew ktfmtFormat

## Libraries

Core F-Droid functionality is split into re-usable libraries
to make using F-Droid technology in your own projects as easy as possible.

[More information about libraries](libs/README.md)

## Contributing

See our [Contributing doc](CONTRIBUTING.md) for information on how to report
issues, translate the app into your language or help with development.

## IRC

We are on `#fdroid` and `#fdroid-dev` on OFTC. We hold weekly dev meetings
on `#fdroid-dev` on Thursdays at 11:30h UTC, which usually last half an hour.

## FAQ

* Why does F-Droid require "Unknown Sources" to install apps by default?

Because a regular Android app cannot act as a package manager on its
own. To do so, it would require system privileges (see below), similar
to what Google Play does.

* Can I avoid enabling "Unknown Sources" by installing F-Droid as a
  privileged system app?

This used to be the case, but no longer is. Now the [Privileged
Extension](https://gitlab.com/fdroid/privileged-extension) is the one that should be placed in
the system. It can be bundled with a ROM or installed via a zip.

## License

This program is Free Software: You can use, study share and improve it at your
will. Specifically you can redistribute and/or modify it under the terms of the
[GNU General Public License](https://www.gnu.org/licenses/gpl.html) as
published by the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Some icons are made by [Picol](http://www.flaticon.com/authors/picol),
[Icomoon](http://www.flaticon.com/authors/icomoon) or
[Dave Gandy](http://www.flaticon.com/authors/dave-gandy) from
[Flaticon](http://www.flaticon.com) or by Google and are licensed by
[Creative Commons BY 3.0](https://creativecommons.org/licenses/by/3.0/).

Other icons are from the
[Material Design Icon set](https://github.com/google/material-design-icons)
released under an
[Attribution 4.0 International license](https://creativecommons.org/licenses/by/4.0/).


## Translation

Everything can be translated.  See
[Translation and Localization](https://f-droid.org/docs/Translation_and_Localization)
for more info.
[![translation status](https://hosted.weblate.org/widgets/f-droid/-/f-droid/multi-auto.svg)](https://hosted.weblate.org/engage/f-droid/?utm_source=widget)
