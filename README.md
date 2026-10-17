## 需要安装对应的应用，端口设置为8040，开放8000-9000的端口

https://github.com/duoshoumiao/pcrjjc2

https://github.com/duoshoumiao/PPPPPP

https://github.com/duoshoumiao/priconne
# 个人库，不建议商用，有问题不要找我。没有自己的bot下载也用不了，高度搭配使用的

# 竞技场查询 Android 应用

（HoshinoBot QQ 机器人插件）的核心功能移植为独立 Android 应用。

## 功能

- 支持 B 服、渠道服、台服竞技场排名查询
- 绑定多个游戏 UID，实时监控排名变动
- 后台定期轮询排名，排名变化时推送 Android 本地通知
- 查看击剑（排名变动）历史记录
- 查看详细玩家资料
- 支持排名变动通知、排名上升通知、上线提醒

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **网络**: OkHttp
- **数据库**: Room
- **依赖注入**: Hilt
- **异步**: Kotlin Coroutines
- **后台任务**: WorkManager + Foreground Service
- **序列化**: msgpack-java
- **加密**: AES-CBC + RSA (与游戏服务器通信)

## 项目结构

```
app/src/main/java/com/pcrjjc/app/  
├── PcrJjcApp.kt               # Application 类 (Hilt, 通知渠道)  
├── MainActivity.kt            # 主 Activity (Compose)  
├── ScreenCaptureActivity.kt   # 截图识别入口 Activity  
├── data/  
│   ├── local/  
│   │   ├── entity/            # Room 实体  
│   │   ├── dao/               # Room DAO  
│   │   ├── AppDatabase.kt     # Room 数据库  
│   │   ├── RankCacheDao.kt    # 排名缓存 DAO  
│   │   └── SettingsDataStore.kt # DataStore 设置存储  
│   ├── model/                 # 数据模型 (ActivityClassifier, FortnightlyData)  
│   ├── repository/            # 仓库层 (FortnightlyRepository)  
│   └── remote/  
│       ├── CryptoUtils.kt     # AES-CBC 加密 + msgpack 序列化  
│       ├── RsaCrypto.kt       # RSA 公钥加密  
│       ├── PcrClient.kt       # CN 服 API 客户端  
│       ├── TwPcrClient.kt     # TW 服 API 客户端  
│       ├── RemotePcrClient.kt # 远程代理 API 客户端  
│       ├── RoomClient.kt      # 房间 API 客户端  
│       ├── BigFunClient.kt    # BigFun API 客户端  
│       ├── AppPushClient.kt   # 推送客户端  
│       ├── BiliAuth.kt        # B 站 SDK 登录  
│       ├── ApiException.kt    # API 异常  
│       └── CaptchaRequiredException.kt # 验证码异常  
├── di/  
│   └── AppModule.kt           # Hilt 依赖注入  
├── domain/  
│   ├── QueryEngine.kt         # 查询引擎  
│   ├── RankMonitor.kt         # 排名监控 + 通知  
│   ├── ClanBattleEngine.kt    # 公会战引擎  
│   ├── ManualBattleEngine.kt  # 手动出刀引擎  
│   ├── ExEquipEngine.kt       # EX装备引擎  
│   ├── SupportEngine.kt       # 支援引擎  
│   ├── LabyrinthDb.kt         # 地下城数据库  
│   ├── LabyrinthRouteFinder.kt # 地下城路线搜索  
│   ├── LabyrinthViewModel.kt  # 地下城 ViewModel  
│   ├── ClientManager.kt       # 客户端管理  
│   ├── CaptchaManager.kt      # 验证码管理  
│   └── UpdateChecker.kt       # 更新检查  
├── service/  
│   ├── RankMonitorService.kt  # 前台服务  
│   ├── FloatingWindowService.kt # 悬浮窗服务  
│   ├── ScreenCaptureService.kt  # 截图服务  
│   ├── CropSelectionOverlay.kt  # 截图区域选择  
│   ├── UpdateCheckWorker.kt     # 更新检查任务  
│   └── BootReceiver.kt          # 开机自启  
├── worker/  
│   └── RankCheckWorker.kt     # WorkManager 定期轮询  
├── ui/  
│   ├── theme/                 # Material 3 主题  
│   ├── navigation/            # 导航  
│   ├── components/            # 通用组件  
│   ├── home/                  # 主页 (绑定列表)  
│   ├── bind/                  # 添加绑定  
│   ├── query/                 # 排名查询  
│   ├── detail/                # 详细资料  
│   ├── history/               # 击剑记录  
│   ├── settings/              # 设置  
│   ├── account/               # 账号管理  
│   ├── friend/                # 好友管理  
│   ├── support/               # 支援  
│   ├── clanranking/           # 公会排名  
│   ├── manualbattle/          # 手动出刀  
│   ├── master/                # 会长/管理  
│   ├── labyrinth/             # 地下城  
│   ├── exequip/               # EX装备  
│   ├── eqa/                   # EQA  
│   ├── room/                  # 房间  
│   ├── daily/                 # 日常  
│   └── fortnightly/           # 半月刊/活动日历  
└── util/  
    ├── Platform.kt            # 平台枚举  
    ├── ArenaQueryClient.kt    # 竞技场查询客户端封装  
    ├── CharaRoster.kt         # 角色名单  
    ├── CharaIconUtil.kt       # 角色头像  
    ├── IconStorage.kt         # 图标存储  
    ├── EqaImageCache.kt       # EQA 图片缓存  
    ├── ElementUnits.kt        # 属性角色数据  
    ├── ClanBattleUtils.kt     # 会战工具  
    ├── KnightRankCalculator.kt # 骑士君等级计算  
    ├── ExMeta.kt              # EX 元数据  
    ├── PcrdApiSigner.kt       # PCD API 签名  
    ├── EmailSender.kt         # 邮件发送  
    └── TopBarBgStorage.kt     # 顶栏背景存储
```

## 构建

```bash
./gradlew assembleDebug
```



## 原库

基于 著名竞技场2 移植。
