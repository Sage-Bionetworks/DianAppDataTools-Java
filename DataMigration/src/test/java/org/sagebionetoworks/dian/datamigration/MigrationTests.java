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

package org.sagebionetoworks.dian.datamigration;

import com.google.common.collect.Lists;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetoworks.dian.datamigration.BridgeUtil.*;
import static org.sagebionetoworks.dian.datamigration.MigrationUtil.*;

public class MigrationTests {

    private final String rootParticipantsFolderStr =
            "src/test/java/org/sagebionetoworks/dian/datamigration/participants";
    private final File rootParticipantsFolder = new File(rootParticipantsFolderStr);

    private final String rootTestSessionsFolderStr =
            "src/test/java/org/sagebionetoworks/dian/datamigration/testSessions";
    private final File rootTestSessionsFolder = new File(rootTestSessionsFolderStr);

    private final String rootTestSessionsSchedulesFolderStr =
            "src/test/java/org/sagebionetoworks/dian/datamigration/testSessionSchedules";
    private final File rootTestSessionsSchedulesFolder = new File(rootTestSessionsSchedulesFolderStr);

    private final String rootWakeSleepSchedulesFolderStr =
            "src/test/java/org/sagebionetoworks/dian/datamigration/wakeSleepSchedules";
    private final File rootWakeSleepSchedulesFolder = new File(rootWakeSleepSchedulesFolderStr);

    private final String expectedSessionsSchedules000000 = rootTestSessionsSchedulesFolderStr +
            "/test_session_schedules_2021-07-08/" +
            "000000 test_session_schedule 2019-08-29T16-06-11Z.json";

    private final String expectedSessionsSchedules000001 = rootTestSessionsSchedulesFolderStr +
            "/test_session_schedules_2021-07-08/" +
            "000001 test_session_schedule 2020-01-08T10-18-37Z.json";

    private final String expectedSessionsSchedules000077 = rootTestSessionsSchedulesFolderStr +
            "/test_session_schedules_2021-07-09/" +
            "000077 test_session_schedule 2020-11-05T13-03-26Z.json";

    private final String expectedWakeSleepSchedules000000 = rootWakeSleepSchedulesFolderStr +
            "/wake_sleep_schedules_08-07-21/" +
            "000000 Availability 2019-08-29T16-06-11Z.json";

    private final String expectedWakeSleepSchedules000001 = rootWakeSleepSchedulesFolderStr +
            "/wake_sleep_schedules_08-07-21/" +
            "000001 Availability 2020-01-08T10-18-37Z.json";

    private final String expectedWakeSleepSchedules000077 = rootWakeSleepSchedulesFolderStr +
            "/wake_sleep_schedules_08-08-21/" +
            "000077 Availability 2020-11-05T13-03-26Z.json";

    List<HmDataModel.CompletedTest> expectedUser000000 = Arrays.asList(
            new HmDataModel.CompletedTest(
                    0, 0, 0, 1567112771.182),
            new HmDataModel.CompletedTest(
                    0, 1, 0, 1567174360),
            new HmDataModel.CompletedTest(
                    0, 1, 1, 1567181794),
            new HmDataModel.CompletedTest(
                    0, 2, 0, 1567261629),
            new HmDataModel.CompletedTest(
                    0, 5, 2, 1567536042),
            new HmDataModel.CompletedTest(
                    25, 0, 0, 1567546534));

    List<HmDataModel.CompletedTest> expectedUser000001 = Arrays.asList(
            new HmDataModel.CompletedTest(
            0, 0, 0, 1567112771.999),
            new HmDataModel.CompletedTest(
            0, 1, 0, 1567174360),
            new HmDataModel.CompletedTest(
            0, 1, 1, 1567181794));

    @Test
    public void test_findParticipantFiles() {
        HmDataModel.TableRow.ParticipantFiles participantFiles =
                MigrationUtil.findParticipantFiles(rootParticipantsFolder);

        assertNotNull(participantFiles);

        assertNotNull(participantFiles.participants);
        assertTrue(participantFiles.participants.getAbsolutePath().endsWith(
                rootParticipantsFolderStr + "/sage_qa-participant-9-21-21.json"));

        assertNotNull(participantFiles.siteLocations);
        assertTrue(participantFiles.siteLocations.getAbsolutePath().endsWith(
                rootParticipantsFolderStr + "/sage_qa-site_location-9-21-21.json"));

        assertNotNull(participantFiles.raters);
        assertTrue(participantFiles.raters.getAbsolutePath().endsWith(
                rootParticipantsFolderStr + "/sage_qa-rater-9-21-21.json"));

        assertNotNull(participantFiles.phone);
        assertTrue(participantFiles.phone.getAbsolutePath().endsWith(
                rootParticipantsFolderStr + "/sage_qa-participant_phone-9-21-21.json"));

        assertNotNull(participantFiles.participantRaters);
        assertTrue(participantFiles.participantRaters.getAbsolutePath().endsWith(
                rootParticipantsFolderStr + "/sage_qa_staging_participant_rater-9-21-21.json"));

        assertNotNull(participantFiles.participantSiteLocations);
        assertTrue(participantFiles.participantSiteLocations.getAbsolutePath().endsWith(
                rootParticipantsFolderStr + "/sage_qa-participant_site_location-9-21-21.json"));
    }

