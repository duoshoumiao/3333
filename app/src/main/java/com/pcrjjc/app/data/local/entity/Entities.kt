package com.pcrjjc.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * PCR binding entity corresponding to PCRBind in pcrjjc2/database/models.py
 */
@Entity(tableName = "pcr_bind")
data class PcrBind(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val pcrid: Long,
    val platform: Int,
    val name: String? = null,
    val jjcNotice: Boolean = true,
    val pjjcNotice: Boolean = true,
    val upNotice: Boolean = false,
    val onlineNotice: Int = 0
)

/**
 * Account entity corresponding to Account in pcrjjc2/database/models.py
 */
@Entity(tableName = "account")
data class Account(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val viewerId: String,
    val account: String,
    val password: String,
    val platform: Int
)

/**
 * JJC History entity corresponding to JJCHistory in pcrjjc2/database/models.py
 */
@Entity(tableName = "jjc_history")
data class JjcHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val pcrid: Long,
    val name: String,
    val platform: Int,
    val date: Long,
    val item: Int,
    val before: Int,
    val after: Int
)
