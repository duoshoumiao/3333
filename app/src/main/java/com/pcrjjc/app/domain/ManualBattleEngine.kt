package com.pcrjjc.app.domain  
  
import com.pcrjjc.app.data.local.entity.*  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
import org.json.JSONArray  
import org.json.JSONObject  
import java.net.HttpURLConnection  
import java.net.URL  
import java.util.Calendar  
import java.util.TimeZone  
  
/**  
 * 手动报刀引擎，移植自 yobot_remix-stable  
 *   realize.py  — 报刀/尾刀/撤销/申请/挂树/预约/SL/查树/出刀记录/业绩表/修改boss/重置  
 *   kernel.py   — 指令匹配（已转为按钮调用，不再需要正则）  
 *   handler.py  — 预约处理  
 *   settings.py — 自动获取boss数据  
 *  
 * 设计原则：  
 *   所有操作都是纯函数 (state, params) -> Result(newState, message)  
 *   不持有任何可变状态，方便 ViewModel 使用和房间消息同步  
 */  
object ManualBattleEngine {  
  
    // ======================== 返回类型 ========================  
  
    data class Result(  
        val state: ManualBattleState,  
        val message: String  
    )  
  
    // ======================== PCR 日期工具 ========================  
  
    /**  
     * 获取 PCR 日期（以凌晨5点为分界）  
     * 移植自 yobot util.py pcr_datetime()  
     * @param gameServer "cn" / "jp" / "tw"  
     * @return pcrDate 格式 yyyyMMdd 的整数  
     */  
    fun getPcrDate(gameServer: String = "cn"): Int {  
        val tz = when (gameServer) {  
            "jp" -> TimeZone.getTimeZone("Asia/Tokyo")  
            "tw" -> TimeZone.getTimeZone("Asia/Taipei")  
            else -> TimeZone.getTimeZone("Asia/Shanghai")  
        }  
        val cal = Calendar.getInstance(tz)  
        // PCR 以凌晨5点为日期分界  
        if (cal.get(Calendar.HOUR_OF_DAY) < 5) {  
            cal.add(Calendar.DAY_OF_MONTH, -1)  
        }  
        val y = cal.get(Calendar.YEAR)  
        val m = cal.get(Calendar.MONTH) + 1  
        val d = cal.get(Calendar.DAY_OF_MONTH)  
        return y * 10000 + m * 100 + d  
    }  
  
    // ======================== 创建公会 ========================  
  
    /**  
     * 创建公会  
     * 移植自 realize.py create_group()  
     */  
    fun createGuild(  
        state: ManualBattleState,  
        gameServer: String = "cn"  
    ): Result {  
        if (state.isCreated) {  
            return Result(state, "公会已经存在")  
        }  
        val level = BossConfig.levelByCycle(1, gameServer)  
        val bosses = (0 until 5).map { i ->  
            val hp = BossConfig.getFullHp(gameServer, level, i)  
            ManualBossState(  
                bossNum = i + 1,  
                currentHp = hp,  
                maxHp = hp,  
                cycle = 1,  
                isNext = false  
            )  
        }  
        val newState = state.copy(  
            isCreated = true,  
            gameServer = gameServer,  
            bossCycle = 1,  
            bosses = bosses,  
            challengingMembers = emptyList(),  
            records = emptyList(),  
            subscribes = emptyList(),  
            lastUpdateTime = System.currentTimeMillis()  
        )  
        return Result(newState, "公会创建成功（${gameServer}服）")  
    }  
  
    // ======================== 加入公会 ========================  
  
    /**  
     * 加入公会  
     * 移植自 realize.py bind_group()  
     */  
    fun joinGuild(  
        state: ManualBattleState,  
        playerQq: String,  
        playerName: String  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (state.isMember(playerQq)) return Result(state, "${playerName}已经是公会成员了")  
        val newMembers = state.members + GuildMember(playerQq, playerName)  
        return Result(  
            state.copy(members = newMembers, lastUpdateTime = System.currentTimeMillis()),  
            "${playerName}已加入本公会"  
        )  
    }  
  
    // ======================== 申请出刀 ========================  
  
    /**  
     * 申请出刀  
     * 移植自 realize.py apply_for_challenge()  
     */  
    fun applyForChallenge(  
        state: ManualBattleState,  
        playerQq: String,  
        playerName: String,  
        bossNum: Int,  
        isContinue: Boolean = false,  
        behalfQq: String? = null,  
        behalfName: String? = null  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
  
        val challenger = behalfQq ?: playerQq  
        val challengerName = behalfName ?: playerName  
  
        if (!state.isMember(challenger)) return Result(state, "请先加入公会")  
        if (state.hasApplied(challenger)) return Result(state, "你已经申请过了 (╯‵□′)╯︵┻━┻")  
  
        if (bossNum < 1 || bossNum > 5) return Result(state, "Boss编号必须在1-5之间")  
  
        // 检查boss是否可挑战  
        val boss = state.bosses.getOrNull(bossNum - 1) ?: return Result(state, "Boss不存在")  
        if (boss.currentHp <= 0 && !canChallengeNextBoss(state, bossNum)) {  
            return Result(state, "只能挑战2个周目内且不跨阶段的同个boss")  
        }  
  
        // 检查今日出刀次数  
        val todayPcrDate = getPcrDate(state.gameServer)  
        val todayRecords = state.records.filter { it.playerQq == challenger && it.pcrDate == todayPcrDate }  
        val finished = todayRecords.count { it.bossHealthRemain > 0 || it.isContinue }  
        if (finished >= 3) return Result(state, "今日已出了3次完整刀")  
  
        // 检查补偿刀  
        val allContBlade = todayRecords.count { it.isContinue }  
        val contBlade = todayRecords.size - finished - allContBlade  
        if (isContinue && contBlade == 0) return Result(state, "您没有补偿刀")  
  
        // 自动判断是否应该是补偿刀  
        val tailBlade = todayRecords.count { it.bossHealthRemain == 0L && !it.isContinue }  
        val actualIsContinue = if (!isContinue && finished + tailBlade - allContBlade >= 3 && contBlade != 0) true else isContinue  
  
        val newMember = ChallengingMember(  
            playerQq = challenger,  
            playerName = challengerName,  
            bossNum = bossNum,  
            isContinue = actualIsContinue,  
            behalf = if (behalfQq != null) playerQq else null,  
            behalfName = if (behalfQq != null) playerName else null  
        )  
        val newState = state.copy(  
            challengingMembers = state.challengingMembers + newMember,  
            lastUpdateTime = System.currentTimeMillis()  
        )  
        val info = buildString {  
            appendLine("${challengerName}已开始挑战${bossNum}王")  
            appendLine(challengerInfoSmall(newState, bossNum))  
        }  
        return Result(newState, info.trim())  
    }  
  
