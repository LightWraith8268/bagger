package com.inknironapps.bagger.data.db.seed

import android.content.Context
import com.inknironapps.bagger.data.db.dao.DiscDao
import com.inknironapps.bagger.data.db.entity.DiscEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BaselineDiscLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val discDao: DiscDao
) {
    suspend fun loadIfEmpty() {
        if (discDao.count() > 0) return
        val raw = context.assets.open("discs-baseline.json").bufferedReader().use { it.readText() }
        val arr = JSONArray(raw)
        val discs = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            DiscEntity(
                id = o.getString("id"),
                brand = o.getString("brand"),
                mold = o.getString("mold"),
                speed = o.getDouble("speed").toFloat(),
                glide = o.getDouble("glide").toFloat(),
                turn = o.getDouble("turn").toFloat(),
                fade = o.getDouble("fade").toFloat(),
                discType = o.getString("discType"),
                stability = o.getString("stability"),
                pdgaApproved = o.getBoolean("pdgaApproved"),
                yearReleased = if (o.isNull("yearReleased")) null else o.getInt("yearReleased"),
                primaryStampUrl = if (o.isNull("primaryStampUrl")) null else o.getString("primaryStampUrl"),
                maxWeightG = if (o.has("maxWeightG") && !o.isNull("maxWeightG")) o.getDouble("maxWeightG").toFloat() else null,
                diameterCm = if (o.has("diameterCm") && !o.isNull("diameterCm")) o.getDouble("diameterCm").toFloat() else null,
                discClass = if (o.has("discClass") && !o.isNull("discClass")) o.getString("discClass") else null
            )
        }
        discDao.upsertAll(discs)
    }
}
