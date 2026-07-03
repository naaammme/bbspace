<p align="center">
  <img src="images/banner.jpg" alt="BBSpace banner" width="920" />
</p>

<h2 align="center">哔哔空间</h2>

<p align="center">
  <a href="https://github.com/naaammme/bbspace/stargazers">
    <img src="https://img.shields.io/github/stars/naaammme/bbspace?style=flat" alt="stars" />
  </a>
  <a href="https://github.com/naaammme/bbspace/releases">
    <img src="https://img.shields.io/github/v/release/naaammme/bbspace?style=flat" alt="release" />
  </a>
  <a href="https://github.com/naaammme/bbspace/releases">
    <img src="https://img.shields.io/github/downloads/naaammme/bbspace/total?style=flat" alt="downloads" />
  </a>
  <a href="https://github.com/naaammme/bbspace/releases/latest">
    <img src="https://img.shields.io/github/downloads/naaammme/bbspace/latest/total?style=flat" alt="latest release downloads" />
  </a>
  <a href="https://developer.android.com/about/versions/nougat">
    <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat&logo=android&logoColor=white" alt="android 7+" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/github/license/naaammme/bbspace?style=flat" alt="license" />
  </a>
</p>

<p align="center">
  一个强大的第三方哔哩哔哩 Android 客户端。持续施工中...
</p>

<p align="center">
  <a href="https://github.com/naaammme/bbspace/releases"><strong>下载 APK</strong></a>
  ·
  <a href="https://github.com/naaammme/bbspace/issues"><strong>反馈问题</strong></a>
  ·
  <a href="https://t.me/ourbbspace"><strong>Telegram</strong></a>
</p>


## 截图

<p align="center">
  <img src="images/shot.png" alt="BBSpace screenshot home" width="240" />
  <img src="images/shot2.png" alt="BBSpace screenshot player" width="240" />
</p>
<p align="center">
  <img src="images/shot3.png" alt="BBSpace screenshot tablet" width="640" />
</p>

## 当前功能

### 账号

- [x] 扫码登录
- [x] 短信登录
- [x] 多账号管理

### 内容

- [x] 首页推荐视频流
- [x] 视频搜索
- [x] 视频详情页
- [x] 评论区
- [x] 动态
- [x] 个人空间
- [x] 稍后再看

### 播放

- [x] 视频播放
- [x] 弹幕
- [x] 听视频
- [x] 直播
- [x] 应用内小窗
- [x] 应用内缓存
- [x] 视频音频一键下载导出

### 社交

- [x] 即时消息

### 其他

- [x] 发评反诈
- [x] 评论弹幕记录
- [x] 评论历史查询
- [x] 用户关系查询

### 正在补全

- [ ] 字幕
- [ ] 分类搜索
- [ ] 点赞、投币、收藏
- [ ] 空降助手
- [ ] 其他细节完善

## 技术实现

- `Kotlin` + `Jetpack Compose` + `Material 3`
- `Media3` 负责播放，`DanmakuFlameMaster` 负责弹幕渲染
- `Room` 和 `DataStore` 管理本地数据
- `OkHttp` `Retrofit` `Protobuf` 负责网络和协议层
- 自行实现 B 站 gRPC over HTTP/1.1 请求封装

## 下载与运行

- 用户安装：前往 [Releases](https://github.com/naaammme/bbspace/releases) 下载最新 APK
- 系统要求：Android 7.0 及以上
- 开发环境：JDK 17、Android SDK 36

## 说明

BBSpace 与哔哩哔哩官方无关。项目主要用于学习、研究和 Android 原生界面实现练习。仓库内涉及的接口信息均来自公开资料整理，仅用于技术交流，不包含破解和付费内容分发。

如果这个项目帮你省下了找实现细节和踩坑的时间，欢迎点个 Star。

## 致谢

- [bilibili-API-collect](https://github.com/SocialSisterYi/bilibili-API-collect)
- [PiliPlus](https://github.com/bggRGjQaUbCoE/PiliPlus)
- [androidx/media](https://github.com/androidx/media)
- [DanmakuFlameMaster](https://github.com/naaammme/DanmakuFlameMaster)

## License

[GPL-3.0](LICENSE)


## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=naaammme/bbspace&type=Date)](https://star-history.com/#naaammme/bbspace&Date)