    // ======================== 取消申请出刀 ========================  
  
    /**  
     * 取消申请出刀  
     * 移植自 realize.py cancel_blade()  
     */  
    fun cancelApply(  
        state: ManualBattleState,  
        playerQq: String,  
        cancelAll: Boolean = false  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (cancelAll) {  
            return Result(  
                state.copy(challengingMembers = emptyList(), lastUpdateTime = System.currentTimeMillis()),  
                "已取消所有申请出刀"  
            )  
        }  
        if (!state.hasApplied(playerQq)) {  
            return Result(state, "你都没申请出刀，取啥子消啊 (╯‵□′)╯︵┻━┻")  
        }  
        val newMembers = state.challengingMembers.filter { it.playerQq != playerQq }  
        return Result(  
            state.copy(challengingMembers = newMembers, lastUpdateTime = System.currentTimeMillis()),  
            "取消申请出刀成功"  
        )  
    }  
  
    // ======================== 报刀（核心） ========================  
  
    /**  
     * 报刀 / 尾刀  
     * 移植自 realize.py challenge()  
     *  
     * @param defeat 是否击败boss（尾刀）  
     * @param damage 伤害值（尾刀时可为0，自动计算）  
     * @param bossNum 指定boss编号（1-5），0表示自动从申请中获取  
     * @param isContinue 是否补偿刀  
     * @param behalfQq 代刀人QQ  
     * @param previousDay 是否记录为昨日  
     */  
    fun challenge(  
        state: ManualBattleState,  
        playerQq: String,  
        playerName: String,  
        defeat: Boolean,  
        damage: Long = 0,  
        bossNum: Int = 0,  
        isContinue: Boolean = false,  
        behalfQq: String? = null,  
        behalfName: String? = null,  
        previousDay: Boolean = false  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
  
        // 确定真正出刀的人  
        val realQq: String  
        val realName: String  
        val behalf: String?  
        val behalfNik: String?  
        if (behalfQq != null) {  
            realQq = behalfQq  
            realName = behalfName ?: behalfQq  
            behalf = playerQq  
            behalfNik = playerName  
        } else {  
            realQq = playerQq  
            realName = playerName  
            behalf = null  
            behalfNik = null  
        }  
  
        if (!state.isMember(realQq)) return Result(state, "请先加入公会")  
  
        // 确定boss编号  
        var actualBossNum = bossNum  
        var workingState = state  
  
        // 若已申请出刀且指定了boss，先取消旧申请  
        if (actualBossNum > 0 && state.hasApplied(realQq)) {  
            workingState = workingState.copy(  
                challengingMembers = workingState.challengingMembers.filter { it.playerQq != realQq }  
            )  
        }  
        // 若未指定boss，从申请中获取  
        if (actualBossNum == 0) {  
            actualBossNum = state.getAppliedBossNum(realQq)  
        }  
        if (actualBossNum == 0) {  
            return Result(state, "又不申请出刀又不说打哪个王，报啥子刀啊 (╯‵□′)╯︵┻━┻")  
        }  
  
        // 如果没有申请出刀，自动申请  
        if (!workingState.hasApplied(realQq)) {  
            val applyResult = applyForChallenge(  
                workingState, if (behalf != null) behalf else realQq,  
                if (behalf != null) (behalfNik ?: behalf) else realName,  
                actualBossNum, isContinue,  
                if (behalf != null) realQq else null,  
                if (behalf != null) realName else null  
            )  
            if (applyResult.message.contains("已出了3次") || applyResult.message.contains("没有补偿刀")) {  
                return Result(state, applyResult.message)  
            }  
            workingState = applyResult.state  
        }  
  
        // 获取boss状态  
        val bossIdx = actualBossNum - 1  
        val boss = workingState.bosses[bossIdx]  
        var bossCycle = workingState.bossCycle  
  
        // 确定实际血量（当前周目或下一周目）  
        var realHp = boss.currentHp  
        if (boss.currentHp <= 0 && boss.isNext) {  
            // 下一周目的boss  
            bossCycle += 1  
            realHp = boss.maxHp // 下周目满血  
        } else if (boss.currentHp <= 0) {  
            return Result(state, "只能挑战2个周目内的同个boss")  
        }  
  
        // 获取补偿刀状态  
        val challengingMember = workingState.challengingMembers.firstOrNull { it.playerQq == realQq }  
        val actualIsContinue = isContinue || (challengingMember?.isContinue ?: false)  
  
        // 验证伤害  
        if (!defeat && damage <= 0) return Result(state, "未击败boss需要提供伤害值")  
        if (!defeat && damage < 0) return Result(state, "伤害不可以是负数")  
        if (!defeat && damage >= realHp) return Result(state, "伤害超出剩余血量，如击败请使用尾刀")  
  
        // 检查今日出刀次数  
        var todayPcrDate = getPcrDate(workingState.gameServer)  
        if (previousDay) {  
            val todayCount = workingState.records.count { it.pcrDate == todayPcrDate }  
            if (todayCount != 0) return Result(state, "今日报刀记录不为空，无法将记录添加到昨日")  
            todayPcrDate -= 1  
        }  
  
        val todayRecords = workingState.records.filter { it.playerQq == realQq && it.pcrDate == todayPcrDate }  
        val finished = todayRecords.count { it.bossHealthRemain > 0 || it.isContinue }  
        if (finished >= 3) {  
            return Result(state, if (previousDay) "昨日上报次数已达到3次" else "今日上报次数已达到3次")  
        }  
        val allContBlade = todayRecords.count { it.isContinue }  
        val contBlade = todayRecords.size - finished - allContBlade  
        if (actualIsContinue && contBlade == 0) return Result(state, "您没有补偿刀")  
  
        // 计算伤害和剩余血量  
        val challengeDamage: Long  
        val bossHealthRemain: Long  
        if (defeat) {  
            challengeDamage = realHp  
            bossHealthRemain = 0  
        } else {  
            challengeDamage = damage  
            bossHealthRemain = realHp - damage  
        }  
  
        // 创建出刀记录  
        val record = ManualChallengeRecord(  
            id = workingState.nextRecordId,  
            playerQq = realQq,  
            playerName = realName,  
            pcrDate = todayPcrDate,  
            bossCycle = bossCycle,  
            bossNum = actualBossNum,  
            bossHealthRemain = bossHealthRemain,  
            challengeDamage = challengeDamage,  
            isContinue = actualIsContinue,  
            behalf = behalf,  
            behalfName = behalfNik  
        )  
  
        // 更新boss血量  
        val newBosses = workingState.bosses.toMutableList()  
        if (defeat) {  
			if (canChallengeNextBoss(workingState, actualBossNum)) {  
				val nextCycle = workingState.bossCycle + 1  
				val nextLevel = BossConfig.levelByCycle(nextCycle, workingState.gameServer)  
				val nextFullHp = BossConfig.getFullHp(workingState.gameServer, nextLevel, bossIdx)  
				newBosses[bossIdx] = newBosses[bossIdx].copy(  
					currentHp = nextFullHp,  
					maxHp = nextFullHp,  
					cycle = nextCycle,  
					isNext = true  
				)  
			} else {  
				newBosses[bossIdx] = newBosses[bossIdx].copy(currentHp = 0, isNext = false)  
			}  
		} else {  
			newBosses[bossIdx] = newBosses[bossIdx].copy(currentHp = bossHealthRemain)  
		}  
  
        var newCycle = workingState.bossCycle  
  
        // 击败boss后检查是否全部击杀  
        if (defeat) {  
            val allClear = newBosses.count { it.currentHp <= 0L || it.isNext }
            if (allClear == 5) {  
                // 进入下一周目  
                newCycle += 1  
                val nextLevel = BossConfig.levelByCycle(newCycle, workingState.gameServer)  
                for (i in 0 until 5) {  
                    val fullHp = BossConfig.getFullHp(workingState.gameServer, nextLevel, i)  
                    newBosses[i] = ManualBossState(  
                        bossNum = i + 1,  
                        currentHp = fullHp,  
                        maxHp = fullHp,  
                        cycle = newCycle,  
                        isNext = false  
                    )  
                }  
            }  
        }  
  
        // 取消申请出刀  
        var newChallengingMembers = workingState.challengingMembers  
        if (defeat) {  
            // 击败boss：取消该boss上所有挂树的人的挂树状态（通知下树），然后取消自己的申请  
            newChallengingMembers = newChallengingMembers.filter { it.playerQq != realQq }  
        } else {  
            newChallengingMembers = newChallengingMembers.filter { it.playerQq != realQq }  
        }  
  
        // 击败boss后触发预约提醒（移除该boss的预约）  
        var newSubscribes = workingState.subscribes  
        var subscribeMsg = ""  
        if (defeat) {  
            val subs = newSubscribes.filter { it.bossNum == actualBossNum }  
            if (subs.isNotEmpty()) {  
                subscribeMsg = "\n船新的${actualBossNum}王来惹~ 预约提醒：${subs.joinToString("、") { it.playerName }}"  
                newSubscribes = newSubscribes.filter { it.bossNum != actualBossNum }  
            }  
        }  
  
        val newState = workingState.copy(  
            bossCycle = newCycle,  
            bosses = newBosses,  
            records = workingState.records + record,  
            challengingMembers = newChallengingMembers,  
            subscribes = newSubscribes,  
            nextRecordId = workingState.nextRecordId + 1,  
            lastUpdateTime = System.currentTimeMillis()  
        )  
  
        // 生成消息  
        val behalfStr = if (behalf != null) "（${behalfNik ?: behalf}代）" else ""  
        val msg = if (defeat) {  
            val newContBlade = if (actualIsContinue) contBlade - 1 else contBlade + 1  
            val newFinished = if (actualIsContinue) finished else finished + 1  
            val bladeType = if (actualIsContinue) "尾余刀" else "收尾刀"  
            "${realName}${behalfStr}对${actualBossNum}号boss造成了${formatDamage(challengeDamage)}点伤害，击败了boss\n" +  
                "（今日已完成${newFinished}刀，还有补偿刀${newContBlade}刀，本刀是${bladeType}）" +  
                subscribeMsg  
        } else {  
            val newContBlade = if (actualIsContinue) contBlade - 1 else contBlade  
            val bladeType = if (actualIsContinue) "剩余刀" else "完整刀"  
            "${realName}${behalfStr}对${actualBossNum}号boss造成了${formatDamage(challengeDamage)}点伤害\n" +  
                "（今日已出完整刀${finished + 1}刀，还有补偿刀${newContBlade}刀，本刀是${bladeType}）"  
        }  
  
        return Result(newState, msg)  
    }  
  