     @Test
    public void test_createHmUserRaterData() throws IOException {
        List<MigrationUtil.HmUser> users = MigrationUtil
                .createHmUserRaterData(Lists.newArrayList(rootParticipantsFolder));
        assertNotNull(users);
        assertEquals(6, users.size());

        MigrationUtil.HmUser user = users.get(0);
        assertEquals("000001", user.arcId);
        assertEquals("EXR_Test1", user.name);
        assertEquals(STUDY_ID_DIAN_ARC_EXR, user.studyId);
        assertNotNull(user.phone);
        assertEquals("+11111111111", user.phone);
        assertEquals("rater1@test.edu", user.raterEmail);
        assertEquals("1-WashU", user.siteLocationName);

        user = users.get(1);
        assertEquals("000002", user.arcId);
        assertEquals("EXR_Test2", user.name);
        assertEquals(STUDY_ID_DIAN_ARC_EXR, user.studyId);
        assertNotNull(user.phone);
        assertEquals("+12222222222", user.phone);
        assertEquals("rater1@test.edu", user.raterEmail);
        assertEquals("1-WashU", user.siteLocationName);

        user = users.get(2);
        assertEquals("200007", user.arcId);
        assertEquals("HASD_Test2", user.name);
        assertEquals(STUDY_ID_MAP_ARC_HASD, user.studyId);
        assertNull(user.phone);
        assertEquals("rater2@test.com", user.raterEmail);
        assertEquals("2-SDP", user.siteLocationName);

        user = users.get(3);
        assertEquals("555555", user.arcId);
        assertEquals("HASD_Test1", user.name);
        assertEquals(STUDY_ID_MAP_ARC_HASD, user.studyId);
        assertNull(user.phone);
        assertEquals("rater2@test.com", user.raterEmail);
        assertEquals("2-SDP", user.siteLocationName);

        user = users.get(4);
        assertEquals("626017", user.arcId);
        assertEquals("OBS_Test1", user.name);
        assertEquals(STUDY_ID_DIAN_OBS_EXR, user.studyId);
        assertNull(user.phone);
        assertEquals("rater3@test.edu", user.raterEmail);
        assertEquals("3-Sage", user.siteLocationName);

        user = users.get(5);
        assertEquals("999999", user.arcId);
        assertEquals("NoRaterYet", user.name);
        assertEquals(STUDY_ID_MAP_ARC_HASD, user.studyId);
        assertNull(user.phone);
        assertEquals(NO_RATER_ASSIGNED_YET_EMAIL, user.raterEmail);
        assertEquals("3-Sage", user.siteLocationName);
    }

    @Test
    public void test_completedTestMap() throws IOException {
        Map<String, HmDataModel.CompletedTestList> map =
                MigrationUtil.completedTestMap(rootTestSessionsFolder);

        assertNotNull(map);
        assertEquals(2, map.keySet().size());

        List<String> keys = Arrays.asList(map.keySet().toArray(new String[2]));
        keys.sort(String::compareTo);

        assertEquals("000000", keys.get(0));
        HmDataModel.CompletedTestList testList = map.get(keys.get(0));
        assertNotNull(testList);
        compareTestList(expectedUser000000, testList.completed);

        assertEquals("000001", keys.get(1));
        testList = map.get(keys.get(1));
        assertNotNull(testList);
        compareTestList(expectedUser000001, testList.completed);
    }

