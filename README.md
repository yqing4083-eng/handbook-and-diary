# 手账本与日记本 · Doki

一款原生 Android 手账与日记应用，将天气氛围、液态玻璃和立体书本结合，让记录生活更自然。

包含 **Today、日历、日记、Me** 四个板块，支持天气查看、日程管理、文字记录、手绘、贴纸、图片与个人空间。使用 Kotlin、Jetpack Compose 和 Vulkan 开发。

## 引用与借鉴

| 来源 | 用途 |
| --- | --- |
| [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6) | 直接使用玻璃渲染，并改编液态按钮、底栏及拖动交互代码。 |
| [Kyant0 Shapes](https://github.com/Kyant0) | 玻璃组件的形状依赖。 |
| [lunar-java](https://github.com/6tail/lunar-java) | 农历、节日和节气计算。 |
| [Feather Icons](https://github.com/feathericons/feather) | 部分线性图标及其改编。 |
| [Open-Meteo](https://open-meteo.com/) | 提供天气数据。 |
| [PageCurl](https://github.com/oleksandrbalan/pagecurl)、[android-pagecurl](https://github.com/harism/android-pagecurl) | 早期翻页实现，当前已停用，现使用项目内的 Vulkan 书本渲染。 |
| Paper（FiftyThree / WeTransfer） | 参考书架、书本开合、翻页与纸页总览的交互设计。 |
| Apple iOS 备忘录与系统动画 | 参考笔工具交互、连续圆角及手写欢迎动画设计。 |

Paper 与 iOS 仅为设计参考，未使用其产品源码或字体。

## 说明

- 自有代码采用 [Apache-2.0](LICENSE)，第三方代码保留原许可，详见 [引用与许可说明](THIRD_PARTY_NOTICES.md)。
- 公开版本使用占位素材，详见 [素材说明](ASSETS.md)。
- 可在 [Releases](https://github.com/yqing4083-eng/handbook-and-diary/releases) 下载应用。
