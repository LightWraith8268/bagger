package com.inknironapps.bagger.data.backup

import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.dao.OwnedDiscDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    private val ownedDao: OwnedDiscDao,
    private val discDao: DiscDao
) {
    suspend fun export(): String {
        val owned = ownedDao.getAll()
        val sb = StringBuilder()
        sb.appendLine("brand,mold,plastic,weight,color,condition,state,purchase_date,purchase_price_cents,notes")
        owned.forEach { o ->
            val d = discDao.getById(o.discId)
            sb.append(d?.brand?.csvEscape() ?: "")
            sb.append(',').append(d?.mold?.csvEscape() ?: "")
            sb.append(',').append(o.plasticType?.csvEscape() ?: "")
            sb.append(',').append(o.weight?.toString() ?: "")
            sb.append(',').append(o.color?.csvEscape() ?: "")
            sb.append(',').append(o.condition.csvEscape())
            sb.append(',').append(o.state.csvEscape())
            sb.append(',').append(o.purchaseDate?.toString() ?: "")
            sb.append(',').append(o.purchasePrice?.toString() ?: "")
            sb.append(',').append(o.notes?.csvEscape() ?: "")
            sb.appendLine()
        }
        return sb.toString()
    }
}

private fun String.csvEscape(): String =
    if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\""
    else this
