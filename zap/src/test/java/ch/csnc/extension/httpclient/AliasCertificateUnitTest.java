/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2012 The ZAP Development Team
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
package ch.csnc.extension.httpclient;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;

import java.security.cert.Certificate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AliasCertificateUnitTest {

    private AliasCertificate aliasCertificate;

    @Mock private Certificate certificate;

    @Test
    public void shouldRetrieveCnFromUnderlyingCertificate() {
        // Given
        given(certificate.toString()).willReturn("CN=test\\,certificate,post");
        aliasCertificate = new AliasCertificate(certificate, "");
        // When
        String cn = aliasCertificate.getCN();
        // Then
        assertThat(cn, is("test\\,certificate"));
    }

    @Test
    public void shouldMergeCnAndAliasIntoName() {
        // Given
        given(certificate.toString()).willReturn("CN=test\\,certificate,post");
        aliasCertificate = new AliasCertificate(certificate, "alias");
        // When
        String name = aliasCertificate.getName();
        // Then
        assertThat(name, is("test\\,certificate [alias]"));
    }

    @Test
    public void shouldReturnNullAsCnOnUnexpectedUnderlyingCertificateString() {
        // Given
        given(certificate.toString()).willReturn("xxx");
        aliasCertificate = new AliasCertificate(certificate, "");
        // When
        String cn = aliasCertificate.getCN();
        // Then
        assertThat(cn, is(nullValue()));
    }

    @Test
    public void shouldFailRetrievingNameOnNullCn() {
        // Given
        given(certificate.toString()).willReturn("xxx");
        aliasCertificate = new AliasCertificate(certificate, "");
        // When
        String name = aliasCertificate.getName();
        // Then
        assertThat(name, is(""));
    }
}
