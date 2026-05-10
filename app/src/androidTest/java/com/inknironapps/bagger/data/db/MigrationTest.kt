package com.inknironapps.bagger.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.inknironapps.bagger.data.db.migrations.MIGRATION_1_2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        BaggerDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_addsNullableColumns() {
        helper.createDatabase("test.db", 1).use { db ->
            db.execSQL(
                """
                INSERT INTO discs
                    (id, brand, mold, speed, glide, turn, fade, discType,
                     stability, pdgaApproved, yearReleased, primaryStampUrl)
                VALUES
                    ('innova-aviar', 'Innova', 'Aviar', 2.0, 3.0, 0.0, 1.0,
                     'Putter', 'stable', 1, 1985, NULL)
                """.trimIndent()
            )
        }
        helper.runMigrationsAndValidate("test.db", 2, true, MIGRATION_1_2).use { db ->
            val cursor = db.query(
                "SELECT maxWeightG, diameterCm, discClass FROM discs WHERE id = 'innova-aviar'"
            )
            assertNotNull(cursor)
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            cursor.close()
        }
    }
}
