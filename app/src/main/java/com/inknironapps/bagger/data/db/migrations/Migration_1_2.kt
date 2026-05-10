package com.inknironapps.bagger.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema v2 — adds nullable PDGA-detail fields (max weight, diameter,
 * disc class) to the discs table. Existing rows get NULL for each new
 * column; future catalog syncs populate them when the upstream feed
 * supplies values.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE discs ADD COLUMN maxWeightG REAL")
        db.execSQL("ALTER TABLE discs ADD COLUMN diameterCm REAL")
        db.execSQL("ALTER TABLE discs ADD COLUMN discClass TEXT")
    }
}