    // ======================== 撤销 ========================  
  
    /**  
     * 撤销上一刀  
     * 移植自 realize.py undo()  
     */  
    fun undo(state: ManualBattleState, playerQq: String): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (state.records.isEmpty()) return Result(state, "本群无出刀记录")  
  
        val lastRecord = state.records.last()  
  
        // 恢复boss血量  
        val newBosses = state.bosses.toMutableList()  
        val bossIdx = lastRecord.bossNum - 1  
        var newCycle = state.bossCycle  
  
        if (lastRecord.bossCycle < state.bossCycle) {  
            // 被撤销的一刀是切换周目的一刀，需要回退周目  
            newCycle = lastRecord.bossCycle  
            val level = BossConfig.levelByCycle(newCycle, state.gameServer)  
            // 当前周目的boss血量全部设为0，被撤销的boss恢复伤害  
            for (i in 0 until 5) {  
                val fullHp = BossConfig.getFullHp(state.gameServer, level, i)  
                newBosses[i] = if (i == bossIdx) {  
                    ManualBossState(  
                        bossNum = i + 1,  
                        currentHp = lastRecord.challengeDamage,  
                        maxHp = fullHp,  
                        cycle = newCycle  
                    )  
                } else {  
                    ManualBossState(bossNum = i + 1, currentHp = 0, maxHp = fullHp, cycle = newCycle)  
                }  
            }  
        } else {  
            // 同周目撤销  
            val level = BossConfig.levelByCycle(lastRecord.bossCycle, state.gameServer)  
            val fullHp = BossConfig.getFullHp(state.gameServer, level, bossIdx)  
            val restoredHp = minOf(  
                newBosses[bossIdx].currentHp + lastRecord.challengeDamage,  
                fullHp  
            )  
            newBosses[bossIdx] = newBosses[bossIdx].copy(currentHp = restoredHp)  
        }  
  
