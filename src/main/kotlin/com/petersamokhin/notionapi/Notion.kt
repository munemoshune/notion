package com.petersamokhin.notionapi

import com.petersamokhin.notionapi.model.NotionCredentials
import com.petersamokhin.notionapi.model.error.NotionAuthException
import com.petersamokhin.notionapi.model.request.LoadPageChunkRequestBody
import com.petersamokhin.notionapi.model.request.Loader
import com.petersamokhin.notionapi.model.request.QueryCollectionRequestBody
import com.petersamokhin.notionapi.model.response.NotionResponse
import com.petersamokhin.notionapi.request.LoadPageChunkRequest
import com.petersamokhin.notionapi.request.QueryNotionCollectionRequest
import com.petersamokhin.notionapi.request.base.NotionRequest
import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class Notion internal constructor(token: String, private var httpClient: HttpClient) {
    init {
        httpClient = httpClient.config {
            defaultRequest {
                header(HttpHeaders.Cookie, "$NOTION_TOKEN_COOKIE_KEY=$token")
            }
        }
    }

    suspend fun loadPage(pageId: String, limit: Int = 50): NotionResponse {
        return LoadPageChunkRequest(httpClient).execute(
            LoadPageChunkRequestBody(pageId, limit, 0, false)
        )
    }

    suspend fun queryCollection(collectionId: String, collectionViewId: String, limit: Int = 70): NotionResponse {
        return QueryNotionCollectionRequest(httpClient).execute(
            QueryCollectionRequestBody(
                collectionId, collectionViewId, Loader(limit, false, "table")
            )
        )
    }

    fun close() = httpClient.close()

    fun setHttpClient(newHttpClient: HttpClient) {
        httpClient = newHttpClient
    }

    companion object {
        private const val NOTION_TOKEN_COOKIE_KEY = "token_v2"

        @JvmStatic
        fun fromToken(token: String, httpClient: HttpClient): Notion {
            return Notion(token, httpClient)
        }

        @JvmStatic
        suspend fun fromEmailAndPassword(credentials: NotionCredentials, httpClient: HttpClient): Notion {
            val endpoint = "${NotionRequest.API_BASE_URL}/${NotionRequest.Endpoint.LOGIN_WITH_EMAIL}"
            val response = httpClient.post<HttpResponse>(endpoint) {
                headers.appendAll(NotionRequest.BASE_HEADERS)
                contentType(ContentType.Application.Json)
                body = credentials
            }

            val token = response.headers.getAll(HttpHeaders.SetCookie)?.firstOrNull {
                it.contains("$NOTION_TOKEN_COOKIE_KEY=", true)
            }?.split("; ")?.firstOrNull {
                it.contains("$NOTION_TOKEN_COOKIE_KEY=", true)
            }?.split("=")?.getOrNull(1) ?: throw NotionAuthException("No $NOTION_TOKEN_COOKIE_KEY in headers!")

            return fromToken(token, httpClient)
        }
    }
}