package com.inknironapps.bagger.data.db.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inknironapps.bagger.data.db.BaggerDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BaselineDiscLoaderTest {
    private lateinit var db: BaggerDatabase

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), BaggerDatabase::class.java
        ).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    @Test fun loadsBaselineDiscs() = runBlocking {
        val loader = BaselineDiscLoader(ApplicationProvider.getApplicationContext(), db.discDao())
        loader.loadIfEmpty()
        val count = db.discDao().count()
        assertTrue(count >= 10, "expected at least 10 baseline discs, got $count")
    }
}
