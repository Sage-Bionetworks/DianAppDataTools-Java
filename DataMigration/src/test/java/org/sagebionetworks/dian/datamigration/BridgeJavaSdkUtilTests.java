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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantReportsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.ExternalIdentifier;
import org.sagebionetworks.bridge.rest.model.ExternalIdentifierList;
import org.sagebionetworks.bridge.rest.model.ForwardCursorReportDataList;
import org.sagebionetworks.bridge.rest.model.IdentifierHolder;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ReportData;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.Study;
import org.sagebionetworks.bridge.rest.model.StudyList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil.*;

public class BridgeJavaSdkUtilTests extends Mockito {

    private final Path resourceDirectory = Paths.get("src", "test", "resources");
    private final Path testSessionSchedulePath = resourceDirectory
            .resolve("testSessionSchedules")
            .resolve("test_session_schedules_2021-07-08")
            .resolve("000000 test_session_schedule 2019-08-29T16-06-11Z.json");
    private final Path wakeSleepSchedulePath = resourceDirectory
            .resolve("wakeSleepSchedules")
            .resolve("wake_sleep_schedules_08-07-21")
            .resolve("000000 Availability 2019-08-29T16-06-11Z.json");

    @Mock
    private ForResearchersApi mockResearcherApi;

    @Mock
    private ParticipantReportsApi mockReportsApi;

    @Mock
    private ParticipantsApi mockParticipantsApi;

    @Mock
    private Call<Message> mockTestSessionReportCall;

    @Mock
    private Call<Message> mockWakeSleepReportCall;

    @Mock
    private Call<Message> mockCompletedTestsReportCall;

    @Mock
    private Call<Message> mockDeleteTestSessionReportCall;

    @Mock
    private Call<Message> mockDeleteWakeSleepReportCall;

    @Mock
    private Call<Message> mockDeleteCompletedTestReportCall;

    @Mock
    private Call<Message> mockUpdateParticipantCall;

    @Mock
    private Call<IdentifierHolder> mockSignUpCall;

    @Mock
    private Call<StudyParticipant> mockGetExternalIdMigratedCall;

    @Mock
    private Call<StudyParticipant> mockGetExternalIdNotMigratedCall;

    @Mock
    private Call<StudyList> mockGetAllStudies;

    @Mock
    private Call<ForwardCursorReportDataList> mockGetUsersParticipantReportRecordsV4;

    @Mock
    private Call<ExternalIdentifierList> mockGetExternalIdsInStudyA;

    @Before
    public void before() throws IOException {
        MockitoAnnotations.initMocks(this);

        BridgeJavaSdkUtil.mockInitialize(mockResearcherApi, mockReportsApi, mockParticipantsApi);

        when(mockTestSessionReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.addParticipantReportRecordV4(
                anyString(), eq(TEST_SCHEDULE_REPORT_ID), any()))
                .thenReturn(mockTestSessionReportCall);

        when(mockWakeSleepReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.addParticipantReportRecordV4(
                anyString(), eq(AVAILABILITY_REPORT_ID), any()))
                .thenReturn(mockWakeSleepReportCall);

        when(mockCompletedTestsReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.addParticipantReportRecordV4(
                anyString(), eq(COMPLETED_TESTS_REPORT_ID), any()))
                .thenReturn(mockCompletedTestsReportCall);

        when(mockDeleteTestSessionReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.deleteAllParticipantReportRecords(anyString(), eq(TEST_SCHEDULE_REPORT_ID)))
                .thenReturn(mockDeleteTestSessionReportCall);

        when(mockDeleteWakeSleepReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.deleteAllParticipantReportRecords(anyString(), eq(AVAILABILITY_REPORT_ID)))
                .thenReturn(mockDeleteWakeSleepReportCall);

        when(mockDeleteCompletedTestReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.deleteAllParticipantReportRecords(anyString(), eq(COMPLETED_TESTS_REPORT_ID)))
                .thenReturn(mockDeleteCompletedTestReportCall);

        when(mockUpdateParticipantCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockParticipantsApi.updateParticipant(anyString(), any()))
                .thenReturn(mockUpdateParticipantCall);

        ObjectMapper mapper = new ObjectMapper();
        // Work-around for BridgeJavaSdk not exposing IdentifierHolder constructor
        IdentifierHolder identifierHolder = mapper
                .readValue("{\"identifier\":\"abc123\"}", IdentifierHolder.class);
        when(mockSignUpCall.execute())
                .thenReturn(Response.success(identifierHolder));
        when(mockResearcherApi.createParticipant(any()))
                .thenReturn(mockSignUpCall);

        when(mockResearcherApi.getParticipantByExternalId(eq("999999"), eq(false)))
                .thenThrow(new EntityNotFoundException("Account not found", ""));

        // Work-around for BridgeJavaSdk not exposing user ID
        StudyParticipant migratedParticipant = mapper
                .readValue("{\"id\":\"abc123\"}", StudyParticipant.class);
        Map<String, String> migratedAttributes = new HashMap<>();
        migratedAttributes.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_TRUE);
        migratedParticipant.setAttributes(migratedAttributes);
        when(mockGetExternalIdMigratedCall.execute())
                .thenReturn(Response.success(migratedParticipant));
        when(mockResearcherApi.getParticipantByExternalId(eq("d1a5cbaf-288c-48dd-9d4a-98c90213ac01"), eq(false)))
                .thenReturn(mockGetExternalIdMigratedCall);

