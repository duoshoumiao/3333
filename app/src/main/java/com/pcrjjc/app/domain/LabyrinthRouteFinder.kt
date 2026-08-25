package com.pcrjjc.app.domain  
  
/**  
 * 黎明界路线查找 + Boss 过滤 + 完美开局  
 * 完整移植 PPPPPP autopcr/module/modules/labyrinth.py 第 10-216 行  
 */  
class LabyrinthRouteFinder(private val db: LabyrinthDb) {  
  
    companion object {  
        // (unitId, 名称, 难度)  对应 labyrinth.py 第 10-24 行  
        val AREA3_BOSSES = listOf(  
            Triple(312505, "厄勒克特拉夫人", "简单"),  
            Triple(319604, "冰霜魔狼", "简单"),  
            Triple(303306, "暗黑滴水嘴兽", "普通"),  
            Triple(301206, "巨型魔像", "普通"),  
            Triple(306604, "毒液沙鳗蛇", "困难"),  
        )  
        val AREA5_BOSSES = listOf(  
            Triple(310103, "愤怒巨龙", "简单"),  
            Triple(301701, "炸脖龙", "普通"),  
            Triple(319401, "究极守护者", "普通"),  
            Triple(315004, "领主哥布林", "困难"),  
            Triple(302501, "奇美拉", "困难"),  
        )  
        // unit_id -> 名称  对应 labyrinth.py 第 26-29 行  
        val BOSS_NAME_BY_UNIT: Map<Int, String> =  
            (AREA3_BOSSES + AREA5_BOSSES).associate { it.first to it.second }  
  
        // block_type -> 名称  对应 labyrinth.py 第 31-40 行  
        val BLOCK_TYPE_NAME = mapOf(  
            1 to "起点", 2 to "普通怪物", 3 to "EX怪物", 4 to "角色",  
            5 to "事件", 6 to "遗物", 7 to "商店", 8 to "Boss",  
        )  
  
        // area -> (column -> blockType)  对应 labyrinth.py 第 62-68 行  
        val AREA_REQUIREMENTS: Map<Int, Map<Int, Int>> = mapOf(  
            1 to mapOf(1 to 1, 2 to 2, 3 to 4, 4 to 2, 5 to 4, 6 to 6),  
            2 to mapOf(1 to 1, 2 to 4, 3 to 2, 4 to 6, 5 to 3, 6 to 4, 7 to 6),  
            3 to mapOf(1 to 1, 2 to 2, 3 to 6, 4 to 4, 5 to 3, 6 to 7, 7 to 8),  
            4 to mapOf(1 to 1, 2 to 4, 3 to 3, 4 to 5, 5 to 3, 6 to 2, 7 to 4, 8 to 7),  
            5 to mapOf(1 to 1, 2 to 2, 3 to 6, 4 to 3, 5 to 6, 6 to 3, 7 to 7, 8 to 8),  
        )  
    }  
  
    private fun blockType(b: QueryEngine.LabyrinthBlock): Int = b.blockType  
  
    // 对应 _target_areas 第 107-110 行  
    private fun targetAreas(difficulty: Int): List<Int> =  
        if (difficulty == 1) listOf(1, 2, 3) else AREA_REQUIREMENTS.keys.sorted()  
  
    // 对应 _boss_matches 第 101-105 行  
    private fun bossMatches(  
        area: Int, block: QueryEngine.LabyrinthBlock,  
        area3Bosses: Set<Int>, area5Bosses: Set<Int>  
    ): Boolean {  
        val selected = when (area) { 3 -> area3Bosses; 5 -> area5Bosses; else -> emptySet() }  
        if (selected.isEmpty()) return true  
        return (db.bossUnitIds(block.questId) intersect selected).isNotEmpty()  
    }  
  
    // 对应 _expected_block_types 第 122-129 行  
    private fun expectedBlockTypes(area: Int, column: Int, thirdBlockType: String): Set<Int> {  
        if ((area == 3 || area == 5) && column == 3) {  
            return when (thirdBlockType) {  
                "必须事件" -> setOf(5)  
                "两者都行" -> setOf(5, 6)  
                else -> setOf(6) // 必须遗物  
            }  
        }  
        return setOf(AREA_REQUIREMENTS.getValue(area).getValue(column))  
    }  
  
    // 对应 _position_name 第 73-82 行  
    private fun positionName(  
        block: QueryEngine.LabyrinthBlock,  
        areaColumns: Map<Int, List<QueryEngine.LabyrinthBlock>>  
    ): String {  
        val rows = areaColumns[block.column]?.map { it.row } ?: emptyList()  
        val maxRow = rows.maxOrNull() ?: block.row  
        return when {  
            maxRow <= 1 -> "合流"  
            maxRow == 2 -> mapOf(1 to "下", 2 to "上")[block.row] ?: block.row.toString()  
            maxRow == 3 -> mapOf(1 to "下", 2 to "中", 3 to "上")[block.row] ?: block.row.toString()  
            else -> block.row.toString()  
        }  
    }  
  