        val newRecords = state.records.dropLast(1)  
        val newState = state.copy(  
            bossCycle = newCycle,  
            bosses = newBosses,  
            records = newRecords,  
            lastUpdateTime = System.currentTimeMillis()  
        )  
        return Result(newState, "${lastRecord.playerName}的出刀记录已被撤销")  
    }  
  
    // ======================== 挂树 ========================  
  
    /**  
     * 挂树  
     * 移植自 realize.py put_on_the_tree()  
     */  
    fun putOnTree(  
        state: ManualBattleState,  
        playerQq: String,  
        playerName: String,  
        bossNum: Int = 0,  
        message: String? = null  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (!state.isMember(playerQq)) return Result(state, "请先加入公会")  
  
        // 确定挂树的boss  
        val actualBossNum = if (bossNum > 0) bossNum else state.getAppliedBossNum(playerQq)  
        if (actualBossNum == 0) {  
            return Result(state, "你既没申请出刀，也没说挂哪个，挂啥子树啊 (╯‵□′)╯︵┻━┻")  
        }  
  
        if (state.isOnTree(playerQq)) {  
            return Result(state, "您已经在树上了")  
        }  
  
        // 如果没有申请出刀，自动申请  
        var workingState = state  
        if (!state.hasApplied(playerQq)) {  
            val applyResult = applyForChallenge(state, playerQq, playerName, actualBossNum)  
            if (applyResult.message.contains("已出了3次") || applyResult.message.contains("下班了")) {  
                return Result(state, "你今天都下班了，挂啥子树啊 (╯‵□′)╯︵┻━┻")  
            }  
            workingState = applyResult.state  
        }  
  
        // 更新挂树状态  
        val newMembers = workingState.challengingMembers.map {  
            if (it.playerQq == playerQq) it.copy(isOnTree = true, treeMessage = message)  
            else it  
        }  
        return Result(  
            workingState.copy(challengingMembers = newMembers, lastUpdateTime = System.currentTimeMillis()),  
            "${playerName}挂树惹~ (っ °Д °;)っ"  
        )  
    }  
  
    /**  
     * 下树  
     * 移植自 realize.py take_it_of_the_tree()  
     */  
    fun takeOffTree(state: ManualBattleState, playerQq: String): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (!state.isOnTree(playerQq)) {  
            return Result(state, "你都没挂树，下啥子树啊 (╯‵□′)╯︵┻━┻")  
        }  
        val newMembers = state.challengingMembers.map {  
            if (it.playerQq == playerQq) it.copy(isOnTree = false, treeMessage = null)  
            else it  
        }  
        return Result(  
            state.copy(challengingMembers = newMembers, lastUpdateTime = System.currentTimeMillis()),  
            "下树惹~ _(:з)∠)_"  
        )  
    }  
  
