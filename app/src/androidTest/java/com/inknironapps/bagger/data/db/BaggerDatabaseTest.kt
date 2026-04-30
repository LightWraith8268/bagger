package com.inknironapps.bagger.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class BaggerDatabaseTest {
    private lateinit var db: BaggerDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BaggerDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun insertAndQueryDisc() = runBlocking {
        val disc = DiscEntity(
            id = "innova-destroyer", brand = "Innova", mold = "Destroyer",
            speed = 12f, glide = 5f, turn = -1f, fade = 3f,
            discType = "Driver", stability = "overstable",
            pdgaApproved = true, yearReleased = 2008, primaryStampUrl = null
        )
        db.discDao().upsertAll(listOf(disc))
        val out = db.discDao().getById("innova-destroyer")
        assertEquals("Destroyer", out?.mold)
    }

    @Test fun insertOwnedDiscWithDiscFK() = runBlocking {
        val disc = DiscEntity("innova-aviar", "Innova", "Aviar", 2f, 3f, 0f, 1f, "Putter", "stable", true, 1985, null)
        db.discDao().upsertAll(listOf(disc))
        val owned = OwnedDiscEntity(
            id = "uuid-1", discId = "innova-aviar", plasticType = "DX",
            weight = 175, color = "#ffffff", condition = "New", state = "Shelf",
            bagId = null, purchaseDate = null, purchasePrice = null, notes = null,
            isOriginalOwner = true, customTags = emptyList(),
            createdAt = 1L, updatedAt = 1L, userId = null, syncedAt = null
        )
        db.ownedDiscDao().upsert(owned)
        val all = db.ownedDiscDao().getAll()
        assertEquals(1, all.size)
        assertEquals("uuid-1", all.first().id)
    }
}
