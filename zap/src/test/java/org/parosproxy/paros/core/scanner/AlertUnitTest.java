/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2020 The ZAP Development Team
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
package org.parosproxy.paros.core.scanner;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class AlertUnitTest {

    @Test
    public void shouldDefaultAlertRefToPluginId() {
        // Given
        Alert alert = new Alert(1);
        // When / Then
        assertThat(alert.getAlertRef(), is(equalTo(Integer.toString(alert.getPluginId()))));
    }

    @Test
    public void shouldSetValidAlertRef() {
        // Given
        Alert alert = new Alert(1);
        String alertRef = "1-1";
        // When
        alert.setAlertRef(alertRef);
        // Then
        assertThat(alert.getAlertRef(), is(equalTo(alertRef)));
    }

    @Test
    public void shouldHaveEmptyAlertRefForManualAlerts() {
        // Given
        Alert alert = new Alert(-1);
        // When / Then
        assertThat(alert.getAlertRef(), is(equalTo("")));
    }

    @Test
    public void shouldThrowExceptionOnInvalidAlertRef() {
        // Given
        Alert alert = new Alert(1);
        // When / Then
        assertThrows(IllegalArgumentException.class, () -> alert.setAlertRef("2"));
    }

    @Test
    public void shouldThrowExceptionOnTooLongAlertRef() {
        // Given
        Alert alert = new Alert(1);
        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> alert.setAlertRef(new String(new char[256]).replace("\0", "1")));
    }

    @Test
    public void shouldNotEqualIfAlertRefDifferent() {
        // Given
        Alert alertA = new Alert(1);
        Alert alertB = new Alert(1);
        // When
        alertA.setAlertRef("1-1");
        alertB.setAlertRef("1-2");
        boolean equals = alertA.equals(alertB);
        // Then
        assertThat(equals, is(equalTo(false)));
    }

    @Test
    public void shouldNotCompareIfAlertRefDifferent() {
        // Given
        Alert alertA = new Alert(1);
        Alert alertB = new Alert(1);
        // When
        alertA.setAlertRef("1-1");
        alertB.setAlertRef("1-2");
        int cmp = alertA.compareTo(alertB);
        // Then
        assertThat(cmp, is(equalTo(-1)));
    }
}
