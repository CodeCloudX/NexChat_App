package com.nexchat.core.network.api

import com.nexchat.core.network.dto.PublicUser
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ContactApi {
    @GET("contacts/search")
    suspend fun search(@Query("q") query: String): Response<List<PublicUser>>
}
