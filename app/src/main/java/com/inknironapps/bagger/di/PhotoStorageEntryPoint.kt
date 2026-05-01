package com.inknironapps.bagger.di

import com.inknironapps.bagger.data.photo.PhotoStorage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PhotoStorageEntryPoint {
    fun photoStorage(): PhotoStorage
}
