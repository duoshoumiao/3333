## 覆盖清日常文件

[httpserver.py](https://github.com/user-attachments/files/26810006/httpserver.py)

[command_relay.py](https://github.com/user-attachments/files/26810005/command_relay.py)

##房间插件

[room.py](https://github.com/user-attachments/files/26977086/room.py)

## 打包准备
在\hoshino\modules\priconne，arena旁边创建arena_api文件夹，内创建__init__.py
[__init__.py](https://github.com/user-attachments/files/26810816/__init__.py)


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
├── PcrJjcApp.kt              # Application 类 (Hilt, 通知渠道)
├── MainActivity.kt            # 主 Activity (Compose)
├── data/
│   ├── local/
│   │   ├── entity/            # Room 实体 (PcrBind, Account, JjcHistory)
│   │   ├── dao/               # Room DAO (BindDao, AccountDao, HistoryDao)
│   │   └── AppDatabase.kt     # Room 数据库
│   └── remote/
│       ├── CryptoUtils.kt     # AES-CBC 加密 + msgpack 序列化
│       ├── RsaCrypto.kt       # RSA 公钥加密
│       ├── PcrClient.kt       # CN 服 API 客户端
│       ├── TwPcrClient.kt     # TW 服 API 客户端
│       ├── BiliAuth.kt        # B 站 SDK 登录
│       └── ApiException.kt    # API 异常
├── di/
│   └── AppModule.kt           # Hilt 依赖注入
├── domain/
│   ├── QueryEngine.kt         # 查询引擎
│   └── RankMonitor.kt         # 排名监控 + 通知
├── service/
│   └── RankMonitorService.kt  # 前台服务
├── worker/
│   └── RankCheckWorker.kt     # WorkManager 定期轮询
├── ui/
│   ├── theme/Theme.kt         # Material 3 主题
│   ├── navigation/NavHost.kt  # 导航
│   ├── home/                  # 主页 (绑定列表)
│   ├── bind/                  # 添加绑定
│   ├── query/                 # 排名查询
│   ├── detail/                # 详细资料
│   ├── history/               # 击剑记录
│   ├── settings/              # 设置
│   └── account/               # 账号管理
└── util/
    └── Platform.kt            # 平台枚举
```

## 构建

```bash
./gradlew assembleDebug
```



## 原库

基于 著名竞技场2 移植。
