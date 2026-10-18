package com.pcrjjc.app.util.arena

import android.content.Context
import kotlin.math.ln
import kotlin.random.Random

/**
 * 移植自 arena/old_main.py 的 generateCollisionFreeTeam / recommend1Team / recommend2Teams。
 * 作用：PJJC 两队/三队查询时，从各队解法里挑出互不重复角色的组合。
 */
class ArenaNoCollision(private val context: Context) {

    data class Candidate(
        val ids: List<Int>?,
        val value: Double,
        var row: ArenaRenderer.Row?
    )

    fun score(up: Int, down: Int): Double {
        val total = up + down
        val v1 = up / (total + 0.0001) * 2 - 1
        val v2 = ln(total + 0.01) / ln(100.0)
        return v1 + v2 + Random.nextDouble() / 1000
    }

    private fun placeholder(): Candidate =
        Candidate(null, -100.0, ArenaRenderer.Row(ids = List(5) { 9000 }))

    /**
     * @param teams 识别出的防守队（每队 4~5 个 4 位 ID）
     * @param solutions 与 teams 一一对应的解法列表
     * @return 用于渲染的行；null 表示无法完成无冲配对（调用方走降级）
     */
    fun build(
        teams: List<List<Int>>,
        solutions: List<List<ArenaPcrdfansClient.Attack>>
    ): List<ArenaRenderer.Row>? {
        if (teams.size !in 2..3) return null
        val all = ArrayList<MutableList<Candidate>>(teams.size)
        var teamHasResult = 0
        for (i in teams.indices) {
            val list = mutableListOf(placeholder())
            val sol = solutions.getOrNull(i).orEmpty()
            if (sol.isNotEmpty()) teamHasResult++
            for (a in sol) {
                val base = a.ids.map { it / 100 }
                list.add(
                    Candidate(
                        ids = base,
                        value = score(a.up, a.down),
                        row = ArenaRenderer.Row(
                            ids = base, up = a.up, down = a.down, updated = a.updated
                        )
                    )
                )
            }
            all.add(list)
        }
        if (teamHasResult == 0) return null

        return if (teams.size == 2) build2(all) else build3(all)
    }

    private fun build2(all: List<MutableList<Candidate>>): List<ArenaRenderer.Row>? {
        val combos = ArrayList<Triple<Int, Int, Double>>()
        for (i in all[0].indices) {
            for (j in all[1].indices) {
                combos.add(Triple(i, j, all[0][i].value + all[1][j].value))
            }
        }
        combos.sortByDescending { it.third }

        var count = 0
        val out = ArrayList<ArenaRenderer.Row>()
        for (c in combos) {
            val r1 = all[0][c.first]
            val r2 = all[1][c.second]
            val t1 = r1.ids.orEmpty()
            val t2 = r2.ids.orEmpty()
            val mix = t1 + t2
            if (mix.size != mix.toSet().size) continue

            var succ = false
            if (c.third < -250) break
            if (c.third < -150) {
                val (a, b) = recommend2(mix)
                if (a != null && b != null) {
                    r1.row = a
                    r2.row = b
                    succ = true
                }
            } else if (c.third < -50) {
                val a = recommend1(mix)
                if (a != null) {
                    if (t1.isEmpty()) r1.row = a
                    if (t2.isEmpty()) r2.row = a
                    succ = true
                }
            } else {
                succ = true
            }

            if (succ) {
                count++
                out.add(r1.row ?: ArenaRenderer.Row())
                out.add(r2.row ?: ArenaRenderer.Row())
                out.add(ArenaRenderer.Row())
                if (count >= 8) break
            }
        }
        if (count == 0) return null
        return out.dropLast(1)
    }

    private fun build3(all: List<MutableList<Candidate>>): List<ArenaRenderer.Row>? {
        val combos = ArrayList<List<Any>>()
        for (i in all[0].indices) {
            for (j in all[1].indices) {
                for (k in all[2].indices) {
                    combos.add(
                        listOf(
                            i, j, k,
                            all[0][i].value + all[1][j].value + all[2][k].value
                        )
                    )
                }
            }
        }
        combos.sortByDescending { it[3] as Double }

        var count = 0
        var count2 = 0
        val out = ArrayList<ArenaRenderer.Row>()
        val out2 = ArrayList<ArenaRenderer.Row>()

        for (c in combos) {
            val r1 = all[0][c[0] as Int]
            val r2 = all[1][c[1] as Int]
            val r3 = all[2][c[2] as Int]
            val t1 = r1.ids.orEmpty()
            val t2 = r2.ids.orEmpty()
            val t3 = r3.ids.orEmpty()
            val mix = t1 + t2 + t3
            if (mix.size != mix.toSet().size) continue

            val v = c[3] as Double
            if (v < -250) break

            var succ = false
            if (v < -150) {
                val (a, b) = recommend2(mix)
                if (a != null && b != null) {
                    when {
                        t1.isNotEmpty() -> { r2.row = a; r3.row = b }
                        t2.isNotEmpty() -> { r3.row = a; r1.row = b }
                        else -> { r1.row = a; r2.row = b }
                    }
                    succ = true
                }
            } else if (v < -50) {
                val a = recommend1(mix)
                if (a == null) {
                    count2++
                    out2.add(r1.row ?: ArenaRenderer.Row())
                    out2.add(r2.row ?: ArenaRenderer.Row())
                    out2.add(r3.row ?: ArenaRenderer.Row())
                    out2.add(ArenaRenderer.Row())
                } else {
                    if (t1.isEmpty()) r1.row = a
                    if (t2.isEmpty()) r2.row = a
                    if (t3.isEmpty()) r3.row = a
                    succ = true
                }
            } else {
                succ = true
            }

            if (succ) {
                count++
                out.add(r1.row ?: ArenaRenderer.Row())
                out.add(r2.row ?: ArenaRenderer.Row())
                out.add(r3.row ?: ArenaRenderer.Row())
                out.add(ArenaRenderer.Row())
                if (count >= 6) break
            }
        }

        val rows = when {
            count > 0 -> out
            count2 > 0 -> out2
            else -> return null
        }
        return rows.dropLast(1)
    }

    private fun recommend1(used: List<Int>): ArenaRenderer.Row? {
        val records = ArenaAssets.bestAtkRecords(context)
        for (rec6 in records) {
            val base = rec6.map { it / 100 }
            val mix = used + base
            if (mix.size == mix.toSet().size) {
                return ArenaRenderer.Row(ids = base, tag = "高频解")
            }
        }
        return null
    }

    private fun recommend2(used: List<Int>): Pair<ArenaRenderer.Row?, ArenaRenderer.Row?> {
        val records = ArenaAssets.bestAtkRecords(context)
        if (records.size < 2) return null to null
        val combos = ArrayList<Triple<Int, Int, Int>>()
        for (i in 0 until records.size - 1) {
            for (j in i + 1 until records.size) {
                combos.add(Triple(i, j, i + j))
            }
        }
        combos.sortBy { it.third }
        for (c in combos) {
            val base1 = records[c.first].map { it / 100 }
            val base2 = records[c.second].map { it / 100 }
            val mix = used + base1 + base2
            if (mix.size == mix.toSet().size) {
                return ArenaRenderer.Row(ids = base2, tag = "高频解") to
                    ArenaRenderer.Row(ids = base1, tag = "高频解")
            }
        }
        return null to null
    }
}
