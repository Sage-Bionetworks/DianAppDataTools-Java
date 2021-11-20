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

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.dian.datamigration.MigrationUtil.*;
import static org.sagebionetworks.dian.datamigration.HmDataModel.*;

public class MigrationTests {

    private final Path resourceDirectory = Paths.get("src", "test", "resources");
    private final Path participantsFolder = resourceDirectory.resolve("participants");
    private final Path testSessionsFolder = resourceDirectory.resolve("testSessions");
    private final Path testSessionsSchedulesFolder = resourceDirectory.resolve("testSessionSchedules");
    private final Path wakeSleepSchedulesFolder = resourceDirectory.resolve("wakeSleepSchedules");

    private final String expectedSessionsSchedules000000 =
            "000000 test_session_schedule 2019-08-29T16-06-11Z.json";

    private final String expectedSessionsSchedules000001 =
            "000001 test_session_schedule 2020-01-08T10-18-37Z.json";

    private final String expectedSessionsSchedules000077 =
            "000077 test_session_schedule 2020-11-05T13-03-26Z.json";

    private final String expectedWakeSleepSchedules000000 =
            "000000 Availability 2019-08-29T16-06-11Z.json";

    private final String expectedWakeSleepSchedules000001 =
            "000001 Availability 2020-01-08T10-18-37Z.json";

    private final String expectedWakeSleepSchedules000077 =
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
    public void test_findParticipantFiles() throws IOException {

        Map<ParticipantFileEnum, Path> pathMap =
                MigrationUtil.findParticipantPaths(participantsFolder);

        assertNotNull(pathMap);
        assertEquals(pathMap.keySet().size(), 8);

        Path path = pathMap.get(ParticipantFileEnum.PARTICIPANT);
        assertNotNull(path);
        String filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa-participant-9-21-21.json");

        path = pathMap.get(ParticipantFileEnum.SITE_LOCATION);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa-site_location-9-21-21.json");

        path = pathMap.get(ParticipantFileEnum.RATER);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa-rater-9-21-21.json");

        path = pathMap.get(ParticipantFileEnum.PHONE);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa-participant_phone-9-21-21.json");

        path = pathMap.get(ParticipantFileEnum.PARTICIPANT_RATER);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa_staging_participant_rater-9-21-21.json");

        path = pathMap.get(ParticipantFileEnum.PARTICIPANT_SITE_LOCATION);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa-participant_site_location-9-21-21.json");

        path = pathMap.get(ParticipantFileEnum.PARTICIPANT_DEVICE_ID);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa_staging-participant_device-10-11-21.json");

        path = pathMap.get(ParticipantFileEnum.PARTICIPANT_NOTES);
        assertNotNull(path);
        filename = path.getFileName().toString();
        assertNotNull(filename);
        assertEquals(filename, "sage_qa_staging-participant_note-10-11-21.json");
    }

    @Test
    public void test_createHmUserRaterData() throws IOException {
        List<Path> participantPathList = new ArrayList<>();

        // Test that the app throws a null pointer exception with a null path in the list
        participantPathList.add(null);
        assertThrows(NullPointerException.class, () ->
                MigrationUtil.createHmUserRaterData(participantPathList));

        participantPathList.clear();
        participantPathList.add(participantsFolder);
        List<HmUser> users = MigrationUtil.createHmUserRaterData(participantPathList);

        assertNotNull(users);
        assertEquals(8, users.size());

        // Two participant entries have Arc ID "000001", but this one has a more recent Device ID
        HmUser user = users.get(0);
        assertEquals("000001", user.arcId);
        assertEquals("abc87c9d-5d9b-48a6-943a-48680fd57a2c", user.deviceId);
        assertNull(user.name);
        assertEquals("3-Sage", user.studyId);
        assertEquals("3-Sage", user.studyId);
        assertNull(user.phone);
        assertEquals("rater3@test.edu", user.rater.email);
        assertEquals("3-Sage", user.siteLocation.name);
        assertNull(user.notes);

        user = users.get(1);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", user.externalId);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01Arc#", user.password);
        assertEquals("000002", user.arcId);
        assertEquals("d1a5cbaf-288c-48dd-9d4a-98c90213ac01", user.deviceId);
        assertEquals("EXR_Test2", user.name);
        assertEquals("1-WashU", user.studyId);
        assertNotNull(user.phone);
        assertEquals("+12222222222", user.phone);
        assertEquals("rater1@test.edu", user.rater.email);
        assertEquals("1-WashU", user.siteLocation.name);
        assertEquals("Dropping this participant 11/30/20 by Michelle", user.notes);

