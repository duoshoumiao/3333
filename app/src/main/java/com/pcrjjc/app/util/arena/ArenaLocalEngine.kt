package com.pcrjjc.app.util.arena

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.pcrjjc.app.util.ArenaQueryClient
import com.pcrjjc.app.util.CharaProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 「怎么拆」的端侧实现：截图识别 + 直连 pcrdfans + 无冲配对 + 出图，全部在手机上完成，
 * 不再需要 PC 上的 arena_api 服务。
 */
class ArenaLocalEngine(private val context: Context) {

    companion object {
        private const val TAG = "ArenaLocalEngine"
        private const val MIN_INTERVAL_MS = 1500L
    }

    private val recognizer by lazy { ArenaTeamRecognizer(context) }
    private val renderer by lazy { ArenaRenderer(context) }
    private val cache by lazy { ArenaSolutionCache(context) }
    private val noCollision by lazy { ArenaNoCollision(context) }

    private val queryLock = Mutex()
    private var lastQueryAt = 0L

    // ======================== 对外入口 ========================

    suspend fun queryByImage(
        imageBytes: ByteArray,
        region: Int = 2
    ): ArenaQueryClient.ServerArenaResponse = withContext(Dispatchers.Default) {
        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return@withContext fail("图片解码失败")

        val recon = try {
            recognizer.recognize(bmp)
        } catch (e: Exception) {
            Log.e(TAG, "recognize failed", e)
            return@withContext fail("识别失败: ${e.message}")
        }

        val highlight = b64(renderer.renderHighlight(bmp, recon))
        val compare = b64(renderer.renderCompare(bmp, recon))

        val teams = recon.teams.filter { it.size >= 4 }
        if (teams.isEmpty()) {
            return@withContext ArenaQueryClient.ServerArenaResponse(
                code = 1,
                message = "未识别到 4 人以上阵容",
                teamCount = 0,
                defenseTeams = emptyList(),
                results = emptyList(),
                image = null,
                highlightImage = highlight,
                compareImage = compare
            )
        }
        if (teams.size > 3) {
            return@withContext ArenaQueryClient.ServerArenaResponse(
                code = 1,
                message = "识别队伍过多（>3）",
                teamCount = teams.size,
                defenseTeams = emptyList(),
                results = emptyList(),
                image = null,
                highlightImage = highlight,
                compareImage = compare
            )
        }

        val solutions = ArrayList<List<ArenaPcrdfansClient.Attack>>(teams.size)
        var failure: String? = null
        for (t in teams) {
            val (list, err) = fetch(t, region)
            if (err != null) failure = err
            solutions.add(list)
        }

        // 单队：直接出图
        if (teams.size == 1) {
            val attacks = solutions[0]
            val rows = attacks.map { toRow(it) }
            val img = b64(renderer.renderSolutions(rows))
            return@withContext ArenaQueryClient.ServerArenaResponse(
                code = 0,
                message = if (attacks.isEmpty()) (failure ?: "未查询到解法") else "success",
                teamCount = 1,
                defenseTeams = emptyList(),
                results = if (attacks.isEmpty()) emptyList() else listOf(
                    ArenaQueryClient.TeamResult(
                        defenseIndex = 0,
                        defenseIds = teams[0],
                        attacks = attacks.map { toArenaResult(it) }
                    )
                ),
                image = img,
                highlightImage = highlight,
                compareImage = compare
            )
        }

        // 多队：无冲配对
        val matched = noCollision.build(teams, solutions)
        if (matched != null) {
            val img = b64(renderer.renderSolutions(matched))
            return@withContext ArenaQueryClient.ServerArenaResponse(
                code = 0,
                message = "无冲配队",
                teamCount = teams.size,
                defenseTeams = emptyList(),
                results = emptyList(),
                image = img,
                highlightImage = highlight,
                compareImage = compare
            )
        }

        // 降级：逐队单独返回
        val rows = ArrayList<ArenaRenderer.Row>()
        for (sol in solutions) {
            for (a in sol) rows.add(toRow(a))
            rows.add(ArenaRenderer.Row())
        }
        if (rows.isNotEmpty()) rows.removeAt(rows.size - 1)
        val img = if (rows.isEmpty()) null else b64(renderer.renderSolutions(rows))
        return@withContext ArenaQueryClient.ServerArenaResponse(
            code = 0,
            message = when {
                rows.isEmpty() -> failure ?: "均未查询到解法"
                else -> "无冲配对失败，返回单队结果"
            },
            teamCount = teams.size,
            defenseTeams = emptyList(),
            results = emptyList(),
            image = img,
            highlightImage = highlight,
            compareImage = compare
        )
    }

