package com.inknironapps.bagger.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

interface DiscDbApi {
    @GET
    suspend fun fetchDiscs(
        @Url url: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<List<DiscDto>>
}
