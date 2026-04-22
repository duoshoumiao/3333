## 覆盖清日常文件

[httpserver.py](https://github.com/user-attachments/files/26810006/httpserver.py)

[command_relay.py](https://github.com/user-attachments/files/26810005/command_relay.py)

##房间插件 
[room.py](https://github.com/user-attachments/files/26977086/room.py)
"""  
Hoshino 房间管理插件 - 纯HTTP服务器版  
用于提供房间列表和用户连接功能，含会战Boss状态监控  
  
安装方法：  
1. 将此文件放到 hoshino/modules/room_server/ 目录（文件夹名任意）  
2. 在 config/__init__.py 的 MODULES_ON 中添加 'room_server'  
3. 重启 Hoshino  
  
配置说明：  
- 在 Hoshino 的 config 中设置 ROOM_HTTP_PORT = 8066 来指定端口  
- 默认端口是 8066  
  
API 端点：  
- GET  /rooms                                  - 获取房间列表（含 players 列表）  
- POST /rooms                                  - 创建房间  
- POST /rooms/join                             - 加入房间  
- POST /rooms/leave                            - 离开房间（房主离开自动移交）  
- POST /rooms/dismiss                          - 解散房间（仅房主）  
- GET  /rooms/{room_id}/messages               - 获取聊天消息（支持 ?since=timestamp）  
- POST /rooms/{room_id}/messages               - 发送聊天消息  
- GET  /rooms/{room_id}/clan_battle            - 获取房间的会战状态  
- POST /rooms/{room_id}/clan_battle/update     - 监控端推送会战状态（boss血量、排名等）  
- POST /rooms/{room_id}/clan_battle/monitor    - 开启/关闭/接管监控  
- POST /rooms/{room_id}/clan_battle/apply      - 申请出刀（toggle）  
- POST /rooms/{room_id}/clan_battle/tree       - 挂树（toggle）  
- POST /rooms/{room_id}/clan_battle/subscribe  - 预约下一周目boss（toggle）  
- POST /rooms/{room_id}/clan_battle/sl         - 记录SL  
"""  
  
import json  
import time  
import uuid  
import hashlib  
import asyncio  
from aiohttp import web  
from hoshino import Service, config  
  
  
# ==================== 会战状态数据结构 ====================  
class ClanBattleState:  
    """每个房间独立的会战状态"""  
  
    def __init__(self):  
        self.rank = 0                   # 当前排名  
        self.monitor_status = False     # 监控是否开启  
        self.monitor_qq = ""            # 监控人QQ  
        self.monitor_name = ""          # 监控人名称  
        self.current_stage = ""         # 当前进度描述，如 "B面2阶段"  
        self.lap_num = 0                # 当前周目  
        self.boss_list = []             # 5个boss状态  
        self.applies = {}               # boss_num -> [记录列表]  
        self.trees = {}                 # boss_num -> [记录列表]  
        self.subscribes = {}            # boss_num -> [记录列表]  
        self.sl_records = {}            # qq -> timestamp  
        self.last_update = 0            # 最后更新时间戳  
        self._init_boss()  
  
    def _init_boss(self):  
        self.boss_list = []  
        for i in range(5):  
            self.boss_list.append({  
                "order": i + 1,  
                "lap": 0,  
                "current_hp": 0,  
                "max_hp": 0,  
                "fighter_num": 0,  
            })  
        for i in range(1, 6):  
            self.applies[i] = []  
            self.trees[i] = []  
            self.subscribes[i] = []  
  
    def to_dict(self):  
        return {  
            "rank": self.rank,  
            "monitor_status": self.monitor_status,  
            "monitor_qq": self.monitor_qq,  
            "monitor_name": self.monitor_name,  
            "current_stage": self.current_stage,  
            "lap_num": self.lap_num,  
            "boss_list": self.boss_list,  
            "applies": self.applies,  
            "trees": self.trees,  
            "subscribes": self.subscribes,  
            "sl_records": self.sl_records,  
            "last_update": self.last_update,  
        }  
  
    def update_from_dict(self, data: dict):  
        """监控端推送的完整boss状态更新"""  
        if "rank" in data:  
            self.rank = data["rank"]  
        if "current_stage" in data:  
            self.current_stage = data["current_stage"]  
        if "lap_num" in data:  
            self.lap_num = data["lap_num"]  
        if "boss_list" in data:  
            for i, boss_data in enumerate(data["boss_list"]):  
                if i < 5:  
                    old_boss = self.boss_list[i]  
                    old_boss["lap"] = boss_data.get("lap", old_boss["lap"])  
                    old_boss["current_hp"] = boss_data.get("current_hp", old_boss["current_hp"])  
                    old_boss["max_hp"] = boss_data.get("max_hp", old_boss["max_hp"])  
                    old_boss["order"] = boss_data.get("order", old_boss["order"])  
                    old_boss["fighter_num"] = boss_data.get("fighter_num", old_boss["fighter_num"])  
        if "damage_history" in data:  
            # 处理击破时自动清空对应boss的申请出刀和挂树  
            for history in data["damage_history"]:  
                if history.get("kill"):  
                    order = history.get("order_num", 0)  
                    if order in self.applies:  
                        self.applies[order] = []  
                    if order in self.trees:  
                        self.trees[order] = []  
        self.last_update = int(time.time() * 1000)  
  
    def toggle_apply(self, qq: str, name: str, boss_num: int, text: str = "") -> dict:  
        """申请出刀 toggle：已有则取消，没有则添加"""  
        if boss_num not in self.applies:  
            self.applies[boss_num] = []  
  
        records = self.applies[boss_num]  
        for i, record in enumerate(records):  
            if record["qq"] == qq:  
                records.pop(i)  
                return {  
                    "action": "cancel",  
                    "message": f"{name} 取消了{boss_num}王的出刀申请"  
                }  
  
        records.append({  
            "qq": qq,  
            "name": name,  
            "boss_num": boss_num,  
            "text": text,  
            "time": int(time.time()),  
        })  
        msg = f"{name} 申请出刀{boss_num}王"  
        if text:  
            msg += f"（{text}）"  
        return {  
            "action": "apply",  
            "message": msg  
        }  
  
    def toggle_tree(self, qq: str, name: str, boss_num: int, text: str = "") -> dict:  
        """挂树 toggle：已有则取消，没有则添加"""  
        if boss_num not in self.trees:  
            self.trees[boss_num] = []  
  
        records = self.trees[boss_num]  
        for i, record in enumerate(records):  
            if record["qq"] == qq:  
                records.pop(i)  
                return {  
                    "action": "cancel",  
                    "message": f"{name} 已从{boss_num}王下树"  
                }  
  
        records.append({  
            "qq": qq,  
            "name": name,  
            "boss_num": boss_num,  
            "text": text,  
            "time": int(time.time()),  
        })  
        msg = f"{name} 挂树{boss_num}王"  
        if text:  
            msg += f"（{text}）"  
        return {  
            "action": "tree",  
            "message": msg  
        }  
  
    def toggle_subscribe(self, qq: str, name: str, boss_num: int, lap: int = 0, text: str = "") -> dict:  
        """预约下一周目boss toggle：已有则取消，没有则添加"""  
        if boss_num not in self.subscribes:  
            self.subscribes[boss_num] = []  
  
        records = self.subscribes[boss_num]  
        for i, record in enumerate(records):  
            if record["qq"] == qq:  
                records.pop(i)  
                return {  
                    "action": "cancel",  
                    "message": f"{name} 取消了{boss_num}王的预约"  
                }  
  
        records.append({  
            "qq": qq,  
            "name": name,  
            "boss_num": boss_num,  
            "lap": lap if lap else self.lap_num + 1,  
            "text": text,  
            "time": int(time.time()),  
        })  
        target_lap = lap if lap else self.lap_num + 1  
        msg = f"{name} 预约了{target_lap}周目{boss_num}王"  
        if text:  
            msg += f"（{text}）"  
        return {  
            "action": "subscribe",  
            "message": msg  
        }  
  
    def record_sl(self, qq: str, name: str) -> dict:  
        """记录SL"""  
        today_key = time.strftime("%Y-%m-%d", time.localtime())  
        sl_key = f"{qq}_{today_key}"  
  
        if sl_key in self.sl_records:  
            return {  
                "action": "already",  
                "message": f"{name} 今天已经SL过了"  
            }  
  
        self.sl_records[sl_key] = int(time.time())  
        return {  
            "action": "recorded",  
            "message": f"{name} 已记录SL"  
        }  
  
  
# ==================== 房间数据存储 ====================  
class RoomManager:  
    def __init__(self):  
        self.rooms = {}             # room_id -> room_info  
        self.players = {}           # qq -> room_id  
        self.messages = {}          # room_id -> [message_list]  
        self.clan_battles = {}      # room_id -> ClanBattleState  
  
    def _room_summary(self, room: dict) -> dict:  
        """用于 API 响应的房间信息（去掉敏感字段，附带 players 列表）"""  
        room_id = room["room_id"]  
        has_monitor = False  
        if room_id in self.clan_battles:  
            has_monitor = self.clan_battles[room_id].monitor_status  
  
        return {  
            "room_id": room_id,  
            "room_name": room["room_name"],  
            "has_password": room["has_password"],  
            "host_name": room["host_name"],  
            "host_qq": room["host_qq"],  
            "player_count": room["player_count"],  
            "max_players": room["max_players"],  
            "has_monitor": has_monitor,  
            "players": [  
                {  
                    "name": p["name"],  
                    "qq": p["qq"],  
                    "is_host": p["is_host"],  
                }  
                for p in room["players"]  
            ],  
        }  
  
    def _get_or_create_clan_battle(self, room_id: str) -> ClanBattleState:  
        """获取或创建房间的会战状态"""  
        if room_id not in self.clan_battles:  
            self.clan_battles[room_id] = ClanBattleState()  
        return self.clan_battles[room_id]  
  
    def create_room(self, room_name: str, password: str, host_name: str, host_qq: str) -> dict:  
        """创建房间"""  
        room_id = str(uuid.uuid4())[:8].upper()  
        room = {  
            "room_id": room_id,  
            "room_name": room_name,  
            "password": hashlib.md5(password.encode()).hexdigest() if password else None,  
            "has_password": password is not None,  
            "host_name": host_name,  
            "host_qq": host_qq,  
            "player_count": 1,  
            "max_players": 30,  
            "players": [{  
                "name": host_name,  
                "qq": host_qq,  
                "is_host": True,  
                "joined_at": time.time()  
            }],  
            "created_at": time.time()  
        }  
        self.rooms[room_id] = room  
        self.players[host_qq] = room_id  
        self.messages[room_id] = []  
        self.clan_battles[room_id] = ClanBattleState()  
        return room  
  
    def get_room_list(self) -> list:  
        return [self._room_summary(r) for r in self.rooms.values()]  
  
    def join_room(self, room_id: str, password: str, player_name: str, player_qq: str) -> dict:  
        if room_id not in self.rooms:  
            raise ValueError("房间不存在")  
  
        room = self.rooms[room_id]  
  
        # 检查密码  
        if room["password"]:  
            if not password:  
                raise ValueError("需要密码")  
            pwd_hash = hashlib.md5(password.encode()).hexdigest()  
            if pwd_hash != room["password"]:  
                raise ValueError("密码错误")  
  
        # 已经在其他房间 → 先离开  
        if player_qq in self.players and self.players[player_qq] != room_id:  
            self.leave_room(self.players[player_qq], player_qq)  
  
        # 已经在当前房间 → 直接返回  
        for p in room["players"]:  
            if p["qq"] == player_qq:  
                return room  
  
        # 房间已满  
        if room["player_count"] >= room["max_players"]:  
            raise ValueError("房间已满")  
  
        room["players"].append({  
            "name": player_name,  
            "qq": player_qq,  
            "is_host": False,  
            "joined_at": time.time()  
        })  
        room["player_count"] += 1  
        self.players[player_qq] = room_id  
  
        self.add_message(room_id, "system", "系统", f"{player_name} 加入了房间")  
        return room  
  
    def leave_room(self, room_id: str, player_qq: str):  
        """离开房间：房主离开自动移交给下一位玩家，不再解散房间"""  
        if room_id not in self.rooms:  
            return  
  
        room = self.rooms[room_id]  
        players = room["players"]  
  
        player_name = ""  
        was_host = False  
        for i, p in enumerate(players):  
            if p["qq"] == player_qq:  
                was_host = p["is_host"]  
                player_name = p["name"]  
                players.pop(i)  
                room["player_count"] -= 1  
                break  
  
        if player_qq in self.players and self.players[player_qq] == room_id:  
            del self.players[player_qq]  
  
        # 如果离开的人是监控人，自动关闭监控  
        if room_id in self.clan_battles:  
            cb = self.clan_battles[room_id]  
            if cb.monitor_qq == player_qq:  
                cb.monitor_status = False  
                cb.monitor_qq = ""  
                cb.monitor_name = ""  
                self.add_message(room_id, "system", "系统",  
                                 f"监控人 {player_name} 离开了房间，监控已关闭")  
  
        # 房主离开 → 把房主身份移交给最早加入的剩余玩家  
        if was_host and players:  
            new_host = players[0]  
            new_host["is_host"] = True  
            room["host_name"] = new_host["name"]  
            room["host_qq"] = new_host["qq"]  
            self.add_message(  
                room_id,  
                "system",  
                "系统",  
                f"{player_name} 离开了房间，房主已转交给 {new_host['name']}"  
            )  
        elif player_name:  
            self.add_message(room_id, "system", "系统", f"{player_name} 离开了房间")  
  
    def dismiss_room(self, room_id: str, host_qq: str):  
        """解散房间（仅房主）"""  
        if room_id not in self.rooms:  
            raise ValueError("房间不存在")  
  
        room = self.rooms[room_id]  
        if room["host_qq"] != host_qq:  
            raise ValueError("只有房主才能解散房间")  
  
        for p in room["players"]:  
            if self.players.get(p["qq"]) == room_id:  
                del self.players[p["qq"]]  
  
        del self.rooms[room_id]  
        if room_id in self.messages:  
            del self.messages[room_id]  
        if room_id in self.clan_battles:  
            del self.clan_battles[room_id]  
  
    def add_message(self, room_id: str, sender_qq: str, sender_name: str, content: str) -> dict:  
        if room_id not in self.rooms:  
            raise ValueError("房间不存在")  
  
        msg = {  
            "id": str(uuid.uuid4()),  
            "room_id": room_id,  
            "sender_qq": sender_qq,  
            "sender_name": sender_name,  
            "content": content,  
            "timestamp": int(time.time() * 1000)  
        }  
  
        if room_id not in self.messages:  
            self.messages[room_id] = []  
        self.messages[room_id].append(msg)  
  
        if len(self.messages[room_id]) > 500:  
            self.messages[room_id] = self.messages[room_id][-500:]  
  
        return msg  
  
    def get_messages(self, room_id: str, since: int = 0) -> list:  
        if room_id not in self.rooms:  
            raise ValueError("房间不存在")  
        if room_id not in self.messages:  
            return []  
        if since > 0:  
            return [m for m in self.messages[room_id] if m["timestamp"] > since]  
        return list(self.messages[room_id])  
  
  
room_manager = RoomManager()  
  
  
# ==================== 原有 HTTP API ====================  
async def get_rooms(request):  
    return web.json_response(room_manager.get_room_list())  
  
  
async def create_room(request):  
    try:  
        data = await request.json()  
        room_name = data.get("room_name")  
        password = data.get("password")  
        host_name = data.get("host_name")  
        host_qq = data.get("host_qq")  
  
        if not room_name or not host_name or not host_qq:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        room = room_manager.create_room(room_name, password, host_name, host_qq)  
        resp = room_manager._room_summary(room)  
        resp["password"] = password  
        return web.json_response(resp)  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def join_room(request):  
    try:  
        data = await request.json()  
        room_id = data.get("room_id")  
        password = data.get("password")  
        player_name = data.get("player_name")  
        player_qq = data.get("player_qq")  
  
        if not room_id or not player_name or not player_qq:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        room = room_manager.join_room(room_id, password, player_name, player_qq)  
        return web.json_response(room_manager._room_summary(room))  
    except ValueError as e:  
        return web.json_response({"error": str(e)}, status=403)  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def leave_room(request):  
    try:  
        data = await request.json()  
        room_id = data.get("room_id")  
        player_qq = data.get("player_qq")  
  
        if not room_id or not player_qq:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        room_manager.leave_room(room_id, player_qq)  
        return web.json_response({"success": True})  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def dismiss_room(request):  
    try:  
        data = await request.json()  
        room_id = data.get("room_id")  
        host_qq = data.get("host_qq")  
  
        if not room_id or not host_qq:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        room_manager.dismiss_room(room_id, host_qq)  
        return web.json_response({"success": True})  
    except ValueError as e:  
        return web.json_response({"error": str(e)}, status=403)  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def get_messages(request):  
    try:  
        room_id = request.match_info["room_id"]  
        since = int(request.query.get("since", "0"))  
        messages = room_manager.get_messages(room_id, since)  
        return web.json_response(messages)  
    except ValueError as e:  
        return web.json_response({"error": str(e)}, status=404)  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def send_message(request):  
    try:  
        room_id = request.match_info["room_id"]  
        data = await request.json()  
        sender_qq = data.get("sender_qq")  
        sender_name = data.get("sender_name")  
        content = data.get("content")  
  
        if not sender_qq or not content:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        if not sender_name:  
            sender_name = "玩家"  
  
        msg = room_manager.add_message(room_id, sender_qq, sender_name, content)  
        return web.json_response(msg)  
    except ValueError as e:  
        return web.json_response({"error": str(e)}, status=404)  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
# ==================== 会战 API ====================  
  
async def get_clan_battle(request):  
    """GET /rooms/{room_id}/clan_battle — 获取房间的会战状态"""  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
        return web.json_response(cb.to_dict())  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def update_clan_battle(request):  
    """  
    POST /rooms/{room_id}/clan_battle/update — 监控端推送会战状态  
    Body: {  
        "qq": "监控人QQ",  
        "rank": 123,  
        "current_stage": "B面2阶段",  
        "lap_num": 12,  
        "boss_list": [  
            {"order": 1, "lap": 12, "current_hp": 12000000, "max_hp": 12000000, "fighter_num": 0},  
            ...  
        ],  
        "damage_history": [  
            {"name": "xxx", "order_num": 1, "lap_num": 12, "damage": 500000, "kill": false, "create_time": 1234567890},  
            ...  
        ]  
    }  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
  
        # 只有当前监控人才能推送更新  
        if cb.monitor_status and cb.monitor_qq and cb.monitor_qq != qq:  
            return web.json_response({"error": "你不是当前监控人"}, status=403)  
  
        # 处理击破通知：boss被击破时自动发消息到房间  
        if "damage_history" in data:  
            for history in data["damage_history"]:  
                kill_text = "并击破" if history.get("kill") else ""  
                msg = (f'{history["name"]}对{history["lap_num"]}周目'  
                       f'{history["order_num"]}王造成了{history["damage"]}点伤害{kill_text}')  
                room_manager.add_message(room_id, "monitor", "监控播报", msg)  
  
                # 击破时通知挂树的人  
                if history.get("kill"):  
                    order = history["order_num"]  
                    if order in cb.trees and cb.trees[order]:  
                        tree_names = ", ".join([r["name"] for r in cb.trees[order]])  
                        room_manager.add_message(  
                            room_id, "system", "系统",  
                            f"{order}王已被击破，以下成员自动下树：{tree_names}"  
                        )  
  
        cb.update_from_dict(data)  
        return web.json_response({"success": True, "last_update": cb.last_update})  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def monitor_clan_battle(request):  
    """  
    POST /rooms/{room_id}/clan_battle/monitor — 开启/关闭/接管监控  
    Body: {  
        "qq": "玩家QQ",  
        "name": "玩家名称",  
        "action": "start" | "stop"  
    }  
    任何房间成员都可以用自己的账号顶掉当前监控  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
        name = data.get("name", "")  
        action = data.get("action", "start")  
  
        if not qq or not name:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        # 检查是否是房间成员  
        room = room_manager.rooms[room_id]  
        is_member = any(p["qq"] == qq for p in room["players"])  
        if not is_member:  
            return web.json_response({"error": "你不是房间成员"}, status=403)  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
  
        if action == "start":  
            old_monitor = cb.monitor_name if cb.monitor_status else None  
            cb.monitor_status = True  
            cb.monitor_qq = qq  
            cb.monitor_name = name  
  
            if old_monitor and old_monitor != name:  
                room_manager.add_message(  
                    room_id, "system", "系统",  
                    f"{name} 接管了监控（原监控人：{old_monitor}）"  
                )  
            else:  
                room_manager.add_message(  
                    room_id, "system", "系统",  
                    f"{name} 开启了出刀监控"  
                )  
  
            return web.json_response({  
                "success": True,  
                "message": f"监控已开启，监控人：{name}"  
            })  
  
        elif action == "stop":  
            # 只有当前监控人或房主可以关闭  
            if cb.monitor_qq != qq and room["host_qq"] != qq:  
                return web.json_response({"error": "只有监控人或房主才能关闭监控"}, status=403)  
  
            cb.monitor_status = False  
            cb.monitor_qq = ""  
            cb.monitor_name = ""  
  
            room_manager.add_message(  
                room_id, "system", "系统",  
                f"{name} 关闭了出刀监控"  
            )  
  
            return web.json_response({  
                "success": True,  
                "message": "监控已关闭"  
            })  
  
        else:  
            return web.json_response({"error": "无效的action，请使用 start 或 stop"}, status=400)  
  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def apply_clan_battle(request):  
    """  
    POST /rooms/{room_id}/clan_battle/apply — 申请出刀（toggle）  
    Body: {  
        "qq": "玩家QQ",  
        "name": "玩家名称",  
        "boss_num": 1-5,  
        "text": "留言（可选）"  
    }  
    再次点击取消，两种消息都会发送到房间聊天  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
        name = data.get("name", "")  
        boss_num = data.get("boss_num", 0)  
        text = data.get("text", "")  
  
        if not qq or not name or not boss_num:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        if boss_num not in range(1, 6):  
            return web.json_response({"error": "boss_num 必须在 1-5 之间"}, status=400)  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
        result = cb.toggle_apply(qq, name, boss_num, text)  
  
        # 发送消息到房间聊天  
        room_manager.add_message(room_id, qq, name, result["message"])  
  
        return web.json_response({  
            "success": True,  
            "action": result["action"],  
            "message": result["message"],  
            "applies": cb.applies,  
        })  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def tree_clan_battle(request):  
    """  
    POST /rooms/{room_id}/clan_battle/tree — 挂树（toggle）  
    Body: {  
        "qq": "玩家QQ",  
        "name": "玩家名称",  
        "boss_num": 1-5,  
        "text": "留言（可选）"  
    }  
    再次点击取消，两种消息都会发送到房间聊天  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
        name = data.get("name", "")  
        boss_num = data.get("boss_num", 0)  
        text = data.get("text", "")  
  
        if not qq or not name or not boss_num:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        if boss_num not in range(1, 6):  
            return web.json_response({"error": "boss_num 必须在 1-5 之间"}, status=400)  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
        result = cb.toggle_tree(qq, name, boss_num, text)  
  
        # 发送消息到房间聊天  
        room_manager.add_message(room_id, qq, name, result["message"])  
  
        return web.json_response({  
            "success": True,  
            "action": result["action"],  
            "message": result["message"],  
            "trees": cb.trees,  
        })  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def subscribe_clan_battle(request):  
    """  
    POST /rooms/{room_id}/clan_battle/subscribe — 预约下一周目boss（toggle）  
    Body: {  
        "qq": "玩家QQ",  
        "name": "玩家名称",  
        "boss_num": 1-5,  
        "lap": 周目数（可选，默认当前周目+1）,  
        "text": "留言（可选）"  
    }  
    再次点击取消，两种消息都会发送到房间聊天  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
        name = data.get("name", "")  
        boss_num = data.get("boss_num", 0)  
        lap = data.get("lap", 0)  
        text = data.get("text", "")  
  
        if not qq or not name or not boss_num:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        if boss_num not in range(1, 6):  
            return web.json_response({"error": "boss_num 必须在 1-5 之间"}, status=400)  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
        result = cb.toggle_subscribe(qq, name, boss_num, lap, text)  
  
        # 发送消息到房间聊天  
        room_manager.add_message(room_id, qq, name, result["message"])  
  
        return web.json_response({  
            "success": True,  
            "action": result["action"],  
            "message": result["message"],  
            "subscribes": cb.subscribes,  
        })  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def sl_clan_battle(request):  
    """  
    POST /rooms/{room_id}/clan_battle/sl — 记录SL  
    Body: {  
        "qq": "玩家QQ",  
        "name": "玩家名称"  
    }  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
        name = data.get("name", "")  
  
        if not qq or not name:  
            return web.json_response({"error": "缺少必要参数"}, status=400)  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
        result = cb.record_sl(qq, name)  
  
        # 发送消息到房间聊天  
        room_manager.add_message(room_id, qq, name, result["message"])  
  
        return web.json_response({  
            "success": True,  
            "action": result["action"],  
            "message": result["message"],  
            "sl_records": cb.sl_records,  
        })  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def report_clan_battle(request):  
    """  
    GET /rooms/{room_id}/clan_battle/report?type=current|today|yesterday|personal&name=游戏名称  
    获取战报数据（由客户端监控账号查询后推送，这里只做存储和转发）  
  
    POST /rooms/{room_id}/clan_battle/report — 监控端推送战报数据  
    Body: {  
        "qq": "监控人QQ",  
        "type": "current" | "today" | "yesterday" | "personal",  
        "name": "游戏名称（personal类型必填）",  
        "report_data": "战报文本内容"  
    }  
    """  
    if request.method == "GET":  
        try:  
            room_id = request.match_info["room_id"]  
            if room_id not in room_manager.rooms:  
                return web.json_response({"error": "房间不存在"}, status=404)  
  
            cb = room_manager._get_or_create_clan_battle(room_id)  
  
            if not cb.monitor_status:  
                return web.json_response({"error": "监控未开启，无法获取战报"}, status=400)  
  
            report_type = request.query.get("type", "current")  
            player_name = request.query.get("name", "")  
  
            # 返回需要监控端执行的查询请求  
            return web.json_response({  
                "need_query": True,  
                "monitor_qq": cb.monitor_qq,  
                "type": report_type,  
                "name": player_name,  
                "message": "请等待监控端查询..."  
            })  
        except Exception as e:  
            return web.json_response({"error": str(e)}, status=400)  
  
    elif request.method == "POST":  
        try:  
            room_id = request.match_info["room_id"]  
            if room_id not in room_manager.rooms:  
                return web.json_response({"error": "房间不存在"}, status=404)  
  
            data = await request.json()  
            qq = data.get("qq", "")  
            report_type = data.get("type", "current")  
            player_name = data.get("name", "")  
            report_data = data.get("report_data", "")  
  
            cb = room_manager._get_or_create_clan_battle(room_id)  
  
            # 只有监控人可以推送战报  
            if cb.monitor_qq != qq:  
                return web.json_response({"error": "只有监控人才能推送战报"}, status=403)  
  
            type_names = {  
                "current": "当前战报",  
                "today": "今日出刀",  
                "yesterday": "昨日出刀",  
                "personal": f"{player_name}的战报",  
            }  
            title = type_names.get(report_type, "战报")  
  
            # 将战报作为消息发送到房间  
            room_manager.add_message(  
                room_id, "monitor", "监控播报",  
                f"===== {title} =====\n{report_data}"  
            )  
  
            return web.json_response({"success": True, "message": f"{title}已发送"})  
        except Exception as e:  
            return web.json_response({"error": str(e)}, status=400)  
  
  
async def request_report(request):  
    """  
    POST /rooms/{room_id}/clan_battle/request_report — 房友请求战报  
    Body: {  
        "qq": "请求人QQ",  
        "name": "请求人名称",  
        "type": "current" | "today" | "yesterday" | "personal",  
        "player_name": "游戏名称（personal类型必填）"  
    }  
    将请求存入待处理队列，监控端轮询时获取并执行  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        data = await request.json()  
        qq = data.get("qq", "")  
        name = data.get("name", "")  
        report_type = data.get("type", "current")  
        player_name = data.get("player_name", "")  
  
        cb = room_manager._get_or_create_clan_battle(room_id)  
  
        if not cb.monitor_status:  
            return web.json_response({"error": "监控未开启，无法请求战报"}, status=400)  
  
        type_names = {  
            "current": "当前战报",  
            "today": "今日出刀",  
            "yesterday": "昨日出刀",  
            "personal": f"{player_name}的战报",  
        }  
        title = type_names.get(report_type, "战报")  
  
        room_manager.add_message(  
            room_id, qq, name,  
            f"请求查看【{title}】"  
        )  
  
        # 存入待处理队列  
        if not hasattr(cb, 'pending_reports'):  
            cb.pending_reports = []  
        cb.pending_reports.append({  
            "qq": qq,  
            "name": name,  
            "type": report_type,  
            "player_name": player_name,  
            "time": int(time.time()),  
        })  
  
        return web.json_response({  
            "success": True,  
            "message": f"已请求{title}，等待监控端响应..."  
        })  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
async def poll_report_requests(request):  
    """  
    GET /rooms/{room_id}/clan_battle/pending_reports?qq=监控人QQ  
    监控端轮询获取待处理的战报请求  
    """  
    try:  
        room_id = request.match_info["room_id"]  
        if room_id not in room_manager.rooms:  
            return web.json_response({"error": "房间不存在"}, status=404)  
  
        qq = request.query.get("qq", "")  
        cb = room_manager._get_or_create_clan_battle(room_id)  
  
        if cb.monitor_qq != qq:  
            return web.json_response({"error": "你不是当前监控人"}, status=403)  
  
        pending = getattr(cb, 'pending_reports', [])  
        # 取出后清空  
        cb.pending_reports = []  
  
        return web.json_response({"requests": pending})  
    except Exception as e:  
        return web.json_response({"error": str(e)}, status=400)  
  
  
# ==================== 路由注册 ====================  
def setup_api_routes(app: web.Application):  
    # 原有房间 API  
    app.router.add_get('/rooms', get_rooms)  
    app.router.add_post('/rooms', create_room)  
    app.router.add_post('/rooms/join', join_room)  
    app.router.add_post('/rooms/leave', leave_room)  
    app.router.add_post('/rooms/dismiss', dismiss_room)  
    app.router.add_get('/rooms/{room_id}/messages', get_messages)  
    app.router.add_post('/rooms/{room_id}/messages', send_message)  
  
    # 会战 API  
    app.router.add_get('/rooms/{room_id}/clan_battle', get_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/update', update_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/monitor', monitor_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/apply', apply_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/tree', tree_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/subscribe', subscribe_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/sl', sl_clan_battle)  
    app.router.add_get('/rooms/{room_id}/clan_battle/report', report_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/report', report_clan_battle)  
    app.router.add_post('/rooms/{room_id}/clan_battle/request_report', request_report)  
    app.router.add_get('/rooms/{room_id}/clan_battle/pending_reports', poll_report_requests)  
  
  
# ==================== HTTP 服务器启动 ====================  
http_runner = None  
http_site = None  
  
  
async def start_http_server():  
    global http_runner, http_site  
    port = getattr(config, 'ROOM_HTTP_PORT', 8066)  
  
    app = web.Application()  
    setup_api_routes(app)  
  
    http_runner = web.AppRunner(app)  
    await http_runner.setup()  
  
    http_site = web.TCPSite(http_runner, '0.0.0.0', port)  
    await http_site.start()  
  
    print(f"[房间插件] HTTP服务器已启动，端口: {port}")  
    print(f"[房间插件] API地址: http://0.0.0.0:{port}/rooms")  
    print(f"[房间插件] 会战API: http://0.0.0.0:{port}/rooms/{{room_id}}/clan_battle")  
  
  
async def stop_http_server():  
    global http_runner, http_site  
    if http_site:  
        await http_site.stop()  
    if http_runner:  
        await http_runner.cleanup()  
    print("[房间插件] HTTP服务器已停止")  
  
  
# ==================== Hoshino 服务 ====================  
sv = Service("room_server", enable_on_default=True, help_="房间HTTP服务器（含会战监控）")  
  
  
try:  
    loop = asyncio.get_event_loop()  
    loop.create_task(start_http_server())  
except Exception as e:  
    print(f"[房间插件] 启动失败: {e}")ng room.py…]()

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
