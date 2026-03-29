# Logger 使用说明

## 简介

`Logger` 是一个轻量级日志工具，只在 debug 模式下输出日志，release 模式下完全静默。

## 特性

- **性能优化**：使用 inline + lambda，release 模式下字符串拼接不会执行
- **自动控制**：根据 `BuildConfig.DEBUG` 自动开关
- **简洁 API**：支持 d/i/w/e 四种日志级别

## 使用方法

```kotlin
// 在 Application 中初始化
Logger.init(BuildConfig.DEBUG)

// 使用 lambda 避免不必要的字符串拼接
Logger.d(TAG) { "调试信息: $value" }
Logger.i(TAG) { "普通信息" }
Logger.w(TAG) { "警告信息" }
Logger.e(TAG, exception) { "错误信息" }
```