        user = users.get(2);
        assertEquals("200007", user.arcId);
        assertEquals("E402924D-34CE-443B-9E53-C0466440D622", user.externalId);
        assertEquals("E402924D-34CE-443B-9E53-C0466440D622Arc#", user.password);
        assertEquals("E402924D-34CE-443B-9E53-C0466440D622", user.deviceId);
        assertEquals("HASD_Test2", user.name);
        assertEquals("2-SDP", user.studyId);
        assertNull(user.phone);
        assertEquals("rater2@test.com", user.rater.email);
        assertEquals("2-SDP", user.siteLocation.name);
        assertNull(user.notes);

        user = users.get(3);
        assertEquals("555555", user.arcId);
        assertEquals("7799c212-49aa-417a-8f8d-a7d50390d558", user.externalId);
        assertEquals("7799c212-49aa-417a-8f8d-a7d50390d558Arc#", user.password);
        assertEquals("7799c212-49aa-417a-8f8d-a7d50390d558", user.deviceId);
        assertEquals("HASD_Test1", user.name);
        assertEquals("2-SDP", user.studyId);
        assertNull(user.phone);
        assertEquals("rater2@test.com", user.rater.email);
        assertEquals("2-SDP", user.siteLocation.name);
        assertEquals("Registered to site A", user.notes);

        user = users.get(4);
        assertEquals("626017", user.arcId);
        assertEquals("193A86E0-892F-4230-9688-2D9E4B1556F9", user.externalId);
        assertEquals("193A86E0-892F-4230-9688-2D9E4B1556F9Arc#", user.password);
        assertEquals("193A86E0-892F-4230-9688-2D9E4B1556F9", user.deviceId);
        assertEquals("OBS_Test1", user.name);
        assertEquals("3-Sage", user.studyId);
        assertNull(user.phone);
        assertEquals("rater3@test.edu", user.rater.email);
        assertEquals("3-Sage", user.siteLocation.name);
        assertNull(user.notes);

        // When a user does not have a device-id, but does have a site location
        // We create a new account for them with the Arc ID.
        user = users.get(5);
        assertEquals("777777", user.arcId);
        assertEquals("777777", user.externalId);
        assertNotNull(user.password);
        assertEquals(9, user.password.length());
        /// No device-id for user
        assertEquals("No-Device-Id", user.deviceId);
        assertNull(user.name);
        assertEquals("3-Sage", user.studyId);
        assertNull(user.phone);
        assertNull(user.rater);
        assertEquals("3-Sage", user.siteLocation.name);
        assertNull(user.notes);

        // When a user does not have a site location,
        // we create a new account for them with the Arc ID
        // And store it in the Happy-Medium-Errors project
        user = users.get(6);
        assertEquals("888888", user.arcId);
        assertEquals("888888", user.externalId);
        assertNotNull(user.password);
        assertEquals(9, user.password.length());
        // No device-id for user
        assertEquals("No-Device-Id", user.deviceId);
        assertNull(user.name);
        assertEquals("Happy-Medium-Errors", user.studyId);
        assertNull(user.phone);
        assertNull(user.rater);
        assertNull(user.siteLocation);
        assertEquals(" Could not find site location ", user.notes);

