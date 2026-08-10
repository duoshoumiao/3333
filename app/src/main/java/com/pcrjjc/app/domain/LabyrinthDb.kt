package com.pcrjjc.app.domain  
  
import android.content.Context  
import org.json.JSONArray  
import kotlinx.coroutines.Dispatchers  
import kotlinx.coroutines.withContext  
  
/**  
 * 黎明界游戏数据（从 assets/labyrinth/*.json 加载）  
 * 用于 Boss 过滤：block.questId -> wave_group_id -> enemy_id 列表 -> unit_id 集合  
 * 对应 PPPPPP autopcr/module/modules/labyrinth.py 的 _boss_unit_ids  
 */  
class LabyrinthDb(private val context: Context) {  
  
    // 懒加载 + 只解析一次  
    @Volatile private var loaded = false  
  
    private val enterGuild = LinkedHashMap<Int, String>()   // guild_id -> guild_name  
    private val questData = HashMap<Int, Int>()             // quest_id -> wave_group_id  
    private val waveGroup = HashMap<Int, List<Int>>()       // wave_group_id -> enemy_id 列表(非0)  
    private val enemyParam = HashMap<Int, Int>()            // enemy_id -> unit_id  
  
    private fun readJsonArray(path: String): JSONArray {  
        context.assets.open(path).use { input ->  
            val text = input.bufferedReader(Charsets.UTF_8).readText()  
            return JSONArray(text)  
        }  
    }  
  
    @Synchronized  
    private fun ensureLoaded() {  
        if (loaded) return  
  
        readJsonArray("labyrinth/labyrinth_enter_guild.json").let { arr ->  
            for (i in 0 until arr.length()) {  
                val o = arr.getJSONObject(i)  
                enterGuild[o.getInt("guild_id")] = o.getString("guild_name")  
            }  
        }  
        readJsonArray("labyrinth/labyrinth_quest_data.json").let { arr ->  
            for (i in 0 until arr.length()) {  
                val o = arr.getJSONObject(i)  
                questData[o.getInt("quest_id")] = o.getInt("wave_group_id")  
            }  
        }  
        readJsonArray("labyrinth/labyrinth_wave_group_data.json").let { arr ->  
            for (i in 0 until arr.length()) {  
                val o = arr.getJSONObject(i)  
                val ids = (1..5).mapNotNull { n ->  
                    val e = o.optInt("enemy_id_$n", 0)  
                    if (e != 0) e else null  
                }  
                waveGroup[o.getInt("wave_group_id")] = ids  
            }  
        }  
        readJsonArray("labyrinth/labyrinth_enemy_parameter.json").let { arr ->  
            for (i in 0 until arr.length()) {  
                val o = arr.getJSONObject(i)  
                enemyParam[o.getInt("enemy_id")] = o.getInt("unit_id")  
            }  
        }  
        loaded = true  
    }  
  
    suspend fun preload() = withContext(Dispatchers.IO) { ensureLoaded() }  
  
    /** 公会下拉选项，按 guild_id 排序 */  
    fun guildOptions(): List<Pair<Int, String>> {  
        ensureLoaded()  
        return enterGuild.entries.sortedBy { it.key }.map { it.key to it.value }  
    }  
  
    /**  
     * 某个格子对应的 boss unit_id 集合  
     * quest_id -> wave_group_id -> enemy_id 列表 -> unit_id 集合  
     * 任一环查不到返回空集（对应 _boss_unit_ids）  
     */  
    fun bossUnitIds(questId: Int): Set<Int> {  
        ensureLoaded()  
        if (questId == 0) return emptySet()  
        val waveGroupId = questData[questId] ?: return emptySet()  
        val enemyIds = waveGroup[waveGroupId] ?: return emptySet()  
        val units = HashSet<Int>()  
        for (e in enemyIds) {  
            enemyParam[e]?.let { units.add(it) }  
        }  
        return units  
    }  
}