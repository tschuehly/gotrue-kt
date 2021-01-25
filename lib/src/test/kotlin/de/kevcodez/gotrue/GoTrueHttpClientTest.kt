package de.kevcodez.gotrue

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.StringEntity
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class GoTrueHttpClientTest {

    private val baseUrl = "https://test.com"
    private val httpClientMock = mockk<CloseableHttpClient>()
    private val defaultHeaders = mapOf(
            HttpHeaders.AUTHORIZATION to "Bearer foobar"
    )

    private val goTrueHttpClient = GoTrueHttpClient(
            baseUrl = baseUrl,
            defaultHeaders = defaultHeaders,
            httpClient = httpClientMock
    )


    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class SetHeaders {

        @ParameterizedTest
        @MethodSource("headersTestData")
        fun `should be able to replace default header`(testData: HeadersTestData) {
            val httpResponse = mockk<CloseableHttpResponse>()
            every { httpResponse.code } returns 200
            every { httpResponse.entity } returns null

            mockHttpCall(httpResponse)

            val slot = slot<ClassicHttpRequest>()
            every { httpClientMock.execute(capture(slot)) } returns httpResponse

            goTrueHttpClient.post(
                    path = "/anywhere",
                    headers = testData.customHeaders
            )

            val request = slot.captured
            assertAll {
                assertThat(request.headers).hasSize(testData.expectedRequestHeaders.size)
                testData.expectedRequestHeaders.forEach { (name, value) ->
                    assertThat(request.getHeader(name).value).isEqualTo(value)
                }
            }
        }

        @Suppress("unused")
        fun headersTestData(): Stream<HeadersTestData> {
            return Stream.of(
                    HeadersTestData(
                            customHeaders = emptyMap(),
                            expectedRequestHeaders = defaultHeaders
                    ),
                    HeadersTestData(
                            customHeaders = mapOf(HttpHeaders.AUTHORIZATION to "something else"),
                            expectedRequestHeaders = mapOf(HttpHeaders.AUTHORIZATION to "something else")
                    ),
                    HeadersTestData(
                            customHeaders = mapOf("foo" to "bar"),
                            expectedRequestHeaders = mapOf(
                                    HttpHeaders.AUTHORIZATION to "Bearer foobar",
                                    "foo" to "bar"
                            )
                    )
            )
        }
    }

    data class HeadersTestData(val customHeaders: Map<String, String>, val expectedRequestHeaders: Map<String, String>)

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ThrowHttpException {

        @ParameterizedTest
        @MethodSource("exceptionTestData")
        fun `should throw http exception when status is above 300`(testData: ExceptionTestData) {
            val httpResponse = mockk<CloseableHttpResponse>()
            val responseCode = testData.status
            val httpBody = testData.body

            every { httpResponse.code } returns responseCode
            every { httpResponse.entity } returns httpBody?.let { StringEntity(it) }

            mockHttpCall(httpResponse)

            val exception = assertThrows<GoTrueHttpException> {
                goTrueHttpClient.get(
                        path = "/anywhere",
                        responseType = String::class
                )
            }

            assertThat(exception.status).isEqualTo(responseCode)
            assertThat(exception.httpBody).isEqualTo(httpBody)
        }

        @Suppress("unused")
        private fun exceptionTestData(): Stream<ExceptionTestData> {
            return Stream.of(
                    ExceptionTestData(301, "httpbody"),
                    ExceptionTestData(301, null),
                    ExceptionTestData(400, "httpbody")
            )
        }
    }

    data class ExceptionTestData(
            val status: Int,
            val body: String?
    )

    private fun mockHttpCall(httpResponse: CloseableHttpResponse) {
        every { httpClientMock.close() }.returns(Unit)

        every { httpClientMock.execute(any(), any<HttpClientResponseHandler<Any>>()) }.answers {
            val handler = args[1] as HttpClientResponseHandler<Any>
            handler.handleResponse(httpResponse)
        }
    }

}