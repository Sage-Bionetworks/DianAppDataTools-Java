/*
 * BSD 3-Clause License
 *
 * Copyright 2021  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.sagebionetworks.dian.datamigration;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BridgeJavaSdkUtilTests {

    @Test
    public void test_bridgifyAttributes() throws IOException {
        Map<String, String> attributeMap = new HashMap<>();
        String A255 = StringUtils.repeat("A", 255);
        String A300 = StringUtils.repeat("A", 300);
        attributeMap.put("A255", A255);
        attributeMap.put("A300", A300);
        attributeMap.put("A", "A");
        BridgeJavaSdkUtil.bridgifyAttributes(attributeMap);
        assertEquals("A", attributeMap.get("A"));
        assertEquals(A255, attributeMap.get("A255"));
        assertEquals(A255, attributeMap.get("A300"));
    }

    @Test
    public void test_newUserAttributes_ArcIdUser() throws IOException {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String password = "5tm95s?ES?qTx5iGeLmb";
        user.arcId = "000000";
        user.password = password;
        Map<String, String> attributes = BridgeJavaSdkUtil.newUserAttributes(user);
        assertNotNull(attributes);
        assertEquals("000000", attributes.get("ARC_ID"));
        assertEquals(password, attributes.get("SIGN_IN_TOKEN"));
        assertEquals("", attributes.get("PHONE_NUMBER"));
        assertEquals("", attributes.get("SITE_NOTES"));
        assertEquals("false", attributes.get("IS_MIGRATED"));
        assertEquals("No rater assigned yet", attributes.get("RATER_EMAIL"));
    }

    @Test
    public void test_newUserAttributes_PhoneAndRater() throws IOException {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String password = "5tm95s?ES?qTx5iGeLmb";
        user.arcId = "000000";
        user.password = password;
        user.phone = "+11111111111";
        user.notes = "Notes";
        HmDataModel.TableRow.Rater rater = new HmDataModel.TableRow.Rater();
        rater.email = "a@b.com";
        user.rater = rater;
        Map<String, String> attributes = BridgeJavaSdkUtil.newUserAttributes(user);
        assertNotNull(attributes);
        assertEquals("000000", attributes.get("ARC_ID"));
        assertEquals(password, attributes.get("SIGN_IN_TOKEN"));
        assertEquals("+11111111111", attributes.get("PHONE_NUMBER"));
        assertEquals("Notes", attributes.get("SITE_NOTES"));
        assertEquals("false", attributes.get("IS_MIGRATED"));
        assertEquals("a@b.com", attributes.get("RATER_EMAIL"));
    }

    @Test
    public void test_isParticipantMigrated() {
        StudyParticipant participant = new StudyParticipant();
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String arcId = "000000";
        String deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        user.externalId = deviceId;
        user.arcId = arcId;

        // Null attributes, user not migrated
        assertFalse(BridgeJavaSdkUtil.isParticipantMigrated(participant, user));

        // false for attributes, user not migrated
        participant.setAttributes(ImmutableMap.of("IS_MIGRATED", "false"));
        assertFalse(BridgeJavaSdkUtil.isParticipantMigrated(participant, user));

        // true for attributes, user not migrated
        participant.setAttributes(ImmutableMap.of("IS_MIGRATED", "true"));
        assertTrue(BridgeJavaSdkUtil.isParticipantMigrated(participant, user));

        // user's external ID is their arc id, they are always false to migrated
        user.externalId = arcId;
        assertFalse(BridgeJavaSdkUtil.isParticipantMigrated(participant, user));
    }

    @Test
    public void test_signUpObject_ArcId() {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String arcId = "000000";
        String deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        String password = "5tm95s?ES?qTx5iGeLmb";
        user.externalId = arcId;
        user.arcId = arcId;
        user.studyId = "DIAN";
        user.deviceId = deviceId;
        user.password = password;

        SignUp signUp = BridgeJavaSdkUtil.createSignUpObject(user);
        assertNotNull(signUp);
        assertEquals(1, signUp.getExternalIds().keySet().size());
        assertEquals(arcId, signUp.getExternalIds().get("DIAN"));
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, signUp.getSharingScope());
        assertEquals(0, signUp.getDataGroups().size());
        assertEquals(password, signUp.getPassword());
        assertNotNull(signUp.getAttributes());
    }

    @Test
    public void test_signUpObject_DeviceId() {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String arcId = "000000";
        String deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        String password = "5tm95s?ES?qTx5iGeLmb";
        user.externalId = deviceId;
        user.arcId = arcId;
        user.deviceId = deviceId;
        user.studyId = "DIAN";
        user.password = password;

        SignUp signUp = BridgeJavaSdkUtil.createSignUpObject(user);
        assertNotNull(signUp);
        assertEquals(1, signUp.getExternalIds().keySet().size());
        assertEquals(deviceId, signUp.getExternalIds().get("DIAN"));
        assertEquals(SharingScope.NO_SHARING, signUp.getSharingScope());
        assertEquals(1, signUp.getDataGroups().size());
        assertEquals("test_user", signUp.getDataGroups().get(0));
        assertEquals(password, signUp.getPassword());
        assertNotNull(signUp.getAttributes());
    }
}