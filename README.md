覆盖文件

[httpserver.py](https://github.com/user-attachments/files/26810006/httpserver.py)

[command_relay.py](https://github.com/user-attachments/files/26810005/command_relay.py)

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
## 打包准备
在\hoshino\modules\priconne，arena旁边创建arena_api文件夹，内创建__init__.py
代码输入：

```
import asyncio  
import base64  
import json  
import traceback  
from io import BytesIO  
from aiohttp import web  
from PIL import Image  
  
from hoshino import Service  
from hoshino.util import pic2b64  
from .. import chara  
  
sv = Service('arena_api', help_='竞技场图片识别HTTP API', bundle='pcr查询')  
  
API_HOST = '0.0.0.0'  
API_PORT = 8020  #写你空闲的端口
runner = None  
  
  
async def handle_query_image(request):  
    """  
    POST /api/arena/query_image  
    multipart/form-data:  
      - image: 图片文件  
      - region: 服务器区域 (1=全服, 2=B服, 3=台服, 4=日服)，默认2  
    """  
    try:  
        reader = await request.multipart()  
        image_data = None  
        region = 2  
  
        while True:  
            part = await reader.next()  
            if part is None:  
                break  
            if part.name == 'image':  
                image_data = await part.read()  
            elif part.name == 'region':  
                region_str = (await part.read()).decode('utf-8')  
                region = int(region_str)  
  
        if image_data is None:  
            return web.json_response({"code": -1, "message": "缺少image参数"}, status=400)  
  
        sv.logger.info(f'收到图片, 大小={len(image_data)}, region={region}')  
  
        from ..arena.old_main import getBox, clear_cache_except_preserved, render_atk_def_teams, recommend1Team, recommend2Teams  
        from ..arena import arena as arena_module  
  
        clear_cache_except_preserved()  
  
        image = Image.open(BytesIO(image_data))  
        box_dict, _debug_str = await getBox(image)  
  
        if not box_dict:  
            return web.json_response({  
                "code": 1,  
                "message": "未识别到有4个及以上角色的阵容",  
                "team_count": 0,  
                "image": None  
            })  
  
        sv.logger.info(f'识别到 {len(box_dict)} 个队伍: {box_dict}')  
  
        # 过滤掉空队伍  
        box_dict = [team for team in box_dict if len(team) >= 4]  
  
        if not box_dict:  
            return web.json_response({  
                "code": 1,  
                "message": "未识别到有4个及以上角色的阵容",  
                "team_count": 0,  
                "image": None  
            })  
  
        team_count = len(box_dict)  
  
        if team_count > 3:  
            return web.json_response({  
                "code": 1,  
                "message": "请截图pjjc详细对战记录（对战履历详情）（含敌我双方2或3队阵容）",  
                "team_count": team_count,  
                "image": None  
            })  
  
        # ========== 单队 JJC ==========  
        if team_count == 1:  
            sv.logger.info(f'单队查询: {box_dict[0]}')  
            team_ids = box_dict[0]  
  
            if all(tid == 1000 for tid in team_ids):  
                return web.json_response({  
                    "code": 1,  
                    "message": "识别到的队伍全是未知角色",  
                    "team_count": 1,  
                    "image": None  
                })  
  
            try:  
                records = await arena_module.do_query(team_ids, region, 1)  
            except Exception as e:  
                sv.logger.error(f'do_query 失败: {e}', exc_info=True)  
                records = []  
  
            if not records:  
                return web.json_response({  
                    "code": 1,  
                    "message": "未查询到解法",  
                    "team_count": 1,  
                    "image": None  
                })  
  
            # 渲染图片  
            render_entries = records[:10]  
            try:  
                teams_img = await render_atk_def_teams(render_entries)  
                buf = BytesIO()  
                teams_img.save(buf, format='PNG')  
                img_b64 = base64.b64encode(buf.getvalue()).decode()  
            except Exception as e:  
                sv.logger.error(f'渲染图片失败: {e}', exc_info=True)  
                img_b64 = None  
  
            return web.json_response({  
                "code": 0,  
                "message": "success",  
                "team_count": 1,  
                "image": img_b64  
            })  
        # ========== 多队 PJJC (2-3队) ==========  
        sv.logger.info(f'PJJC多队查询: {team_count} 队')  
  
        team_has_result = 0  
        all_query_records = [[] for _ in range(team_count)]  
        # 结构:  
        # [  
        #   [ [None, -100, "placeholder"], [(team1_atk1), val, render], ... ],  
        #   [ [None, -100, "placeholder"], [(team2_atk1), val, render], ... ],  
        #   ...  
        # ]  
  
        for query_index, query_team in enumerate(box_dict):  
            all_query_records[query_index].append([None, -100, "placeholder"])  
  
            if all(tid == 1000 for tid in query_team):  
                sv.logger.info(f'  队伍{query_index+1}: 全是未知角色，跳过')  
                continue  
  
            sv.logger.info(f'  队伍{query_index+1}: 查询 {query_team}')  
  
            try:  
                records = await arena_module.do_query(query_team, region, 1)  
            except Exception as e:  
                sv.logger.error(f'  队伍{query_index+1} do_query 失败: {e}', exc_info=True)  
                records = []  
  
            if not records:  
                sv.logger.info(f'  队伍{query_index+1}: 无结果')  
                continue  
  
            team_has_result += 1  
            sv.logger.info(f'  队伍{query_index+1}: 有 {len(records)} 条结果')  
  
            for record in records:  
                try:  
                    record_team = tuple([chara_obj.id for chara_obj in record["atk"]])  
                    all_query_records[query_index].append([record_team, record["val"], record])  
                except Exception as e:  
                    sv.logger.warning(f'  解析record失败: {e}')  
  
        if team_has_result == 0:  
            return web.json_response({  
                "code": 1,  
                "message": "均未查询到解法！",  
                "team_count": team_count,  
                "image": None  
            })  
  
        # ========== 无冲配队 ==========  
        sv.logger.info(f'开始无冲配队, team_has_result={team_has_result}')  
  
        collision_free_match_cnt = 0  
        outp_render = []  
        collision_free_match_cnt_2 = 0  
        outp_render_2 = []  
  
        if team_count == 2:  
            try_combinations = []  
            for q1_idx, q1_rec in enumerate(all_query_records[0]):  
                for q2_idx, q2_rec in enumerate(all_query_records[1]):  
                    val = q1_rec[1] + q2_rec[1]  
                    try_combinations.append([q1_idx, q2_idx, val])  
            try_combinations = sorted(try_combinations, key=lambda x: x[-1], reverse=True)  
  
            for tc in try_combinations:  
                record_1 = all_query_records[0][tc[0]]  
                record_2 = all_query_records[1][tc[1]]  
                team_1 = [] if record_1[0] is None else list(record_1[0])  
                team_2 = [] if record_2[0] is None else list(record_2[0])  
                team_mix = team_1 + team_2  
                if len(team_mix) != len(set(team_mix)):  
                    continue  
  
                succ = False  
                val = tc[-1]  
                if val < -250:  
                    break  
                if val < -150:  # 0队有结果，补2队  
                    team_recommend_1, team_recommend_2 = recommend2Teams(team_mix)  
                    if team_recommend_1 == "placeholder" or team_recommend_2 == "placeholder":  
                        continue  
                    record_1[-1] = team_recommend_1  
                    record_2[-1] = team_recommend_2  
                    succ = True  
                elif val < -50:  # 1队有结果，补1队  
                    team_recommend = recommend1Team(team_mix)  
                    if team_recommend == "placeholder":  
                        continue  
                    if team_1 == []:  
                        record_1[-1] = team_recommend  
                    if team_2 == []:  
                        record_2[-1] = team_recommend  
                    succ = True  
                else:  # 2队都有结果  
                    succ = True  
  
                if succ:  
                    collision_free_match_cnt += 1  
                    outp_render += [record_1[-1], record_2[-1], []]  
                    if collision_free_match_cnt >= 8:  
                        break  
  
        if team_count == 3:  
            try_combinations = []  
            for q1_idx, q1_rec in enumerate(all_query_records[0]):  
                for q2_idx, q2_rec in enumerate(all_query_records[1]):  
                    for q3_idx, q3_rec in enumerate(all_query_records[2]):  
                        val = q1_rec[1] + q2_rec[1] + q3_rec[1]  
                        try_combinations.append([q1_idx, q2_idx, q3_idx, val])  
            try_combinations = sorted(try_combinations, key=lambda x: x[-1], reverse=True)  
  
            for tc in try_combinations:  
                record_1 = all_query_records[0][tc[0]]  
                record_2 = all_query_records[1][tc[1]]  
                record_3 = all_query_records[2][tc[2]]  
                team_1 = [] if record_1[0] is None else list(record_1[0])  
                team_2 = [] if record_2[0] is None else list(record_2[0])  
                team_3 = [] if record_3[0] is None else list(record_3[0])  
                team_mix = team_1 + team_2 + team_3  
                if len(team_mix) != len(set(team_mix)):  
                    continue  
  
                succ = False  
                val = tc[-1]  
                if val < -250:  
                    break  
                if val < -150:  # 1队有结果，补2队  
                    team_recommend_1, team_recommend_2 = recommend2Teams(team_mix)  
                    if team_recommend_1 == "placeholder" or team_recommend_2 == "placeholder":  
                        continue  
                    if team_1 != []:  
                        record_2[-1] = team_recommend_1  
                        record_3[-1] = team_recommend_2  
                    if team_2 != []:  
                        record_3[-1] = team_recommend_1  
                        record_1[-1] = team_recommend_2  
                    if team_3 != []:  
                        record_1[-1] = team_recommend_1  
                        record_2[-1] = team_recommend_2  
                    succ = True  
                elif val < -50:  # 2队有结果，补1队  
                    team_recommend = recommend1Team(team_mix)  
                    if team_recommend == "placeholder":  
                        collision_free_match_cnt_2 += 1  
                        outp_render_2 += [record_1[-1], record_2[-1], record_3[-1], []]  
                    else:  
                        if team_1 == []:  
                            record_1[-1] = team_recommend  
                        if team_2 == []:  
                            record_2[-1] = team_recommend  
                        if team_3 == []:  
                            record_3[-1] = team_recommend  
                        succ = True  
                else:  # 3队都有结果  
                    succ = True  
  
                if succ:  
                    collision_free_match_cnt += 1  
                    outp_render += [record_1[-1], record_2[-1], record_3[-1], []]  
                    if collision_free_match_cnt >= 6:  
                        break  
  
        # ========== 渲染结果图片 ==========  
        img_b64 = None  
        result_message = ""  
  
        if collision_free_match_cnt:  
            sv.logger.info(f'无冲配队成功: {collision_free_match_cnt} 组')  
            result_message = f"找到 {collision_free_match_cnt} 组无冲配队"  
            try:  
                teams_img = await render_atk_def_teams(outp_render[:-1])  
                buf = BytesIO()  
                teams_img.save(buf, format='PNG')  
                img_b64 = base64.b64encode(buf.getvalue()).decode()  
            except Exception as e:  
                sv.logger.error(f'渲染无冲配队图片失败: {e}', exc_info=True)  
        elif collision_free_match_cnt_2:  
            sv.logger.info(f'部分无冲配队: {collision_free_match_cnt_2} 组')  
            result_message = f"找到 {collision_free_match_cnt_2} 组部分无冲配队"  
            try:  
                teams_img = await render_atk_def_teams(outp_render_2[:-1])  
                buf = BytesIO()  
                teams_img.save(buf, format='PNG')  
                img_b64 = base64.b64encode(buf.getvalue()).decode()  
            except Exception as e:  
                sv.logger.error(f'渲染部分无冲图片失败: {e}', exc_info=True)  
        else:  
            # 无冲配对失败，返回各队独立结果  
            sv.logger.info('无冲配对失败，返回单步查询结果')  
            result_message = "无冲配对失败，返回单步查询结果"  
            all_render = []  
            for index, records in enumerate(all_query_records):  
                if len(records) > 1:  # 第0个是placeholder  
                    for rec in records[1:]:  
                        all_render.append(rec[-1])  # render dict  
                    all_render.append([])  # 空行分隔  
            if all_render and all_render[-1] == []:  
                all_render = all_render[:-1]  
            if all_render:  
                try:  
                    teams_img = await render_atk_def_teams(all_render)  
                    buf = BytesIO()  
                    teams_img.save(buf, format='PNG')  
                    img_b64 = base64.b64encode(buf.getvalue()).decode()  
                except Exception as e:  
                    sv.logger.error(f'渲染单步结果图片失败: {e}', exc_info=True)  
  
        if img_b64:  
            return web.json_response({  
                "code": 0,  
                "message": result_message,  
                "team_count": team_count,  
                "image": img_b64  
            })  
        else:  
            return web.json_response({  
                "code": 1,  
                "message": result_message or "渲染失败",  
                "team_count": team_count,  
                "image": None  
            })  
  
    except Exception as e:  
        error_msg = f"arena_api query_image error: {e}\n{traceback.format_exc()}"  
        sv.logger.error(error_msg)  
        return web.json_response({"code": -1, "message": str(e)}, status=500)  
  
  
async def handle_health(request):  
    return web.json_response({"status": "ok"})  
  
  
app = web.Application(client_max_size=10 * 1024 * 1024)  
app.router.add_post('/api/arena/query_image', handle_query_image)  
app.router.add_get('/api/arena/health', handle_health)  
  
  
async def _start_api_server():  
    global runner  
    try:  
        runner = web.AppRunner(app)  
        await runner.setup()  
        site = web.TCPSite(runner, API_HOST, API_PORT)  
        await site.start()  
        sv.logger.info(f'Arena API server started on {API_HOST}:{API_PORT}')  
    except Exception as e:  
        sv.logger.error(f'Arena API server 启动失败: {e}', exc_info=True)  
  
  
loop = asyncio.get_event_loop()  
loop.create_task(_start_api_server())
```


## 原库

基于 著名竞技场2 移植。
