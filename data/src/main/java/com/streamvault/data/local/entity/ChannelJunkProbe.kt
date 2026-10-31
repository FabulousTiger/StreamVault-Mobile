package com.streamvault.data.local.entity

import androidx.room.ColumnInfo

data class ChannelJunkProbe(
    val id: Long = 0,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    val name: String,
    @ColumnInfo(name = "category_name") val categoryName: String? = null,
    @ColumnInfo(name = "group_title") val groupTitle: String? = null,
    @ColumnInfo(name = "logical_group_id") val logicalGroupId: String = ""
)