    // 对应 _find_area_route 第 131-177 行  
    private fun findAreaRoute(  
        area: Int, mapList: List<QueryEngine.LabyrinthBlock>,  
        area3Bosses: Set<Int>, area5Bosses: Set<Int>,  
        thirdBlockType: String, perfectStart: Boolean  
    ): Pair<List<QueryEngine.LabyrinthBlock>?, String> {  
        val expected = AREA_REQUIREMENTS.getValue(area)  
        val blocks = mapList.filter { it.area == area }  
        if (blocks.isEmpty()) return null to "区域${area}没有地图数据"  
  
        val byId = blocks.associateBy { it.blockId }  
        val byColumn = blocks.groupBy { it.column }  
            .mapValues { (_, v) -> v.sortedBy { it.row } }  
  
        val missing = expected.keys.filter { it !in byColumn }  
        if (missing.isNotEmpty()) return null to "区域${area}缺少列${missing}"  
  
        val lastColumn = expected.keys.max()  
  
        fun dfs(  
            block: QueryEngine.LabyrinthBlock,  
            path: List<QueryEngine.LabyrinthBlock>,  
            seen: Set<Int>  
        ): List<QueryEngine.LabyrinthBlock>? {  
            val column = block.column  
            if (column in expected) {  
                if (perfectStart &&  
                    blockType(block) !in expectedBlockTypes(area, column, thirdBlockType)  
                ) return null  
            }  
			// 区域2：第4列必须是遗物(6)，且必须直接连到第5列EX怪物(3)  
            if (perfectStart && area == 2 && column == 4) {  
                if (blockType(block) != 6) return null  
                val reachesEx = block.nextBlockIdList.any { nid ->  
                    val nb = byId[nid]  
                    nb != null && nb.column == 5 && blockType(nb) == 3  
                }  
                if (!reachesEx) return null  
            }
            if (column == lastColumn) {  
                if (expected[column] == 8 &&  
                    !bossMatches(area, block, area3Bosses, area5Bosses)  
                ) return null  
                return path + block  
            }  
            for (nextId in block.nextBlockIdList) {  
                if (nextId in seen) continue  
                val next = byId[nextId] ?: continue  
                val route = dfs(next, path + block, seen + nextId)  
                if (route != null) return route  
            }  
            return null  
        }  
  
        for (start in byColumn.getValue(1)) {  
            val route = dfs(start, emptyList(), setOf(start.blockId))  
            if (route != null) return route to ""  
        }  
        return null to "区域${area}没有满足条件的可达路线"  
    }  
  
    // 对应 _find_routes 第 179-190 行  
    fun findRoutes(  
        mapList: List<QueryEngine.LabyrinthBlock>, difficulty: Int,  
        area3Bosses: Set<Int>, area5Bosses: Set<Int>,  
        thirdBlockType: String, perfectStart: Boolean  
    ): Pair<Map<Int, List<QueryEngine.LabyrinthBlock>>?, String> {  
        val routes = LinkedHashMap<Int, List<QueryEngine.LabyrinthBlock>>()  
        val failures = mutableListOf<String>()  
        for (area in targetAreas(difficulty)) {  
            val (route, reason) = findAreaRoute(  
                area, mapList, area3Bosses, area5Bosses, thirdBlockType, perfectStart  
            )  
            if (route == null) failures.add(reason) else routes[area] = route  
        }  
        if (failures.isNotEmpty()) return null to failures.joinToString("；")  
        return routes to ""  
    }  
  
    // 对应 _format_route 第 192-216 行  
    fun formatRoute(  
        area: Int, route: List<QueryEngine.LabyrinthBlock>,  
        mapList: List<QueryEngine.LabyrinthBlock>,  
        area3Bosses: Set<Int>, area5Bosses: Set<Int>  
    ): String {  
        val areaColumns = mapList.filter { it.area == area }.groupBy { it.column }  
  
        val parts = route.map { block ->  
            val position = positionName(block, areaColumns)  
            val typeName = BLOCK_TYPE_NAME[blockType(block)] ?: blockType(block).toString()  
            var extra = ""  
            if (blockType(block) == 8) {  
                val bossUnits = db.bossUnitIds(block.questId)  
                if (bossUnits.isNotEmpty()) {  
                    val selected = when (area) { 3 -> area3Bosses; 5 -> area5Bosses; else -> emptySet() }  
                    val candidates = selected.ifEmpty { BOSS_NAME_BY_UNIT.keys }  
                    val bossNames = (bossUnits intersect candidates).sorted()  
                        .mapNotNull { BOSS_NAME_BY_UNIT[it] }  
                    if (bossNames.isNotEmpty()) extra = "(${bossNames.joinToString("/")})"  
                }  
            }  
            "${block.column}${position}【${typeName}${extra}】"  
        }  
        return "区域${area}：" + parts.joinToString("-")  
    }  
}