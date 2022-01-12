package org.sagebionetworks.dian.datamigration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantReportsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.model.ForwardCursorReportDataList;
import org.sagebionetworks.bridge.rest.model.Message;
import org.sagebionetworks.bridge.rest.model.ReportData;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.dian.datamigration.rescheduler.ReschedulingTool;
import org.sagebionetworks.dian.datamigration.rescheduler.TestSchedule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import retrofit2.Call;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil.ATTRIBUTE_IS_MIGRATED;
import static org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil.ATTRIBUTE_VALUE_TRUE;
import static org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID;

public class ReschedulingToolTests extends Mockito {

    private final Path resourceDirectory = Paths.get(
            "src", "test", "resources", "rescheduleTests");
    private final Path testScheduleSeattlePath =
            resourceDirectory.resolve("TestSessionScheduleSeattle.json");
    private final Path testScheduleChicagoPath =
            resourceDirectory.resolve("TestSessionScheduleChicago.json");
    private final Path testScheduleNoTimezonePath =
            resourceDirectory.resolve("TestSessionScheduleNoTimezone.json");

    private final ZoneOffset zoneSeattle = ZoneOffset.ofHours(-8);
    private final ZoneOffset zoneStLouis = ZoneOffset.ofHours(-6);
    private final ZoneOffset zonePlus1 = ZoneOffset.ofHours(1);

    // Fri Dec 17 2021 20:49:33 GMT+0000
    private final double Dec_17_2021 = 1639774173.0;

    @Mock
    private ForResearchersApi mockResearcherApi;

    @Mock
    private ParticipantReportsApi mockReportsApi;

    @Mock
    private ParticipantsApi mockParticipantsApi;

    @Mock
    private Call<Message> mockTestSessionReportCall;

    @Mock
    private Call<StudyParticipant> mockGetExternalIdMigratedCall;

    @Mock
    private Call<StudyParticipant> mockGetExternalIdMigratedCall2;

    @Mock
    private Call<ForwardCursorReportDataList> mockGetUsersParticipantReportRecordsV4;

    @Mock
    private Call<ForwardCursorReportDataList> mockGetUsersParticipantReport2RecordsV4;

    @Before
    public void before() throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        MockitoAnnotations.initMocks(this);
        BridgeJavaSdkUtil.mockInitialize(mockResearcherApi, mockReportsApi, mockParticipantsApi);

        when(mockTestSessionReportCall.execute())
                .thenReturn(Response.success(new Message()));
        when(mockReportsApi.addParticipantReportRecordV4(
                anyString(), eq(TEST_SCHEDULE_REPORT_ID), any()))
                .thenReturn(mockTestSessionReportCall);

        String reportDataStr = PathsHelper.readFile(testScheduleSeattlePath);
        reportDataStr = reportDataStr.replace("\"", "\\\"");
        String responseJson = "{\"items\":[{\"data\":\"" + reportDataStr + "\"}]}";
        ForwardCursorReportDataList reportResponse = mapper.readValue(
                responseJson, ForwardCursorReportDataList.class);
        when(mockGetUsersParticipantReportRecordsV4.execute())
                .thenReturn(Response.success(reportResponse));
        when(mockReportsApi.getUsersParticipantReportRecordsV4(
                eq("abc123"), any(), any(), any(), any(), anyInt()))
                .thenReturn(mockGetUsersParticipantReportRecordsV4);