        user = users.get(7);
        assertEquals("999999", user.arcId);
        assertEquals("cef87c9d-5d9b-48a6-943a-48680fd57a2c", user.deviceId);
        assertEquals("NoRaterYet", user.name);
        assertEquals("3-Sage", user.studyId);
        assertNull(user.phone);
        assertNull(user.rater);
        assertEquals("3-Sage", user.siteLocation.name);
        assertNull(user.notes);

        // We should also check that there are no duplicate Arc IDs in the list
        Set<String> arcIdSet = new HashSet<>();
        for (HmUser arcUser : users) {
            arcIdSet.add(arcUser.arcId);
        }
        assertEquals(arcIdSet.size(), users.size());
    }

    @Test
    public void test_completedTestMap() throws IOException {
        Map<String, HmDataModel.CompletedTestList> map =
                MigrationUtil.completedTestMap(testSessionsFolder);

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
                testSessionsFolder,
                testSessionsSchedulesFolder,
                wakeSleepSchedulesFolder);

        assertNotNull(userList);
        assertEquals(3, userList.size());

        assertEquals("000000", userList.get(0).arcId);
        assertEquals("000001", userList.get(1).arcId);
        assertEquals("000077", userList.get(2).arcId);

        assertNotNull(userList.get(0).completedTests);
        compareTestList(expectedUser000000, userList.get(0).completedTests.completed);
        assertEquals(expectedSessionsSchedules000000, userList.get(0).testSessionSchedule.getFileName().toString());
        assertEquals(expectedWakeSleepSchedules000000, userList.get(0).wakeSleepSchedule.getFileName().toString());

        assertNotNull(userList.get(1).completedTests);
        compareTestList(expectedUser000001, userList.get(1).completedTests.completed);
        assertEquals(expectedSessionsSchedules000001, userList.get(1).testSessionSchedule.getFileName().toString());
        assertEquals(expectedWakeSleepSchedules000001, userList.get(1).wakeSleepSchedule.getFileName().toString());

        // No completed tests, OK to be null, app will create empty list
        assertNull(userList.get(2).completedTests);
        assertEquals(expectedSessionsSchedules000077, userList.get(2).testSessionSchedule.getFileName().toString());
        assertEquals(expectedWakeSleepSchedules000077, userList.get(2).wakeSleepSchedule.getFileName().toString());
    }

    @Test
    public void test_sessionScheduleMap() throws IOException {
        Map<String, Path> map = MigrationUtil.sessionScheduleMap(testSessionsSchedulesFolder);
        assertNotNull(map);
        assertEquals(3, map.size());

        List<String> keys = Arrays.asList(map.keySet().toArray(new String[3]));
        keys.sort(String::compareTo);

        assertEquals("000000", keys.get(0));
        assertEquals("000001", keys.get(1));
        assertEquals("000077", keys.get(2));

        assertEquals(expectedSessionsSchedules000000, map.get("000000").getFileName().toString());
        assertEquals(expectedSessionsSchedules000001, map.get("000001").getFileName().toString());

        // This user had multiple test session schedules, but this is the most recent
        assertEquals(expectedSessionsSchedules000077, map.get("000077").getFileName().toString());
    }

    @Test
    public void test_wakeSleepScheduleMap() throws IOException {
        Map<String, Path> map = MigrationUtil.sessionScheduleMap(wakeSleepSchedulesFolder);
        assertNotNull(map);
        assertEquals(3, map.size());

        List<String> keys = Arrays.asList(map.keySet().toArray(new String[3]));
        keys.sort(String::compareTo);

        assertEquals("000000", keys.get(0));
        assertEquals("000001", keys.get(1));
        assertEquals("000077", keys.get(2));

        assertEquals(expectedWakeSleepSchedules000000, map.get("000000").getFileName().toString());
        assertEquals(expectedWakeSleepSchedules000001, map.get("000001").getFileName().toString());

        // This user had multiple wake sleep schedules, but this is the most recent
        assertEquals(expectedWakeSleepSchedules000077, map.get("000077").getFileName().toString());
    }

    @Test
    public void test_fixParticipantIds() {
        assertEquals("000001", MigrationUtil.fixParticipantId("1"));
        assertEquals("000012", MigrationUtil.fixParticipantId("12"));
        assertEquals("000123", MigrationUtil.fixParticipantId("123"));
        assertEquals("001234", MigrationUtil.fixParticipantId("1234"));
        assertEquals("012345", MigrationUtil.fixParticipantId("12345"));
        assertEquals("123456", MigrationUtil.fixParticipantId("123456"));
        assertEquals("123456", MigrationUtil.fixParticipantId("1234567"));
    }

    @Test
    public void test_bridgifySiteName() {
        String siteName = null;
        assertNull(bridgifySiteName(siteName));
        siteName = "St.Louis' Site";
        assertEquals("StLouisSite", MigrationUtil.bridgifySiteName(siteName));
    }

    @Test
    public void addUniqueUserAndResolveConflicts() {
        List<HmUser> userList = new ArrayList<>();
        HmUser user = BridgeJavaSdkUtilTests.createNewUser();
        user.deviceIdCreatedAt = NO_DEVICE_ID_CREATED_ON;

        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals(1, userList.size());
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(NO_DEVICE_ID_CREATED_ON,  userList.get(0).deviceIdCreatedAt);

        // Attempting to add duplicate user, won't add it again
        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals(1, userList.size());
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(NO_DEVICE_ID_CREATED_ON,  userList.get(0).deviceIdCreatedAt);

        user = BridgeJavaSdkUtilTests.createExistingUser();
        user.deviceIdCreatedAt = 1L;
        // Adding a duplicate ARC ID user with a more recent Device ID, should remove
        // the previous user in the list, and add this new one in.
        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals(1, userList.size());
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(1,  userList.get(0).deviceIdCreatedAt);

        user = BridgeJavaSdkUtilTests.createExistingUser();
        user.arcId = "000001";
        user.deviceIdCreatedAt = 1L;
        // Adding a duplicate ARC ID user with a more recent Device ID, should remove
        // the previous user in the list, and add this new one in.
        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals(2, userList.size());
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(1,  userList.get(0).deviceIdCreatedAt);
        assertEquals("000001", userList.get(1).arcId);
        assertEquals(1, userList.get(1).deviceIdCreatedAt);

        user = BridgeJavaSdkUtilTests.createExistingUser();
        user.arcId = "000001";
        user.deviceIdCreatedAt = 2L;
        // Adding a duplicate ARC ID user with a more recent Device ID, should remove
        // the previous user in the list, and add this new one in.
        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(1, userList.get(0).deviceIdCreatedAt);
        assertEquals("000001", userList.get(1).arcId);
        assertEquals(2, userList.get(1).deviceIdCreatedAt);

        user = BridgeJavaSdkUtilTests.createExistingUser();
        user.arcId = "000001";
        user.deviceIdCreatedAt = NO_DEVICE_ID_CREATED_ON;
        // Adding a duplicate ARC ID user with an older Device ID,
        // should not have it added to the list.
        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(1, userList.get(0).deviceIdCreatedAt);
        assertEquals("000001", userList.get(1).arcId);
        assertEquals(2, userList.get(1).deviceIdCreatedAt);

        user = BridgeJavaSdkUtilTests.createExistingUser();
        user.arcId = "000001";
        user.deviceIdCreatedAt = 1L;
        // Adding a duplicate ARC ID user with an older Device ID,
        // should not have it added to the list.
        MigrationUtil.addUniqueUserAndResolveConflicts(user, userList);
        assertEquals("000000", userList.get(0).arcId);
        assertEquals(1, userList.get(0).deviceIdCreatedAt);
        assertEquals("000001", userList.get(1).arcId);
        assertEquals(2, userList.get(1).deviceIdCreatedAt);
    }
}