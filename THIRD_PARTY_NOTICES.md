# 第三方来源与修改说明

本文件与 README 的引用表须随源码二次分发保留。

## 当前直接使用

1. **AndroidLiquidGlass 1.0.6 / Backdrop — Kyant，Apache-2.0**。源码位于 `core/glass/src/main/kotlin/com/kyant/backdrop/catalog/`。保留作者包名、版权及修改头；本项目修改形状适配、天气染色、共享光学参数、缩小导航比例、按压窗口、拖动交接及减少动态效果。`LiquidLensEffect.kt` 基于同版本 Lens / Shaders 的距离场、折射及七点色散算法，增加连续色散参数。完整来源与逐项修改见 `third_party/android-liquid-glass/README.md`。
2. **lunar-java 1.7.7 — 6tail，MIT**。计算农历、节日、节气；应用中的运势与氛围文案不是上游库提供的预测服务。见 `third_party/lunar-java/`。
3. **Feather — Cole Bemis，MIT**。部分图标及其衍生线稿；见 `third_party/feather/LICENSE`。
4. **AndroidX / Kotlin / Coroutines / Hilt / Retrofit / Gson** 等 Gradle 依赖分别遵守其上游许可证。请在发行时包含适用的依赖声明。

## 历史来源（当前不参与书本渲染）

- `oleksandrbalan/pagecurl` v1.5.1，Apache-2.0。
- `harism/android-pagecurl` CurlMesh / CurlPage，Apache-2.0。

历史说明及许可证保留在 `third_party/pagecurl/` 与 `third_party/harism-pagecurl/`；旧源码未作为当前渲染代码重新发布。当前书本使用项目内 `feature/diary/src/main/cpp/VulkanBook.cpp` 和着色器。

## 设计参考与素材边界

Paper（FiftyThree / WeTransfer）的书架、开合、翻页及总览交互，以及 Apple iOS 备忘录的工具操作和系统材质，用于视觉研究。它们不是开源依赖，本项目不包含其产品源码、字体、参考视频或原图，也不主张其品牌归属。不能将“参考设计”写成“使用了其开源源码”。未核实再分发权的用户素材以自制占位图替代，详见 ASSETS.md。

## 天气数据

Open-Meteo：https://open-meteo.com/ 。天气模块展示 Open-Meteo 署名。使用、再分发数据时应遵守其数据许可及服务条款。本项目不提供或上传定位、日程、日记数据库密钥。
