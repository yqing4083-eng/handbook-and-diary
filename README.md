# 手账本与日记本 · Doki

**让天气成为氛围，让纸页收藏生活。**

原生 Android 手账与日记应用，以 Kotlin、Jetpack Compose 与 Vulkan 构建。应用安装后的名称仍为 **Doki**。本开源版本移除了智能助手，主导航只保留 **Today · 日历 · 日记 · Me**。

> 参赛说明：本项目含开源依赖与改编代码，并非所有功能都从零原创。液态玻璃的直接依赖、改编来源及历史翻页库见下方与 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。请在比赛介绍和二次分发中保留来源说明。

## 功能

- **Today**：日期、时间、定位天气、天气氛围与本地文案。
- **日历**：公历、农历、节气、节日及授权后的系统日程。
- **日记**：Vulkan 书架、书本开合、折页与纸页总览；文字、手绘、贴纸、图片与纸张背景。
- **手账**：图片纸页及图像编辑，独立于日记的横线纸模式。
- **Me**：本地个人资料、动态与自定义背景。
- **液态玻璃**：底部导航、按钮、数字、折射、色散与设备姿态随动高光。
- **本地数据**：Room / DataStore；不再包含聊天 API、聊天数据库、CloudBase 或 Vercel 客户端。

## 必须说明的引用与借鉴

| 项目 / 产品 | 实际用途 | 关系与许可证 |
| --- | --- | --- |
| [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6) | 真实玻璃折射、模糊、边缘高光及液态交互 | **直接使用** `io.github.kyant0:backdrop:1.0.6`，并改编其 catalog 的 LiquidBottomTabs / LiquidBottomTab / LiquidButton / DampedDragAnimation / InteractiveHighlight / DragGestureInspector；Apache-2.0，保留源码头及许可证 |
| [Kyant0 Shapes](https://github.com/Kyant0) | Backdrop 形状依赖 | **直接依赖** `io.github.kyant0:shapes:1.2.0`；依赖发布物保留原许可 |
| [6tail/lunar-java](https://github.com/6tail/lunar-java/tree/v1.7.7) | 农历、节日和节气计算 | **直接依赖** `cn.6tail:lunar:1.7.7`；MIT |
| [Feather Icons](https://github.com/feathericons/feather) | 部分线性图标 | 图标使用 / 改编；MIT，Copyright Cole Bemis |
| [Open-Meteo](https://open-meteo.com/) | 实时天气数据 | 网络数据服务，不是本项目原创天气数据；使用时遵守服务条款及数据署名要求 |
| [oleksandrbalan/pagecurl](https://github.com/oleksandrbalan/pagecurl/tree/v1.5.1) | 早期 Compose 翻页方案 | **历史依赖，现已停用**；Apache-2.0，当前 Vulkan 渲染不依赖它 |
| [harism/android-pagecurl](https://github.com/harism/android-pagecurl) | 早期 CurlMesh / CurlPage 方案 | **历史导入，现已停用**；Apache-2.0，当前不打包旧 OpenGL curl 源码 |
| Paper（FiftyThree / WeTransfer） | 书本开合、折页与总览的视觉交互研究 | **设计参考**；未包含其源码、字体、视频或产品素材，不宣称获得其授权或与其有关联 |
| Apple iOS 备忘录、系统手写欢迎动画 | 笔工具交互、材质和连续圆角的设计研究 | **设计参考**；不包含 Apple 字体或系统代码。公开笔工具位图为自制占位图 |

还使用 AndroidX（Compose、Room、DataStore、Navigation、Lifecycle）、Kotlin Coroutines、Dagger Hilt、Retrofit / Gson 等通用库，具体版本与坐标见 `gradle/libs.versions.toml` 和各模块 `build.gradle.kts`。这些库及其传递依赖分别适用上游许可证。

## 构建

- Android Studio、JDK 21、Android SDK 36（最低 Android 13 / API 33）
- NDK `27.2.12479018`、CMake `3.22.1`
- Gradle Wrapper `8.13`，AGP `8.13.2`，Kotlin `2.3.10`
- 在 `local.properties` 设置本机 `sdk.dir`，不要提交该文件。

```sh
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest :domain:test :feature:diary:testDebugUnitTest
```

Windows 可使用 `gradlew.bat`。若 NDK 不支持本机含中文路径，请先将项目放入英文路径，例如 `D:\Projects\HandbookAndDiary`。APK 位于 `app/build/outputs/apk/release/`。当前 release 配置使用开发签名方便测试，正式商店发行应自行配置签名并妥善保管密钥。

没有天气 API 密钥要求。定位与日程读取均需在手机上授权。模拟器可验证功能，不能代表实体手机的持续帧率。

## 目录

```text
app/                  应用入口、依赖注入、四栏导航
core/glass/           玻璃渲染集成及上游 catalog 改编
core/shape/           连续曲率形状
core/theme/           天气、时间及节日主题
core/ui/              排版、图标和共享组件
core/weather/         天气背景与粒子
feature/today/        Today
feature/calendar/     日历
feature/diary/        手账、日记、编辑器与 C++ Vulkan 书本
feature/me/           个人空间与设置
data/                 定位、日程、天气与本地持久化
domain/               数据模型、几何与接口
third_party/          来源、修改说明及许可文本
```

## 素材与安装包差异

用户提供的封面、贴纸、纸纹、仿真笔与图标未提供可核验的再分发许可，因此没有上传原图，公开源码替换为程序生成的 MIT 占位图。**源码可构建，视觉素材与本地完整版安装包不同。** 详见 [ASSETS.md](ASSETS.md)。不上传个人日记、聊天记录、部署文件、API 密钥、调试日志或用户参考视频。

## 许可

本项目自有代码采用 [Apache-2.0](LICENSE)。上游改编文件保留其原版权和许可；第三方依赖、历史来源与素材不因本项目许可而改变原许可。项目名称为「手账本与日记本」，应用显示名称为 Doki。