        // Work-around for BridgeJavaSdk not exposing user ID
        StudyParticipant migratedParticipant = mapper
                .readValue("{\"id\":\"abc123\"}", StudyParticipant.class);
        Map<String, String> migratedAttributes = new HashMap<>();
        migratedAttributes.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_TRUE);
        migratedParticipant.setAttributes(migratedAttributes);
        when(mockGetExternalIdMigratedCall.execute())
                .thenReturn(Response.success(migratedParticipant));
        when(mockResearcherApi.getParticipantByExternalId(eq("000000"), eq(false)))
                .thenReturn(mockGetExternalIdMigratedCall);

        String reportDataStr2 = PathsHelper.readFile(testScheduleNoTimezonePath);
        reportDataStr2 = reportDataStr2.replace("\"", "\\\"");
        String responseJson2 = "{\"items\":[{\"data\":\"" + reportDataStr2 + "\"}]}";
        ForwardCursorReportDataList reportResponse2 = mapper.readValue(
                responseJson2, ForwardCursorReportDataList.class);
        when(mockGetUsersParticipantReport2RecordsV4.execute())
                .thenReturn(Response.success(reportResponse2));
        when(mockReportsApi.getUsersParticipantReportRecordsV4(
                eq("abc456"), any(), any(), any(), any(), anyInt()))
                .thenReturn(mockGetUsersParticipantReport2RecordsV4);

        StudyParticipant migratedParticipant2 = mapper
                .readValue("{\"id\":\"abc456\"}", StudyParticipant.class);
        Map<String, String> migratedAttributes2 = new HashMap<>();
        migratedAttributes2.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_TRUE);
        migratedParticipant2.setAttributes(migratedAttributes2);
        when(mockGetExternalIdMigratedCall2.execute())
                .thenReturn(Response.success(migratedParticipant2));
        when(mockResearcherApi.getParticipantByExternalId(eq("000001"), eq(false)))
                .thenReturn(mockGetExternalIdMigratedCall2);

    }

    private TestSchedule createTestSchedule(Path path) throws IOException {
        return new ObjectMapper().readValue(PathsHelper.readFile(path), TestSchedule.class);
    }

    @Test
    public void test_rescheduleTestCycle1By1Day() throws IOException {
        // User 000000, Test cycle 1 starts at Fri Dec 03 2021 20:27:11 GMT-0800 (PST)
        // Move this user from 12-3 to 12-4 (one day)
        String userInput = "000000\n1\n2021-12-04\ny\nn";
        InputStream in = null;
        Scanner scanner = null;
        try {
            in = new ByteArrayInputStream(userInput.getBytes());
            System.setIn(in);
            scanner = new Scanner(System.in);

            ReschedulingTool.rescheduleUser(scanner);
            verify(mockGetExternalIdMigratedCall).execute();
            verify(mockGetUsersParticipantReportRecordsV4).execute();
            verify(mockTestSessionReportCall).execute();

        } finally {
            if (in != null) {
                in.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    @Test
    public void test_rescheduleTestCycleNoTimezoneOffsetOrName() throws IOException {
        // User 000000, Test cycle 1 starts at Fri Dec 03 2021 20:27:11 GMT-0800 (PST)
        // Move this user from 12-3 to 12-4 (one day)
        String userInput = "000001\n-8\nPacific Standard Time\n1\n2021-12-04\ny\nn";
        InputStream in = null;
        Scanner scanner = null;
        try {
            in = new ByteArrayInputStream(userInput.getBytes());
            System.setIn(in);
            scanner = new Scanner(System.in);

            TestSchedule testSchedule = ReschedulingTool.rescheduleUser(scanner);
            assertEquals("Pacific Standard Time", testSchedule.timezone_name);
            assertEquals("-8", testSchedule.timezone_offset);
            verify(mockGetExternalIdMigratedCall2).execute();
            verify(mockGetUsersParticipantReport2RecordsV4).execute();
            verify(mockTestSessionReportCall).execute();

        } finally {
            if (in != null) {
                in.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    @Test
    public void test_rescheduleTestCycleAndChangeSession0() throws IOException {
        // User 000000, Test cycle 1 starts at Fri Dec 03 2021 20:27:11 GMT-0800 (PST)
        // Move this user from 12-3 to 12-4 (one day)
        // Change this user's session 0 by 1 hour
        String userInput = "000000\n1\n2021-12-04\ny\ny\n0,0\n1\ny\nn";
        InputStream in = null;
        Scanner scanner = null;
        try {
            in = new ByteArrayInputStream(userInput.getBytes());
            System.setIn(in);
            scanner = new Scanner(System.in);

            ReschedulingTool.rescheduleUser(scanner);
            verify(mockGetExternalIdMigratedCall).execute();
            verify(mockGetUsersParticipantReportRecordsV4).execute();
            verify(mockTestSessionReportCall).execute();

        } finally {
            if (in != null) {
                in.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    @Test
    public void test_rescheduleTestCycleAndChangeSession0Twice() throws IOException {
        // User 000000, Test cycle 1 starts at Fri Dec 03 2021 20:27:11 GMT-0800 (PST)
        // Move this user from 12-3 to 12-4 (one day)
        // Change this user's session 0 by 1 hour
        String userInput = "000000\n1\n2021-12-04\ny" +
            "\ny\n0,0\n1\ny\n" +
            "y\n0,0\n-2\ny\nn";
        InputStream in = null;
        Scanner scanner = null;
        try {
            in = new ByteArrayInputStream(userInput.getBytes());
            System.setIn(in);
            scanner = new Scanner(System.in);

            ReschedulingTool.rescheduleUser(scanner);
            verify(mockGetExternalIdMigratedCall).execute();
            verify(mockGetUsersParticipantReportRecordsV4).execute();
            verify(mockTestSessionReportCall).execute();

        } finally {
            if (in != null) {
                in.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    @Test
    public void test_timeDifference() throws IOException {
        TestSchedule testSchedule = createTestSchedule(testScheduleSeattlePath);
        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionMap =
                ReschedulingTool.convertToMap(testSchedule.sessions);
        ZoneOffset seattleZone = ReschedulingTool.parseZone(testSchedule.timezone_offset);

        // This should be the session at UTC 1638592031
        // Fri Dec 03 2021 20:27:11 GMT-0800 (PST)
        TestSchedule.TestScheduleSession day0Session0 =
                ReschedulingTool.findFirstTest(sessionMap.get(1));

        long timeDiff = ReschedulingTool.timeDifference("2021-12-04", day0Session0, seattleZone);
        // 1 day forward is 86400 seconds
        assertEquals(86400, timeDiff);

        timeDiff = ReschedulingTool.timeDifference("2021-12-02", day0Session0, seattleZone);
        // 1 day back is -86400 seconds
        assertEquals(-86400, timeDiff);

        timeDiff = ReschedulingTool.timeDifference("2021-12-17", day0Session0, seattleZone);
        // Dec 3 - Dec 17 should be 14 days or 1209600
        assertEquals(1209600, timeDiff);

        testSchedule = createTestSchedule(testScheduleChicagoPath);
        sessionMap = ReschedulingTool.convertToMap(testSchedule.sessions);
        ZoneOffset chicagoZone = ReschedulingTool.parseZone(testSchedule.timezone_offset);

        // This should be the session at UTC 1638592031
        // Thu May 28 2020 07:23:59 GMT-0700 (PDT)
        day0Session0 = ReschedulingTool.findFirstTest(sessionMap.get(1));

        timeDiff = ReschedulingTool.timeDifference("2020-05-29", day0Session0, chicagoZone);
        // 1 day forward is 86400 seconds
        assertEquals(86400, timeDiff);

        timeDiff = ReschedulingTool.timeDifference("2020-05-27", day0Session0, chicagoZone);
        // 1 day back is -86400 seconds
        assertEquals(-86400, timeDiff);

        timeDiff = ReschedulingTool.timeDifference("2020-06-11", day0Session0, chicagoZone);
        // Dec 3 - Dec 17 should be 14 days or 1209600
        assertEquals(1209600, timeDiff);

        // Now check daylight savings time
        // Sat Mar 13 2021 08:00:00 GMT-0800 (PST)
        day0Session0.session_date = 1615651200.0;
        timeDiff = ReschedulingTool.timeDifference("2021-03-14", day0Session0, seattleZone);
        // This value should be 1 hour less since we lose an hour for daylight savings time
        // 1 day forward is 86400 seconds minus one hour 3600 is 82800
        String sessionDate1 = ReschedulingTool.sessionDateString(day0Session0, seattleZone);
        day0Session0.session_date += timeDiff;
        String sessionDate2 = ReschedulingTool.sessionDateString(day0Session0, seattleZone);
        assertEquals("2021-03-13T08:00:00-08:00", sessionDate1);
        assertEquals("2021-03-14T08:00:00-08:00", sessionDate2);
    }

    @Test
    public void test_parseZone() {
        // Test Pacific time zone
        assertEquals(ZoneOffset.ofHours(-8),
                ReschedulingTool.parseZone("UTC-08:00"));
        assertEquals(ZoneOffset.ofHours(-8),
                ReschedulingTool.parseZone("-08:00"));
        assertEquals(ZoneOffset.ofHours(-8),
                ReschedulingTool.parseZone("-8"));

        // Test positive time zone
        assertEquals(ZoneOffset.ofHours(8),
                ReschedulingTool.parseZone("UTC+08:00"));
        assertEquals(ZoneOffset.ofHours(8),
                ReschedulingTool.parseZone("+08:00"));
        assertEquals(ZoneOffset.ofHours(8),
                ReschedulingTool.parseZone("8"));
        assertEquals(ZoneOffset.ofHours(8),
                ReschedulingTool.parseZone("+8"));

        // Test St Louis time zone
        assertEquals(ZoneOffset.ofHours(-6),
                ReschedulingTool.parseZone("UTC-06:00"));
        assertEquals(ZoneOffset.ofHours(-6),
                ReschedulingTool.parseZone("-06:00"));
        assertEquals(ZoneOffset.ofHours(-6),
                ReschedulingTool.parseZone("-6"));
    }

    @Test
    public void test_convertSessionsToMap() throws IOException {

        TestSchedule testSchedule = createTestSchedule(testScheduleSeattlePath);
        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionMap =
                ReschedulingTool.convertToMap(testSchedule.sessions);
        assertNotNull(sessionMap);
        assertEquals(10, sessionMap.keySet().size());
        assertEquals(29, sessionMap.get(1).size());
        for (int i = 2; i <= 10; i++) {
            assertEquals("Testing map with value i = " + i,
                    28, sessionMap.get(i).size());
        }

        testSchedule = createTestSchedule(testScheduleChicagoPath);
        sessionMap = ReschedulingTool.convertToMap(testSchedule.sessions);
        assertNotNull(sessionMap);
        assertEquals(10, sessionMap.keySet().size());
        assertEquals(29, sessionMap.get(1).size());
        for (int i = 2; i <= 10; i++) {
            assertEquals("Testing map with value i = " + i,
                    28, sessionMap.get(i).size());
        }
    }

    @Test
    public void test_writeReportToBridge() throws IOException {
        TestSchedule testSchedule = createTestSchedule(testScheduleSeattlePath);
        ReschedulingTool.writeScheduleToBridge("000000", testSchedule);
        verify(mockTestSessionReportCall).execute();
    }

    @Test
    public void test_findFirstSession() throws IOException {
        TestSchedule testSchedule = createTestSchedule(testScheduleSeattlePath);
        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionMap =
                ReschedulingTool.convertToMap(testSchedule.sessions);

        TestSchedule.TestScheduleSession session =
                ReschedulingTool.findFirstTest(sessionMap.get(1));
        assertNotNull(session);
        assertEquals("0", session.session_id);

        // Remove the first session, and the Day 1 should still be found
        sessionMap.get(1).remove(0);
        session = ReschedulingTool.findFirstTest(sessionMap.get(1));
        assertNotNull(session);
        assertEquals("1", session.session_id);

        // Remove the first Day 1 session, and function should return null
        sessionMap.get(1).remove(0);
        session = ReschedulingTool.findFirstTest(sessionMap.get(1));
        assertNull(session);

        session = ReschedulingTool.findFirstTest(sessionMap.get(10));
        assertNotNull(session);
        assertEquals("253", session.session_id);

        // Remove the first session, and the Day 1 should still be found
        sessionMap.get(10).remove(0);
        session = ReschedulingTool.findFirstTest(sessionMap.get(10));
        assertNotNull(session);
        assertEquals("257", session.session_id);

        // Remove the first and second Day's sessions, and function should return null
        sessionMap.get(10).remove(0);
        sessionMap.get(10).remove(0);
        sessionMap.get(10).remove(0);
        sessionMap.get(10).remove(0);
        session = ReschedulingTool.findFirstTest(sessionMap.get(10));
        assertNull(session);
    }

    @Test
    public void test_findSession() throws IOException {
        TestSchedule testSchedule = createTestSchedule(testScheduleSeattlePath);
        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionMap =
            ReschedulingTool.convertToMap(testSchedule.sessions);

        TestSchedule.TestScheduleSession session =
                ReschedulingTool.findSession(sessionMap.get(1), 0, 0);
        assertNotNull(session);
        assertEquals("0", session.session_id);

        session = ReschedulingTool.findSession(sessionMap.get(1), 0, 5);
        assertNull(session);

        session = ReschedulingTool.findSession(sessionMap.get(1), 1, 0);
        assertNotNull(session);
        assertEquals("1", session.session_id);

        session = ReschedulingTool.findSession(sessionMap.get(10), 6, 3);
        assertNotNull(session);
        assertEquals("280", session.session_id);
    }

    @Test
    public void test_sessionDateString() {
        TestSchedule.TestScheduleSession session = new TestSchedule.TestScheduleSession();
        session.session_date = Dec_17_2021;
        assertEquals("2021-12-17T12:49:33-08:00",
                ReschedulingTool.sessionDateString(session, zoneSeattle));
        assertEquals("2021-12-17T14:49:33-06:00",
                ReschedulingTool.sessionDateString(session, zoneStLouis));
        assertEquals("2021-12-17T21:49:33+01:00",
                ReschedulingTool.sessionDateString(session, zonePlus1));
    }

    @Test
    public void test_shouldContinueYN() throws IOException {
        assertTrue(shouldContinueWith("y"));
        assertTrue(shouldContinueWith("Y"));
        assertTrue(shouldContinueWith("abcdefg\ny"));
        assertTrue(shouldContinueWith("a\nb\nC\nY"));
        assertFalse(shouldContinueWith("n"));
        assertFalse(shouldContinueWith("abcdefg\nn"));
        assertFalse(shouldContinueWith("N"));
        assertFalse(shouldContinueWith("a\nb\nN"));
    }

    private boolean shouldContinueWith(String answer) throws IOException {
        InputStream in = null;
        Scanner scanner = null;
        try {
            in = new ByteArrayInputStream(answer.getBytes());
            System.setIn(in);
            scanner = new Scanner(System.in);
            return ReschedulingTool.shouldContinueYN(scanner);
        } finally {
            if (in != null) {
                in.close();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }
}