        // Work-around for BridgeJavaSdk not exposing user ID
        StudyParticipant notMigratedParticipant = mapper
                .readValue("{\"id\":\"abc123\"}", StudyParticipant.class);
        Map<String, String> notMigratedAttributes = new HashMap<>();
        notMigratedAttributes.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_FALSE);
        notMigratedParticipant.setAttributes(migratedAttributes);
        when(mockGetExternalIdNotMigratedCall.execute())
                .thenReturn(Response.success(notMigratedParticipant));
        when(mockResearcherApi.getParticipantByExternalId(eq("999997"), eq(false)))
                .thenReturn(mockGetExternalIdNotMigratedCall);

        String responseJson = "{\"items\":[{\"data\":\"abcdefg\"}]}";
        ForwardCursorReportDataList reportResponse = mapper.readValue(
                responseJson, ForwardCursorReportDataList.class);
        List<ReportData> items = reportResponse.getItems();
        when(mockGetUsersParticipantReportRecordsV4.execute())
                .thenReturn(Response.success(reportResponse));
        when(mockReportsApi.getUsersParticipantReportRecordsV4(
                eq("UserId"), eq("ReportId"), any(), any(), any(), anyInt()))
                .thenReturn(mockGetUsersParticipantReportRecordsV4);

        responseJson = "{\"items\":[{\"identifier\":\"A\"},{\"identifier\":\"B\"}]}";
        StudyList studyList = mapper.readValue(responseJson, StudyList.class);
        when(mockGetAllStudies.execute()).thenReturn(Response.success(studyList));
        when(mockResearcherApi.getStudies(anyInt(), anyInt(), anyBoolean()))
                .thenReturn(mockGetAllStudies);

        StringBuilder externalIdJson = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 100; i++) {
            externalIdJson.append("{\"identifier\":\"").append(i).append("\"}");
        }
        externalIdJson.append("]}");
        ExternalIdentifierList studyAListPage1 = mapper.readValue(
                externalIdJson.toString(), ExternalIdentifierList.class);
        when(mockGetExternalIdsInStudyA.execute())
                .thenReturn(Response.success(studyAListPage1));
        when(mockResearcherApi.getExternalIdsForStudy(
                eq("A"), anyInt(), eq(0), any()))
                .thenReturn(mockGetExternalIdsInStudyA);

        ExternalIdentifierList studyAListPage2 = mapper.readValue(
                "{\"items\":[{\"identifier\":\"100\"}]}", ExternalIdentifierList.class);
        when(mockResearcherApi.getExternalIdsForStudy(
                eq("A"), anyInt(), eq(100), any()))
                .thenReturn(mockGetExternalIdsInStudyA);
    }

    @Test
    public void test_MigrateUser_AccountNotCreatedYet() throws IOException {
        HmDataModel.HmUser user = createNewUser();
        user.externalId = "999999";
        user.arcId = "999999";
        HmDataModel.HmUserData data = createFullNewUserData(user.arcId);

        BridgeJavaSdkUtil.migrateUser(user, data);
        verify(mockSignUpCall).execute();
        verify(mockTestSessionReportCall).execute();
        verify(mockWakeSleepReportCall).execute();
        verify(mockCompletedTestsReportCall).execute();
    }

    @Test
    public void test_MigrateUser_AccountMigrated() throws IOException {
        HmDataModel.HmUser user = createNewUser();
        user.externalId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        user.arcId = "999999";
        user.deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        HmDataModel.HmUserData data = createFullNewUserData(user.arcId);

        BridgeJavaSdkUtil.migrateUser(user, data);
        verify(mockSignUpCall, times(0)).execute();
        verify(mockTestSessionReportCall, times(0)).execute();
        verify(mockWakeSleepReportCall, times(0)).execute();
        verify(mockCompletedTestsReportCall, times(0)).execute();
        verify(mockDeleteTestSessionReportCall).execute();
        verify(mockDeleteWakeSleepReportCall).execute();
        verify(mockDeleteCompletedTestReportCall).execute();
        verify(mockUpdateParticipantCall).execute();
    }

    @Test
    public void test_MigrateUser_AccountNotMigrated() throws IOException {
        HmDataModel.HmUser user = createNewUser();
        user.externalId = "999997";
        user.arcId = "999997";
        HmDataModel.HmUserData data = createFullNewUserData(user.arcId);

        BridgeJavaSdkUtil.migrateUser(user, data);
        verify(mockSignUpCall, times(0)).execute();
        verify(mockTestSessionReportCall).execute();
        verify(mockWakeSleepReportCall).execute();
        verify(mockCompletedTestsReportCall).execute();
    }

    @Test
    public void test_clearMigrationData() throws IOException {
        HmDataModel.HmUser user = createNewUser();
        String userId = "abc123";
        BridgeJavaSdkUtil.clearMigrationData(userId, user);
        verify(mockDeleteTestSessionReportCall).execute();
        verify(mockDeleteWakeSleepReportCall).execute();
        verify(mockDeleteCompletedTestReportCall).execute();
        verify(mockUpdateParticipantCall).execute();
    }

    @Test
    public void test_writeUserReports() throws IOException {
        HmDataModel.HmUserData data = null;

        // No calls to bridge sdk, but it should not throw an exception
        BridgeJavaSdkUtil.writeUserReports("000001", data);

        data = new HmDataModel.HmUserData();
        data.testSessionSchedule = testSessionSchedulePath;

        BridgeJavaSdkUtil.writeUserReports("000001", data);
        verify(mockTestSessionReportCall).execute();
    }

    @Test
    public void test_writeUserReports2() throws IOException {
        HmDataModel.HmUserData data = new HmDataModel.HmUserData();

        data.testSessionSchedule = testSessionSchedulePath;
        data.wakeSleepSchedule = wakeSleepSchedulePath;

        BridgeJavaSdkUtil.writeUserReports("000001", data);
        verify(mockTestSessionReportCall).execute();
        verify(mockWakeSleepReportCall).execute();
    }

    @Test
    public void test_writeUserReports3() throws IOException {
        HmDataModel.HmUserData data = new HmDataModel.HmUserData();;

        data.testSessionSchedule = testSessionSchedulePath;
        data.wakeSleepSchedule = wakeSleepSchedulePath;
        data.completedTests = new HmDataModel.CompletedTestList();

        BridgeJavaSdkUtil.writeUserReports("000001", data);
        verify(mockTestSessionReportCall).execute();
        verify(mockWakeSleepReportCall).execute();
        verify(mockCompletedTestsReportCall).execute();
    }

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
        user.externalId = "000000";
        user.password = password;
        Map<String, String> attributes = BridgeJavaSdkUtil.newUserAttributes(user);
        assertNotNull(attributes);
        assertEquals("000000", attributes.get("ARC_ID"));
        assertEquals(password, attributes.get("VERIFICATION_CODE"));
        assertEquals("No rater assigned yet", attributes.get("RATER_EMAIL"));
        // New users that are Arc IDs do not need migrated, and should not be labeled as such
        assertNull(attributes.get("IS_MIGRATED"));
        assertNull(attributes.get("PHONE_NUMBER"));
        assertNull(attributes.get("SITE_NOTES"));
    }

    @Test
    public void test_newUserAttributes_PhoneAndRater() throws IOException {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String password = "5tm95s?ES?qTx5iGeLmb";
        user.arcId = "000000";
        user.externalId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        user.deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        user.password = password;
        user.phone = "+11111111111";
        user.notes = "Notes";
        HmDataModel.TableRow.Rater rater = new HmDataModel.TableRow.Rater();
        rater.email = "a@b.com";
        user.rater = rater;
        Map<String, String> attributes = BridgeJavaSdkUtil.newUserAttributes(user);
        assertNotNull(attributes);
        assertEquals("000000", attributes.get("ARC_ID"));
        assertEquals(password, attributes.get("VERIFICATION_CODE"));
        assertEquals("+11111111111", attributes.get("PHONE_NUMBER"));
        assertEquals("Notes", attributes.get("SITE_NOTES"));
        assertEquals("false", attributes.get("IS_MIGRATED"));
        assertEquals("a@b.com", attributes.get("RATER_EMAIL"));
    }

    @Test
    public void test_migratedUserAttributes() {
        HmDataModel.HmUser user = createExistingUser();
        Map<String, String> attributes = BridgeJavaSdkUtil.migratedUserAttributes(user);
        assertNotNull(attributes);
        assertEquals(2, attributes.keySet().size());
        assertEquals("true", attributes.get("IS_MIGRATED"));
        assertEquals("000000", attributes.get("ARC_ID"));
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
        HmDataModel.HmUser user = createNewUser();
        SignUp signUp = BridgeJavaSdkUtil.createSignUpObject(user);
        assertNotNull(signUp);
        assertEquals(1, signUp.getExternalIds().keySet().size());
        assertEquals("000000", signUp.getExternalIds().get("DIAN"));
        assertEquals(SharingScope.ALL_QUALIFIED_RESEARCHERS, signUp.getSharingScope());
        assertEquals(0, signUp.getDataGroups().size());
        assertEquals("5tm95s?ES?qTx5iGeLmb", signUp.getPassword());
        assertNotNull(signUp.getAttributes());
    }

    @Test
    public void test_signUpObject_DeviceId() {
        HmDataModel.HmUser user = createExistingUser();
        SignUp signUp = BridgeJavaSdkUtil.createSignUpObject(user);
        assertNotNull(signUp);
        assertEquals(1, signUp.getExternalIds().keySet().size());
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", signUp.getExternalIds().get("DIAN"));
        assertEquals(SharingScope.NO_SHARING, signUp.getSharingScope());
        assertEquals(1, signUp.getDataGroups().size());
        assertEquals("test_user", signUp.getDataGroups().get(0));
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", signUp.getPassword());
        assertNotNull(signUp.getAttributes());
    }

    @Test
    public void test_getAllUsers() {

    }

    @Test
    public void test_getParticipantReportClientDataString() throws IOException {
        String data = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                "UserId", "ReportId");
        verify(mockGetUsersParticipantReportRecordsV4).execute();
        assertNotNull(data);
        assertEquals("abcdefg", data);
    }

    public static HmDataModel.HmUser createExistingUser() {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String arcId = "000000";
        String deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        user.externalId = deviceId;
        user.arcId = arcId;
        user.deviceId = deviceId;
        user.studyId = "DIAN";
        user.password = deviceId;
        return user;
    }

    public static HmDataModel.HmUser createNewUser() {
        HmDataModel.HmUser user = new HmDataModel.HmUser();
        String arcId = "000000";
        String deviceId = "d1a5cbaf-288c-48dd-9d4a-98c90213ac01";
        String password = "5tm95s?ES?qTx5iGeLmb";
        user.externalId = arcId;
        user.arcId = arcId;
        user.studyId = "DIAN";
        user.deviceId = deviceId;
        user.password = password;
        return user;
    }

    public static HmDataModel.HmUserData createNewUserData(String arcId) {
        HmDataModel.HmUserData data = new HmDataModel.HmUserData();
        data.completedTests = null;
        data.testSessionSchedule = null;
        data.wakeSleepSchedule = null;
        data.arcId = arcId;
        return data;
    }

    private HmDataModel.HmUserData createFullNewUserData(String arcId) {
        HmDataModel.HmUserData data = new HmDataModel.HmUserData();
        data.testSessionSchedule = testSessionSchedulePath;
        data.wakeSleepSchedule = wakeSleepSchedulePath;
        data.completedTests = new HmDataModel.CompletedTestList();
        data.arcId = arcId;
        return data;
    }
}