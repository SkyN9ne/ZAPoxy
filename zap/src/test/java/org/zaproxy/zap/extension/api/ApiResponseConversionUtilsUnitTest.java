/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2013 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.zap.extension.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;

import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpRequestHeader;
import org.parosproxy.paros.network.HttpResponseHeader;
import org.zaproxy.zap.network.HttpRequestBody;
import org.zaproxy.zap.network.HttpResponseBody;

@ExtendWith(MockitoExtension.class)
public class ApiResponseConversionUtilsUnitTest {

    @Mock HttpMessage message;

    @Mock HttpRequestHeader requestHeader;

    @Mock HttpRequestBody requestBody;

    @Mock HttpResponseHeader responseHeader;

    @Mock HttpResponseBody responseBody;

    @BeforeEach
    public void prepareMessage() {
        given(message.getRequestHeader()).willReturn(requestHeader);
        given(message.getRequestBody()).willReturn(requestBody);
        given(message.getResponseHeader()).willReturn(responseHeader);
        given(message.getResponseBody()).willReturn(responseBody);
    }

    @Test
    public void nameOfApiResponseShouldBeConstant() {
        ApiResponseSet<String> response = ApiResponseConversionUtils.httpMessageToSet(0, message);

        assertThat(response.getName(), is("message"));
    }

    @Test
    public void historyIdShouldBecomeIdOfApiResponse() {
        ApiResponseSet<String> response = ApiResponseConversionUtils.httpMessageToSet(42, message);

        assertThat(response.getValues(), hasEntry("id", "42"));
    }

    @Test
    public void shouldHaveUndefinedHistoryTypeByDefault() {
        // Given / When
        ApiResponseSet<String> response = ApiResponseConversionUtils.httpMessageToSet(0, message);
        // Then
        assertThat(response.getValues(), hasEntry("type", "-1"));
    }

    @Test
    public void shouldIncludeHistoryTypeInApiResponse() {
        // Given
        int historyType = 2;
        // When
        ApiResponseSet<String> response =
                ApiResponseConversionUtils.httpMessageToSet(0, historyType, message);
        // Then
        assertThat(response.getValues(), hasEntry("type", (Object) "2"));
    }

    @Test
    public void propertiesFromGivenHttpMessageShouldReflectInApiResponse() {
        given(message.getCookieParamsAsString()).willReturn("testCookieParams");
        given(message.getNote()).willReturn("testNote");
        given(requestHeader.toString()).willReturn("testRequestHeader");
        given(requestBody.toString()).willReturn("testRequestBody");
        given(responseHeader.toString()).willReturn("testResponseHeader");
        given(message.getTimeSentMillis()).willReturn(1010101010101L);
        given(message.getTimeElapsedMillis()).willReturn(200);

        ApiResponseSet<String> response = ApiResponseConversionUtils.httpMessageToSet(0, message);

        assertThat(response.getValues(), hasEntry("cookieParams", "testCookieParams"));
        assertThat(response.getValues(), hasEntry("note", "testNote"));
        assertThat(response.getValues(), hasEntry("requestHeader", requestHeader.toString()));
        assertThat(response.getValues(), hasEntry("requestBody", requestBody.toString()));
        assertThat(response.getValues(), hasEntry("responseHeader", responseHeader.toString()));
        assertThat(response.getValues(), hasEntry("timestamp", "1010101010101"));
        assertThat(response.getValues(), hasEntry("rtt", "200"));
    }

    @Test
    public void compressedResponseBodyShouldBeDeflatedIntoApiResponse() throws Exception {
        given(responseHeader.getHeader(HttpHeader.CONTENT_ENCODING)).willReturn(HttpHeader.GZIP);
        given(responseBody.getBytes()).willReturn(gzip(new byte[] {97, 98, 99}));

        ApiResponseSet<String> response = ApiResponseConversionUtils.httpMessageToSet(0, message);

        assertThat(response.getValues(), hasEntry("responseBody", "abc"));
    }

    @Test
    public void brokenCompressedResponseBodyShouldBeStoredAsStringRepresentationInApiResponse() {
        given(responseHeader.getHeader(HttpHeader.CONTENT_ENCODING)).willReturn(HttpHeader.GZIP);
        given(responseBody.getBytes()).willReturn(new byte[] {0, 0, 0});

        ApiResponseSet<String> response = ApiResponseConversionUtils.httpMessageToSet(0, message);

        assertThat(response.getValues(), hasEntry("responseBody", responseBody.toString()));
    }

    private static byte[] gzip(byte[] raw) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(raw.length);
        try (GZIPOutputStream zip = new GZIPOutputStream(bytes)) {
            zip.write(raw);
        }
        return bytes.toByteArray();
    }
}