    private void compareTestList(List<HmDataModel.CompletedTest> expected,
                                 List<HmDataModel.CompletedTest> actual) {

        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).week, actual.get(i).week);
            assertEquals(expected.get(i).day, actual.get(i).day);
            assertEquals(expected.get(i).session, actual.get(i).session);
            assertEquals(expected.get(i).completedOn, actual.get(i).completedOn, 0.001);
        }
    }

    @Test
    public void test_createHmUserData() throws IOException {
        List<HmUserData> userList = MigrationUtil.createHmUserData(
                rootTestSessionsFolder,
                rootTestSessionsSchedulesFolder,
                rootWakeSleepSchedulesFolder);

        assertNotNull(userList);
        assertEquals(3, userList.size());

        assertEquals("000000", userList.get(0).arcId);
        assertEquals("000001", userList.get(1).arcId);
        assertEquals("000077", userList.get(2).arcId);

        assertNotNull(userList.get(0).completedTests);
        compareTestList(expectedUser000000, userList.get(0).completedTests.completed);
        assertTrue(userList.get(0).testSessionSchedule.getAbsolutePath().endsWith(expectedSessionsSchedules000000));
        assertTrue(userList.get(0).wakeSleepSchedule.getAbsolutePath().endsWith(expectedWakeSleepSchedules000000));

        assertNotNull(userList.get(1).completedTests);
        compareTestList(expectedUser000001, userList.get(1).completedTests.completed);
        assertTrue(userList.get(1).testSessionSchedule.getAbsolutePath().endsWith(expectedSessionsSchedules000001));
        assertTrue(userList.get(1).wakeSleepSchedule.getAbsolutePath().endsWith(expectedWakeSleepSchedules000001));

        // No completed tests, OK to be null, app will create empty list
        assertNull(userList.get(2).completedTests);
        assertTrue(userList.get(2).testSessionSchedule.getAbsolutePath().endsWith(expectedSessionsSchedules000077));
        assertTrue(userList.get(2).wakeSleepSchedule.getAbsolutePath().endsWith(expectedWakeSleepSchedules000077));
    }

    @Test
    public void test_sessionScheduleMap() throws IOException {
        Map<String, File> map = MigrationUtil.sessionScheduleMap(rootTestSessionsSchedulesFolder);
        assertNotNull(map);
        assertEquals(3, map.size());

        List<String> keys = Arrays.asList(map.keySet().toArray(new String[3]));
        keys.sort(String::compareTo);

        assertEquals("000000", keys.get(0));
        assertEquals("000001", keys.get(1));
        assertEquals("000077", keys.get(2));

        assertTrue(map.get(keys.get(0)).getAbsolutePath().endsWith(expectedSessionsSchedules000000));
        assertTrue(map.get(keys.get(1)).getAbsolutePath().endsWith(expectedSessionsSchedules000001));

        // This user had multiple test session schedules, but this is the most recent
        assertTrue(map.get(keys.get(2)).getAbsolutePath().endsWith(expectedSessionsSchedules000077));
    }

    @Test
    public void test_wakeSleepScheduleMap() throws IOException {
        Map<String, File> map = MigrationUtil.sessionScheduleMap(rootWakeSleepSchedulesFolder);
        assertNotNull(map);
        assertEquals(3, map.size());

        List<String> keys = Arrays.asList(map.keySet().toArray(new String[3]));
        keys.sort(String::compareTo);

        assertEquals("000000", keys.get(0));
        assertEquals("000001", keys.get(1));
        assertEquals("000077", keys.get(2));

        assertTrue(map.get(keys.get(0)).getAbsolutePath().endsWith(expectedWakeSleepSchedules000000));
        assertTrue(map.get(keys.get(1)).getAbsolutePath().endsWith(expectedWakeSleepSchedules000001));

        // This user had multiple wake sleep schedules, but this is the most recent
        assertTrue(map.get(keys.get(2)).getAbsolutePath().endsWith(expectedWakeSleepSchedules000077));
    }

    @Test
    public void test_fixParticipantIds() {
        assertEquals("000001", MigrationUtil.fixParticipantId("1"));
        assertEquals("000012", MigrationUtil.fixParticipantId("12"));
        assertEquals("000123", MigrationUtil.fixParticipantId("123"));
        assertEquals("001234", MigrationUtil.fixParticipantId("1234"));
        assertEquals("012345", MigrationUtil.fixParticipantId("12345"));
        assertEquals("123456", MigrationUtil.fixParticipantId("123456"));
    }

    @Test
    public void test_uniqueRaterIds() {
        int count = 5;
        List<String> uniqueRaterIds = MigrationUtil.assignUniqueRaterIds(count);
        assertNotNull(uniqueRaterIds);
        assertEquals(count, uniqueRaterIds.size());
        for (int i = 0; i < count; i++) {
            // All rater IDs must be 6 digits
            assertEquals(6, uniqueRaterIds.get(i).length());
            for (int j = 0; j < 6; j++) {
                char c = uniqueRaterIds.get(i).substring(j, j+1).toCharArray()[0];
                assertTrue(c >= '0' && c <= '9');
            }
        }
    }
}