    suspend fun queryByIds(
        ids: List<Int>,
        region: Int = 2
    ): ArenaQueryClient.ServerArenaResponse = withContext(Dispatchers.Default) {
        val team = ids.mapNotNull { if (it > 9999) it / 100 else it }.distinct()
        if (team.size < 4) return@withContext fail("编队角色过少")
        val (attacks, err) = fetch(team, region)
        val rows = attacks.map { toRow(it) }
        ArenaQueryClient.ServerArenaResponse(
            code = 0,
            message = if (attacks.isEmpty()) (err ?: "未查询到解法") else "success",
            teamCount = 1,
            defenseTeams = emptyList(),
            results = if (attacks.isEmpty()) emptyList() else listOf(
                ArenaQueryClient.TeamResult(
                    defenseIndex = 0,
                    defenseIds = team,
                    attacks = attacks.map { toArenaResult(it) }
                )
            ),
            image = b64(renderer.renderSolutions(rows)),
            highlightImage = null,
            compareImage = null
        )
    }

    suspend fun queryByText(
        names: List<String>,
        region: Int = 2
    ): ArenaQueryClient.ServerArenaResponse = withContext(Dispatchers.Default) {
        CharaProfileStore.ensureLoaded(context)
        val ids = ArrayList<Int>(names.size)
        val unknown = ArrayList<String>()
        for (n in names) {
            val hit = CharaProfileStore.search(n, limit = 1).matches.firstOrNull()
            if (hit == null) unknown.add(n) else ids.add(hit.id)
        }
        if (unknown.isNotEmpty()) {
            return@withContext fail("无法识别：${unknown.joinToString("、")}")
        }
        if (ids.size < 4) return@withContext fail("编队角色过少（至少 4 名）")
        if (ids.size > 5) return@withContext fail("编队不能多于 5 名角色")
        if (ids.size != ids.toSet().size) return@withContext fail("编队中含重复角色")
        queryByIds(ids, region)
    }

    // ======================== 查询与缓存 ========================

    private suspend fun fetch(
        team: List<Int>,
        region: Int
    ): Pair<List<ArenaPcrdfansClient.Attack>, String?> {
        // 4 人队：pcrdfans 不接受补位，只能走本地近似解
        if (team.size == 4) {
            val approx = cache.approximate(team, region)
            return approx to if (approx.isEmpty()) "未找到近似解" else null
        }

        cache.get(team, region)?.let { return it to null }

        val result = queryLock.withLock {
            val wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastQueryAt)
            if (wait > 0) delay(wait)
            lastQueryAt = System.currentTimeMillis()
            ArenaPcrdfansClient.search(team, region, 1)
        }

        return when (result) {
            is ArenaPcrdfansClient.Result.Ok -> {
                if (result.attacks.isNotEmpty()) cache.put(team, region, result.attacks)
                result.attacks to null
            }
            is ArenaPcrdfansClient.Result.Failed -> {
                val approx = cache.approximate(team, region)
                if (approx.isNotEmpty()) approx to null else emptyList<ArenaPcrdfansClient.Attack>() to result.message
            }
        }
    }

    // ======================== 转换 ========================

    private fun toRow(a: ArenaPcrdfansClient.Attack): ArenaRenderer.Row =
        ArenaRenderer.Row(
            ids = a.ids.map { it / 100 },
            up = a.up,
            down = a.down,
            updated = a.updated
        )

    private fun toArenaResult(a: ArenaPcrdfansClient.Attack): ArenaQueryClient.ArenaResult =
        ArenaQueryClient.ArenaResult(
            atkUnits = a.ids.map { it / 100 },
            upVote = a.up,
            downVote = a.down,
            score = noCollision.score(a.up, a.down),
            updated = a.updated
        )

    private fun b64(bmp: Bitmap?): String? {
        if (bmp == null) return null
        return try {
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "encode png failed", e)
            null
        }
    }

    private fun fail(msg: String): ArenaQueryClient.ServerArenaResponse =
        ArenaQueryClient.ServerArenaResponse(
            code = -1,
            message = msg,
            teamCount = 0,
            defenseTeams = emptyList(),
            results = emptyList(),
            image = null
        )
}