// ======================== 查树 ========================  
  
    /**  
     * 查树  
     * 移植自 realize.py query_tree()  
     */  
    fun queryTree(state: ManualBattleState, bossNum: Int = 0): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        val sb = StringBuilder()  
        var hasTree = false  
        if (bossNum == 0) {  
            for (i in 1..5) {  
                val trees = state.getTreesForBoss(i)  
                if (trees.isNotEmpty()) {  
                    hasTree = true  
                    sb.appendLine("${i}王挂树的成员：")  
                    trees.forEach { t ->  
                        sb.appendLine("  ${t.playerName}${if (t.treeMessage != null) "：${t.treeMessage}" else ""}")  
                    }  
                }  
            }  
            if (!hasTree) sb.append("当前在任意Boss上无人挂树")  
        } else {  
            val trees = state.getTreesForBoss(bossNum)  
            sb.appendLine(challengerInfoSmall(state, bossNum))  
            if (trees.isEmpty()) {  
                sb.appendLine("没有成员在${bossNum}王挂树")  
            } else {  
                sb.appendLine("${bossNum}王挂树的成员：")  
                trees.forEach { t ->  
                    sb.appendLine("  ${t.playerName}${if (t.treeMessage != null) "：${t.treeMessage}" else ""}")  
                }  
            }  
        }  
        return Result(state, sb.toString().trim())  
    }  
  
    // ======================== 预约 ========================  
  
    /**  
     * 预约某个boss  
     * 移植自 realize.py subscribe()  
     */  
    fun subscribe(  
        state: ManualBattleState,  
        playerQq: String,  
        playerName: String,  
        bossNum: Int,  
        note: String = ""  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (!state.isMember(playerQq)) return Result(state, "请先加入公会")  
        if (bossNum < 1 || bossNum > 5) return Result(state, "Boss编号必须在1-5之间")  
        if (state.hasSubscribed(playerQq, bossNum)) {  
            return Result(state, "你已经预约过这个boss啦 (╯‵□′)╯︵┻━┻")  
        }  
        val newSub = ManualSubscribeRecord(playerQq, playerName, bossNum, note)  
        val newState = state.copy(  
            subscribes = state.subscribes + newSub,  
            lastUpdateTime = System.currentTimeMillis()  
        )  
        return Result(newState, "预约${bossNum}王成功！下个${bossNum}王出现时会提醒。")  
    }  
  
    /**  
     * 取消预约  
     * 移植自 realize.py subscribe_cancel()  
     */  
    fun cancelSubscribe(  
        state: ManualBattleState,  
        playerQq: String,  
        bossNum: Int  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (bossNum < 1 || bossNum > 5) return Result(state, "Boss编号必须在1-5之间")  
        if (!state.hasSubscribed(playerQq, bossNum)) {  
            return Result(state, "您还没有预约这个boss")  
        }  
        val newSubs = state.subscribes.filter { !(it.playerQq == playerQq && it.bossNum == bossNum) }  
        return Result(  
            state.copy(subscribes = newSubs, lastUpdateTime = System.currentTimeMillis()),  
            "取消预约${bossNum}王成功~"  
        )  
    }  
  
    /**  
     * 预约表  
     * 移植自 realize.py subscribe() msg=='表'  
     */  
    fun subscribeTable(state: ManualBattleState): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (state.subscribes.isEmpty()) return Result(state, "目前没有人预约任意一个Boss")  
        val sb = StringBuilder("预约表：\n")  
        for (i in 1..5) {  
            val subs = state.getSubscribesForBoss(i)  
            if (subs.isNotEmpty()) {  
                sb.appendLine("===${i}号Boss===")  
                subs.forEach { s ->  
                    sb.appendLine("${s.playerName}${if (s.note.isNotBlank()) "：${s.note}" else ""}")  
                }  
            }  
        }  
        sb.append("============")  
        return Result(state, sb.toString())  
    }  
  
    // ======================== SL ========================  
  
    /**  
     * 记录SL  
     * 移植自 realize.py save_slot()  
     */  
    fun saveSlot(  
        state: ManualBattleState,  
        playerQq: String,  
        playerName: String,  
        onlyCheck: Boolean = false,  
        cleanFlag: Boolean = false  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (!state.isMember(playerQq)) return Result(state, "请先加入公会")  
  
        val todayPcrDate = getPcrDate(state.gameServer)  
  
        if (cleanFlag) {  
            if (!state.hasSLToday(playerQq, todayPcrDate)) {  
                return Result(state, "您今天还没有SL过")  
            }  
            val newMembers = state.members.map {  
                if (it.playerQq == playerQq) it.copy(lastSaveSlot = 0) else it  
            }  
            return Result(  
                state.copy(members = newMembers, lastUpdateTime = System.currentTimeMillis()),  
                "已取消SL。若已申请/挂树，需重新报告。"  
            )  
        }  
  
        if (onlyCheck) {  
            val slEd = state.hasSLToday(playerQq, todayPcrDate)  
            return Result(state, if (slEd) "今日已使用SL" else "今日未使用SL")  
        }  
  
        if (state.hasSLToday(playerQq, todayPcrDate)) {  
            return Result(state, "您今天已经SL过了，该不会退游戏了吧？ Σ(っ °Д °;)っ")  
        }  
  
        // 记录SL  
        var newMembers = state.members.map {  
            if (it.playerQq == playerQq) it.copy(lastSaveSlot = todayPcrDate) else it  
        }  
  
        // SL后自动下树和取消申请  
        var newChallengingMembers = state.challengingMembers  
        if (state.isOnTree(playerQq)) {  
            newChallengingMembers = newChallengingMembers.map {  
                if (it.playerQq == playerQq) it.copy(isOnTree = false, treeMessage = null) else it  
            }  
        }  
        if (state.hasApplied(playerQq)) {  
            newChallengingMembers = newChallengingMembers.filter { it.playerQq != playerQq }  
        }  
  
        return Result(  
            state.copy(  
                members = newMembers,  
                challengingMembers = newChallengingMembers,  
                lastUpdateTime = System.currentTimeMillis()  
            ),  
            "已记录SL。若已申请/挂树，需重新报告。 Σ(っ °Д °;)っ"  
        )  
    }  
  
    // ======================== 报伤害 ========================  
  
    /**  
     * 报伤害（暂停报伤害）  
     * 移植自 realize.py report_hurt()  
     *  
     * @param seconds 剩余秒数  
     * @param damage 伤害（万）  
     * @param cleanType 0=记录 1=清除特定玩家  
     */  
    fun reportHurt(  
        state: ManualBattleState,  
        playerQq: String,  
        seconds: Int,  
        damage: Long,  
        cleanType: Int = 0  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (!state.hasApplied(playerQq)) {  
            return Result(state, "你都没申请出刀，报啥子伤害啊 (╯‵□′)╯︵┻━┻")  
        }  
  
        val newMembers = state.challengingMembers.map {  
            if (it.playerQq == playerQq) {  
                if (cleanType == 0) {  
                    it.copy(seconds = seconds, reportedDamage = damage)  
                } else {  
                    if (it.reportedDamage == 0L) return Result(state, "您还没有报伤害呢")  
                    it.copy(seconds = 0, reportedDamage = 0)  
                }  
            } else it  
        }  
  
        val msg = if (cleanType == 0) "已记录伤害，小心不要手滑哦~ ♪(´▽｀)" else "取消成功~"  
        return Result(  
            state.copy(challengingMembers = newMembers, lastUpdateTime = System.currentTimeMillis()),  
            msg  
        )  
    }  
  
    // ======================== 出刀记录 ========================  
  
    /**  
     * 出刀记录  
     * 移植自 realize.py challenge_record()  
     */  
    fun challengeRecord(state: ManualBattleState): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        val todayPcrDate = getPcrDate(state.gameServer)  
        val sb = StringBuilder()  
  
        var totalBladeNum = 0.0  
        var totalContinueBladeNum = 0  
        val zeroBladeMembers = mutableListOf<String>()  
        val bladeList = mutableMapOf<Double, Int>()  
  
        for (member in state.members) {  
            val memberRecords = state.records.filter {  
                it.playerQq == member.playerQq && it.pcrDate == todayPcrDate  
            }  
            if (memberRecords.isNotEmpty()) {  
                var memberNum = 0.0  
                var continueBladeNum = 0  
                for (c in memberRecords) {  
                    if (c.bossHealthRemain == 0L && !c.isContinue) {  
                        // 完整刀收尾算0.5刀  
                        memberNum += 0.5  
                        continueBladeNum += 1  
                    } else if (c.isContinue) {  
                        // 补偿刀算0.5刀  
                        memberNum += 0.5  
                        continueBladeNum -= 1  
                    } else {  
                        memberNum += 1.0  
                    }  
                }  
                totalBladeNum += memberNum  
                totalContinueBladeNum += continueBladeNum  
                bladeList[memberNum] = (bladeList[memberNum] ?: 0) + 1  
            } else {  
                zeroBladeMembers.add(member.playerName)  
            }  
        }  
  
        sb.appendLine("待出补偿刀数量：$totalContinueBladeNum")  
        sb.appendLine("已出0刀的成员数量：${zeroBladeMembers.size}")  
        for ((i, name) in zeroBladeMembers.withIndex()) {  
            val prefix = if (i == zeroBladeMembers.size - 1) "┖" else "┣"  
            sb.appendLine("${prefix}${name}")  
        }  
        for ((bladeNum, count) in bladeList.entries.sortedBy { it.key }) {  
            val bladeStr = if (bladeNum == bladeNum.toLong().toDouble()) "${bladeNum.toLong()}" else "$bladeNum"  
            sb.appendLine("已出${bladeStr}刀：${count}")  
        }  
        val totalStr = if (totalBladeNum == totalBladeNum.toLong().toDouble()) "${totalBladeNum.toLong()}" else "$totalBladeNum"  
        sb.append("今天已出 ${totalStr}/${state.members.size * 3}")  
  
        return Result(state, sb.toString())  
    }  
  
    // ======================== 业绩表 ========================  
  
    /**  
     * 业绩表（按成员统计总伤害）  
     * 移植自 realize.py score_table() 概念  
     */  
    fun scoreTable(state: ManualBattleState): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (state.records.isEmpty()) return Result(state, "暂无出刀记录")  
  
        val sb = StringBuilder("===== 业绩表 =====\n")  
  
        // 按成员汇总  
        data class MemberScore(  
            val name: String,  
            var totalDamage: Long = 0,  
            var bladeCount: Int = 0  
        )  
  
        val scoreMap = mutableMapOf<String, MemberScore>()  
        for (r in state.records) {  
            val ms = scoreMap.getOrPut(r.playerQq) { MemberScore(r.playerName) }  
            ms.totalDamage += r.challengeDamage  
            ms.bladeCount += 1  
        }  
  
        val sorted = scoreMap.values.sortedByDescending { it.totalDamage }  
        var rank = 1  
        for (ms in sorted) {  
            sb.appendLine("${rank}. ${ms.name}：${formatDamage(ms.totalDamage)}（${ms.bladeCount}刀）")  
            rank++  
        }  
        sb.appendLine("=================")  
        sb.append("总伤害：${formatDamage(sorted.sumOf { it.totalDamage })}")  
  
        return Result(state, sb.toString())  
    }  
  
    // ======================== 修改boss状态 ========================  
  
    /**  
     * 修改boss状态（管理员功能）  
     * 移植自 realize.py modify()  
     */  
    fun modify(  
        state: ManualBattleState,  
        cycle: Int,  
        bossData: List<Pair<Int, Long>>  // [(bossNum, newHp), ...]  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (cycle < 1) return Result(state, "周目数不能为负")  
  
        val level = BossConfig.levelByCycle(cycle, state.gameServer)  
        val newBosses = state.bosses.toMutableList()  
  
        for ((bossNum, newHp) in bossData) {  
            if (bossNum < 1 || bossNum > 5) continue  
            val idx = bossNum - 1  
            val fullHp = BossConfig.getFullHp(state.gameServer, level, idx)  
            newBosses[idx] = ManualBossState(  
                bossNum = bossNum,  
                currentHp = newHp,  
                maxHp = fullHp,  
                cycle = cycle,  
                isNext = false  
            )  
        }  
  
        return Result(  
            state.copy(  
                bossCycle = cycle,  
                bosses = newBosses,  
                lastUpdateTime = System.currentTimeMillis()  
            ),  
            "boss状态已修改"  
        )  
    }  
  
    // ======================== 重置进度 ========================  
  
    /**  
     * 重置进度  
     * 移植自 realize.py switch_data_slot() + kernel.py match_num==20  
     */  
    fun resetProgress(state: ManualBattleState): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (state.records.isEmpty() && state.bossCycle == 1) {  
            return Result(state, "当前档案记录为空，无需重置")  
        }  
  
        val level = BossConfig.levelByCycle(1, state.gameServer)  
        val newBosses = (0 until 5).map { i ->  
            val hp = BossConfig.getFullHp(state.gameServer, level, i)  
            ManualBossState(bossNum = i + 1, currentHp = hp, maxHp = hp, cycle = 1)  
        }  
  
        return Result(  
            state.copy(  
                bossCycle = 1,  
                bosses = newBosses,  
                challengingMembers = emptyList(),  
                records = emptyList(),  
                subscribes = emptyList(),  
                nextRecordId = 1,  
                lastUpdateTime = System.currentTimeMillis()  
            ),  
            "进度已重置"  
        )  
    }  
  
    // ======================== 合刀计算 ========================  
  
    /**  
     * 合刀计算  
     * 给定两刀伤害和boss剩余血量，计算合刀方案  
     */  
    fun combineBlade(damage1: Long, damage2: Long, bossHp: Long): Result {  
        if (damage1 <= 0 || damage2 <= 0 || bossHp <= 0) {  
            return Result(ManualBattleState(), "参数不能为0或负数")  
        }  
        val sb = StringBuilder("===== 合刀计算 =====\n")  
        sb.appendLine("Boss剩余血量：${formatDamage(bossHp)}")  
        sb.appendLine("伤害1：${formatDamage(damage1)}")  
        sb.appendLine("伤害2：${formatDamage(damage2)}")  
        sb.appendLine("--------------------")  
  
        if (damage1 + damage2 < bossHp) {  
            sb.append("两刀伤害之和（${formatDamage(damage1 + damage2)}）不足以击杀boss")  
            return Result(ManualBattleState(), sb.toString())  
        }  
  
        // 方案A：先出伤害1，再出伤害2收尾  
        if (damage1 < bossHp) {  
            val remain = bossHp - damage1  
            val compensation2 = remain  // 伤害2的补偿刀伤害 = 剩余血量（尾刀）  
            sb.appendLine("方案A：先出伤害1（${formatDamage(damage1)}），再出伤害2收尾")  
            sb.appendLine("  伤害2补偿刀可造成：${formatDamage(damage2)}（90秒内）")  
        }  
  
        // 方案B：先出伤害2，再出伤害1收尾  
        if (damage2 < bossHp) {  
            val remain = bossHp - damage2  
            val compensation1 = remain  // 伤害1的补偿刀伤害 = 剩余血量（尾刀）  
            sb.appendLine("方案B：先出伤害2（${formatDamage(damage2)}），再出伤害1收尾")  
            sb.appendLine("  伤害1补偿刀可造成：${formatDamage(damage1)}（90秒内）")  
        }  
  
        // 如果某一刀单独就能击杀  
        if (damage1 >= bossHp) {  
            sb.appendLine("方案C：伤害1单独可击杀boss，伤害2保留为完整刀")  
        }  
        if (damage2 >= bossHp) {  
            sb.appendLine("方案D：伤害2单独可击杀boss，伤害1保留为完整刀")  
        }  
  
        return Result(ManualBattleState(), sb.toString().trim())  
    }  
  
    // ======================== Boss状态总览 ========================  
  
    /**  
     * 生成boss状态总览文本  
     * 移植自 realize.py boss_status_summary() + challenger_info()  
     */  
    fun bossStatusSummary(state: ManualBattleState): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        val sb = StringBuilder()  
        val todayPcrDate = getPcrDate(state.gameServer)  
        val todayRecords = state.records.filter { it.pcrDate == todayPcrDate }  
        val finishCount = todayRecords.count { it.bossHealthRemain > 0L || it.isContinue }  
  
        sb.appendLine("===== 公会战状态 =====")  
        sb.appendLine("服务器：${state.gameServer} | 周目：${state.bossCycle} | 阶段：${state.currentLevelName}")  
        sb.appendLine("今日完整刀：${finishCount}/${state.members.size * 3}")  
        sb.appendLine("----------------------")  
  
        for (i in 0 until 5) {  
            val boss = state.bosses[i]  
            val challengers = state.getChallengersForBoss(i + 1)  
            val trees = state.getTreesForBoss(i + 1)  
            val subs = state.getSubscribesForBoss(i + 1)  
  
            val hpStr = formatDamage(boss.currentHp)  
            val maxHpStr = formatDamage(boss.maxHp)  
            val pct = if (boss.maxHp > 0) (boss.currentHp * 100 / boss.maxHp) else 0  
            val cycleStr = if (boss.isNext) "${boss.cycle}(下)" else "${boss.cycle}"  
  
            sb.appendLine("${i + 1}王 [${cycleStr}周目] ${hpStr}/${maxHpStr} (${pct}%)")  
  
            if (challengers.isNotEmpty()) {  
                sb.append("  挑战中：")  
                sb.appendLine(challengers.joinToString("、") { c ->  
                    buildString {  
                        append(c.playerName)  
                        if (c.isContinue) append("(补)")  
                        if (c.behalf != null) append("(${c.behalfName ?: c.behalf}代)")  
                        if (c.reportedDamage > 0) append("@${c.seconds}s,${c.reportedDamage}w")  
                        if (c.isOnTree) append("(挂树)")  
                    }  
                })  
            }  
            if (trees.isNotEmpty()) {  
                sb.appendLine("  挂树：${trees.joinToString("、") { it.playerName }}")  
            }  
            if (subs.isNotEmpty()) {  
                sb.appendLine("  预约：${subs.joinToString("、") { s -> "${s.playerName}${if (s.note.isNotBlank()) ":${s.note}" else ""}" }}")  
            }  
        }  
        return Result(state, sb.toString().trim())  
    }  
  
    // ======================== 自动获取boss数据 ========================  
  
    /**  
     * 自动获取所有boss数据（除台服）  
     * 移植自 settings.py auto_get_boss_data()  
     * 调用 https://pcr.satroki.tech/api/Quest/GetClanBattleInfos?s={server}  
     *  
     * @return 更新后的 BossConfig 数据和消息  
     */  
    data class FetchBossResult(  
        val bossHp: Map<String, List<List<Long>>>?,  
        val levelByCycle: Map<String, List<List<Int>>>?,  
        val message: String  
    )  
  
    suspend fun fetchBossData(): FetchBossResult = withContext(Dispatchers.IO) {  
        val newBossHp = mutableMapOf<String, List<List<Long>>>()  
        val newLevelByCycle = mutableMapOf<String, List<List<Int>>>()  
        val messages = mutableListOf<String>()  
  
        // 保留台服的默认数据  
        BossConfig.bossHp["tw"]?.let { newBossHp["tw"] = it }  
        BossConfig.levelByCycle["tw"]?.let { newLevelByCycle["tw"] = it }  
  
        for (server in listOf("cn", "jp")) {  
            val apiUrl = "https://pcr.satroki.tech/api/Quest/GetClanBattleInfos?s=$server"  
            try {  
                val url = URL(apiUrl)  
                val conn = url.openConnection() as HttpURLConnection  
                conn.requestMethod = "GET"  
                conn.connectTimeout = 15000  
                conn.readTimeout = 15000  
  
                val responseCode = conn.responseCode  
                if (responseCode != 200) {  
                    messages.add("${server}获取失败：HTTP $responseCode")  
                    // 保留默认数据  
                    BossConfig.bossHp[server]?.let { newBossHp[server] = it }  
                    BossConfig.levelByCycle[server]?.let { newLevelByCycle[server] = it }  
                    continue  
                }  
  
                val responseText = conn.inputStream.bufferedReader().readText()  
                val infos = JSONArray(responseText)  
  
                val cal = Calendar.getInstance()  
                val dYear = cal.get(Calendar.YEAR)  
                val dMonth = cal.get(Calendar.MONTH) + 1  
  
                var successFlag = false  
                for (idx in 0 until infos.length()) {  
                    val info = infos.getJSONObject(idx)  
                    if (info.optInt("year") != dYear || info.optInt("month") != dMonth) continue  
  
                    successFlag = true  
                    val phases = info.getJSONArray("phases")  
                    val serverBossHp = mutableListOf<List<Long>>()  
                    val serverLevelByCycle = mutableListOf<List<Int>>()  
  
                    for (stage in 0 until phases.length()) {  
                        val stageInfo = phases.getJSONObject(stage)  
                        val bosses = stageInfo.getJSONArray("bosses")  
                        val stageHp = mutableListOf<Long>()  
                        for (bossIdx in 0 until bosses.length()) {  
                            val bossInfo = bosses.getJSONObject(bossIdx)  
                            stageHp.add(bossInfo.optLong("hp", 0))  
                        }  
                        serverBossHp.add(stageHp)  
  
                        val lapFrom = stageInfo.optInt("lapFrom", stage + 1)  
                        serverLevelByCycle.add(listOf(lapFrom, 0)) // 临时，后面修正  
                    }  
  
                    // 修正 levelByCycle 的结束周目  
                    val fixedLevelByCycle = mutableListOf<List<Int>>()  
                    for (stage in serverLevelByCycle.indices) {  
                        val start = serverLevelByCycle[stage][0]  
                        val end = if (stage < serverLevelByCycle.size - 1) {  
                            serverLevelByCycle[stage + 1][0] - 1  
                        } else {  
                            999  
                        }  
                        fixedLevelByCycle.add(listOf(start, end))  
                    }  
  
                    newBossHp[server] = serverBossHp  
                    newLevelByCycle[server] = fixedLevelByCycle  
                    break  
                }  
  
                if (successFlag) {  
                    messages.add("${server}更新当期boss数据成功！")  
                } else {  
                    messages.add("${server}更新当期boss数据失败，可能是获取不到当期数据。")  
                    BossConfig.bossHp[server]?.let { newBossHp[server] = it }  
                    BossConfig.levelByCycle[server]?.let { newLevelByCycle[server] = it }  
                }  
  
                conn.disconnect()  
            } catch (e: Exception) {  
                messages.add("${server}更新当期boss数据失败：${e.message}")  
                BossConfig.bossHp[server]?.let { newBossHp[server] = it }  
                BossConfig.levelByCycle[server]?.let { newLevelByCycle[server] = it }  
            }  
        }  
  
        FetchBossResult(  
            bossHp = newBossHp.ifEmpty { null },  
            levelByCycle = newLevelByCycle.ifEmpty { null },  
            message = messages.joinToString("\n")  
        )  
    }  
  
    /**  
     * 将 fetchBossData 的结果应用到 BossConfig  
     * 需要配合 ManualBattleEntities.kt 中 BossConfig 的 var 声明使用  
     */  
    fun applyFetchedBossData(result: FetchBossResult) {  
        result.bossHp?.let { BossConfig.bossHp = it }  
        result.levelByCycle?.let { BossConfig.levelByCycle = it }  
    }  
  
    // ======================== 辅助函数 ========================  
  
    /**  
     * 格式化伤害数值  
     * 例：12345678 -> "1234万5678"  
     *     10000    -> "1万"  
     *     500      -> "500"  
     */  
    fun formatDamage(damage: Long): String {  
        return when {  
            damage >= 100_000_000 -> {  
                val yi = damage / 100_000_000  
                val wan = (damage % 100_000_000) / 10_000  
                val remainder = damage % 10_000  
                buildString {  
                    append("${yi}亿")  
                    if (wan > 0) append("${wan}万")  
                    if (remainder > 0) append(remainder)  
                }  
            }  
            damage >= 10_000 -> {  
                val wan = damage / 10_000  
                val remainder = damage % 10_000  
                if (remainder == 0L) "${wan}万" else "${wan}万${remainder}"  
            }  
            else -> "$damage"  
        }  
    }  
  
    /**  
     * 生成某个boss的简要挑战者信息  
     * 移植自 realize.py challenger_info_small()  
     */  
    private fun challengerInfoSmall(state: ManualBattleState, bossNum: Int): String {  
        val boss = state.bosses.getOrNull(bossNum - 1) ?: return ""  
        val challengers = state.getChallengersForBoss(bossNum)  
        val hpStr = formatDamage(boss.currentHp)  
        val maxHpStr = formatDamage(boss.maxHp)  
        val sb = StringBuilder()  
        sb.append("${bossNum}王 [${boss.cycle}周目] ${hpStr}/${maxHpStr}")  
        if (challengers.isNotEmpty()) {  
            sb.append(" | 挑战中：")  
            sb.append(challengers.joinToString("、") { c ->  
                buildString {  
                    append(c.playerName)  
                    if (c.isContinue) append("(补)")  
                    if (c.isOnTree) append("(树)")  
                    if (c.reportedDamage > 0) append("@${c.seconds}s,${c.reportedDamage}w")  
                }  
            })  
        }  
        return sb.toString()  
    }  
  
    /**  
     * 检查是否可以挑战下一周目的同一个boss  
     * 移植自 realize.py check_next_boss()  
     * 规则：只能挑战2个周目内且不跨阶段的同个boss  
     */  
    private fun canChallengeNextBoss(state: ManualBattleState, bossNum: Int): Boolean {  
        val currentLevel = BossConfig.levelByCycle(state.bossCycle, state.gameServer)  
        val nextLevel = BossConfig.levelByCycle(state.bossCycle + 1, state.gameServer)  
        // 不能跨阶段  
        return currentLevel == nextLevel  
    }  
  
    // ======================== 不打/不进 ========================  
  
    /**  
     * 不打/不进（取消预约+取消申请）  
     * 移植自 kernel.py match_num==14  
     */  
    fun notFight(  
        state: ManualBattleState,  
        playerQq: String,  
        bossNum: Int = 0  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        var newState = state  
  
        // 取消申请出刀  
        if (state.hasApplied(playerQq)) {  
            newState = newState.copy(  
                challengingMembers = newState.challengingMembers.filter { it.playerQq != playerQq }  
            )  
        }  
  
        // 取消预约  
        if (bossNum > 0) {  
            newState = newState.copy(  
                subscribes = newState.subscribes.filter {  
                    !(it.playerQq == playerQq && it.bossNum == bossNum)  
                }  
            )  
        } else {  
            newState = newState.copy(  
                subscribes = newState.subscribes.filter { it.playerQq != playerQq }  
            )  
        }  
  
        return Result(  
            newState.copy(lastUpdateTime = System.currentTimeMillis()),  
            "已取消"  
        )  
    }  
  
    // ======================== 刷新boss血量 ========================  
  
    /**  
     * 刷新boss血量上限（当配置更新后调用）  
     * 根据当前周目和最新的 BossConfig 重新计算 maxHp  
     */  
    fun refreshBossMaxHp(state: ManualBattleState): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        val level = BossConfig.levelByCycle(state.bossCycle, state.gameServer)  
        val newBosses = state.bosses.mapIndexed { idx, boss ->  
            val newMaxHp = BossConfig.getFullHp(state.gameServer, level, idx)  
            boss.copy(  
                maxHp = newMaxHp,  
                currentHp = minOf(boss.currentHp, newMaxHp)  
            )  
        }  
        return Result(  
            state.copy(bosses = newBosses, lastUpdateTime = System.currentTimeMillis()),  
            "boss血量已刷新"  
        )  
    }  
  
    // ======================== 移除成员 ========================  
  
    /**  
     * 移除公会成员  
     */  
    fun removeMember(  
        state: ManualBattleState,  
        playerQq: String  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        if (!state.isMember(playerQq)) return Result(state, "该成员不在公会中")  
        val name = state.members.first { it.playerQq == playerQq }.playerName  
        val newState = state.copy(  
            members = state.members.filter { it.playerQq != playerQq },  
            challengingMembers = state.challengingMembers.filter { it.playerQq != playerQq },  
            subscribes = state.subscribes.filter { it.playerQq != playerQq },  
            lastUpdateTime = System.currentTimeMillis()  
        )  
        return Result(newState, "${name}已被移出公会")  
    }  
  
    // ======================== 今日出刀详情 ========================  
  
    /**  
     * 查看某成员今日出刀详情  
     */  
    fun memberTodayDetail(  
        state: ManualBattleState,  
        playerQq: String  
    ): Result {  
        if (!state.isCreated) return Result(state, "请先创建公会")  
        val todayPcrDate = getPcrDate(state.gameServer)  
        val todayRecords = state.records.filter {  
            it.playerQq == playerQq && it.pcrDate == todayPcrDate  
        }  
        val member = state.members.firstOrNull { it.playerQq == playerQq }  
        val name = member?.playerName ?: playerQq  
  
        if (todayRecords.isEmpty()) return Result(state, "${name}今日暂无出刀记录")  
  
        val sb = StringBuilder("${name}今日出刀详情：\n")  
        for ((idx, r) in todayRecords.withIndex()) {  
            val typeStr = when {  
                r.bossHealthRemain == 0L && !r.isContinue -> "收尾"  
                r.isContinue -> "补偿"  
                else -> "完整"  
            }  
            val behalfStr = if (r.behalf != null) "(${r.behalfName ?: r.behalf}代)" else ""  
            sb.appendLine("${idx + 1}. ${r.bossNum}王 ${formatDamage(r.challengeDamage)} [${typeStr}]${behalfStr}")  
        }  
  
        val finished = todayRecords.count { it.bossHealthRemain > 0 || it.isContinue }  
        val allCont = todayRecords.count { it.isContinue }  
        val contBlade = todayRecords.size - finished - allCont  
        val slEd = state.hasSLToday(playerQq, todayPcrDate)  
        sb.append("完整刀：${finished}/3 | 补偿刀：${contBlade} | SL：${if (slEd) "已用" else "未用"}")  
  
        return Result(state, sb.toString())  
    }  
}