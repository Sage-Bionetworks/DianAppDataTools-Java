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

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormatter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageScheduleController;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1Schedule;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1StudyBurst;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV2Availability;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule;
import org.sagebionetworks.dian.datamigration.tools.schedulev2.ScheduleV2Migration;
import org.sagebionetworks.dian.datamigration.tools.schedulev2.WakeSleepSchedule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SageScheduleControllerTests {

    private final Path resourceDirectory = Paths.get("src", "test", "resources");
    private final Path rootFileTestFolder = resourceDirectory.resolve("bridge2MigrationTests");
    private final Path all_schedules = rootFileTestFolder.resolve("all_schedules");
    private final Path all_availability = rootFileTestFolder.resolve("all_availability");
    private final Path scheduleJson = rootFileTestFolder.resolve("000050_V1_Schedule.json");
    private final Path scheduleMalformedJson = rootFileTestFolder.resolve("Malformed_V1_Schedule.json");
    private final Path availabilityJson = rootFileTestFolder.resolve("000050_Availability.json");
    private final Path scheduleV2Json = rootFileTestFolder.resolve("000050_V2_Schedule.json");
    private final SageScheduleController controller = new SageScheduleController();

    // These users all have 1 study burst in their schedule and that is it, then they withdrew
    // 595 = 749590, 564 = 877968, 575 = 647513, 584 = 362561, 586 = 326341,
    // 613 = 341038, 627 = 201389
    // all show up in https://sagebionetworks.jira.com/browse/DIAN-231

    // 614 = 999996 is a test user
    public static final List<Integer> withdrawnUsers = Arrays.asList(
            564, 575, 584, 586, 595, 613, 614, 627);
    // see issue https://sagebionetworks.jira.com/browse/DIAN-342
    // 1020 = 361724 this user had their
    // 1225 = https://sagebionetworks.jira.com/browse/DIAN-342
    public static final List<Integer> skipNeedsManualIntervention = Arrays.asList(
            1020, 1225);
    // These major rescheduled study bursts should be skipped and accepted when testing
    // validation of the study burst format
    // 1245 = 463805, has not enough time between study bursts, Marisol did this, we are fine
    // 1253 = 793677, has not enough time between study bursts
    // 1292 = 414864, has not enough time between study bursts, https://sagebionetworks.jira.com/browse/DIAN-459
    public static final List<Integer> skipMajorReschedules = Arrays.asList(
            1245, 1253, 1292);//Arrays.asList(928, 938);

    public String getScheduleJson(String fileId) throws IOException {
        Path path = all_schedules.resolve(fileId + ".json");
        if (!Files.exists(path)) {
            return null;
        }
        return PathsHelper.readFile(path);
    }

    public String getAvailabilityJson(String fileId) throws IOException {
        Path path = all_availability.resolve(fileId + ".json");
        if (!Files.exists(path)) {
            return null;
        }
        return PathsHelper.readFile(path);
    }

    private final Path testScheduleSeattlePath =
            resourceDirectory.resolve("TestSessionScheduleSeattle.json");
    private final Path testScheduleChicagoPath =
            resourceDirectory.resolve("TestSessionScheduleChicago.json");
    private final Path testScheduleNoTimezonePath =
            resourceDirectory.resolve("TestSessionScheduleNoTimezone.json");

    public static Map<String, String> arcTimeZoneAnswerMap = new HashMap<String, String>() {{
        put("Mountain Standard Time", "US/Mountain");
        put("Australian Western Standard Time", "Australia/Perth");
        put("Australian Eastern Standard Time", "Australia/Brisbane");
        put("Argentina Standard Time", "America/Argentina/Buenos_Aires");
        put("Colombia Standard Time", "America/Bogota");
        put("Pacific Standard Time", "US/Pacific");
        put("Japan Standard Time", "Asia/Tokyo");
        put("Eastern Standard Time", "US/Eastern");
        put("Greenwich Mean Time", "UTC");
        put("Central Standard Time", "US/Central");
        put("America/Moncton", "America/Moncton");
        put("America/Boise", "America/Boise");
        put("America/Denver", "America/Denver");
        put("America/Puerto_Rico", "America/Puerto_Rico");
        put("America/New_York", "America/New_York");
        put("America/Toronto", "America/Toronto");
        put("America/Phoenix", "America/Phoenix");
        put("America/Los_Angeles", "America/Los_Angeles");
        put("America/Anchorage", "America/Anchorage");
        put("America/Chicago", "America/Chicago");
        put("America/Mexico_City", "America/Mexico_City");
        put("America/Indiana/Indianapolis", "America/Indiana/Indianapolis");
        put("America/Vancouver", "America/Vancouver");
        put("Australia/Perth", "Australia/Perth");
        put("Europe/Madrid", "Europe/Madrid");
        put("US/Central", "US/Central");
        put("US/Pacific", "US/Pacific");
        put("West Greenland Standard Time", "America/Nuuk");
    }};

    // Fri Dec 17 2021 20:49:33 GMT+0000
    private final double Dec_17_2021 = 1639774173.0;
    private final DateTime Dec_17_2021_DateTime = new DateTime((long)Dec_17_2021);

    public HashMap<String, SageV2Availability> timeTestMap =
        new HashMap<String, SageV2Availability>() {
            {
                put("8:30 AM", new SageV2Availability(
                        "08:30", "08:30"));
                put("8:15 PM", new SageV2Availability(
                        "20:15", "20:15"));
                put("7:00 am", new SageV2Availability(
                        "07:00", "07:00"));
                put("7:45 pm", new SageV2Availability(
                        "19:45", "19:45"));
                put("6:00 a.m.", new SageV2Availability(
                        "06:00", "06:00"));
                put("6:00 p.m.", new SageV2Availability(
                        "18:00", "18:00"));
                put("09:00", new SageV2Availability(
                        "09:00", "09:00"));
                put("18:30", new SageV2Availability(
                        "18:30", "18:30"));
            }};

    @Before
    public void before() throws IOException {

    }

    @After
    public void after() throws IOException {

    }

    @Test
    public void test_allSchedules() throws IOException {

        // Some HM scheduling had bugs where a session or a day of session would be missing
        // Let's accept a single day being missing in a burst, but no more than that
        int minAcceptableSessionsInBurst = 24;
        // As stated above, some bursts will be missing 1-4 sessions, so let's make sure
        // that at least 8 of the 10 bursts have all of their sessions
        int minAcceptableGoalsOverAllBursts = 8;
        // Minimal acceptable distance of weeks between bursts,
        // This is testing the week number variable, not really the actual burst dates,
        // As those can vary based on re-scheduling
        // This makes sure that a schedule hasn't been changed to be too close to the adjacent one
        int weekInSeconds = 7 * 24 * 60 * 60;
        int minWeeksBetweenBursts = 13;
        int minSecondsBetweenBursts = minWeeksBetweenBursts * weekInSeconds;
        // Make sure that MOST study bursts have this much time between them
        int expectedWeeksBetweenBursts = 24;
        int expectedSecondsBetweenBursts = minWeeksBetweenBursts * weekInSeconds;

        int sessionsInABurst = SageScheduleController.Companion.getSessionsInABurst();

        int highestFileInAllSession = 2420;
        for(int fileId = 1292; fileId <= highestFileInAllSession; fileId++) {

            System.out.println(fileId + "");

            if (withdrawnUsers.contains(fileId) ||
                    skipNeedsManualIntervention.contains(fileId)) {
                continue;
            }

            String scheduleJson = getScheduleJson(fileId + "");
            if (scheduleJson == null) {
                continue;
            }

            ObjectMapper mapper = new ObjectMapper();
            TestSchedule schedule = mapper.readValue(scheduleJson, TestSchedule.class);
            SageV1Schedule v1Schedule = controller.createV1Schedule(schedule);

            assertNotNull(v1Schedule);
            assertEquals(10, v1Schedule.getStudyBursts().size());

            int expectedBurstSeperation = 0;
            int expectedSessionsCount = 0;
            // Some of the study bursts
            int studyBurstSessionCount = v1Schedule.getStudyBursts().get(0).getSessions().size();
            // Baseline burst has one extra session (baseline)
            if (studyBurstSessionCount == (sessionsInABurst + 1)) {
                expectedSessionsCount += 1;
            }
            assertTrue(studyBurstSessionCount >= minAcceptableSessionsInBurst);

            for (int i = 1; i < 10; i++) {
                SageV1StudyBurst prevStudyBurst =
                        v1Schedule.getStudyBursts().get(i - 1);
                SageV1StudyBurst curStudyBurst =
                        v1Schedule.getStudyBursts().get(i);

                studyBurstSessionCount = curStudyBurst.getSessions().size();
                if (curStudyBurst.getSessions().size() == sessionsInABurst) {
                    expectedSessionsCount += 1;
                }

                if (studyBurstSessionCount < minAcceptableSessionsInBurst) {
                    int k = 0;
                }
                // Make sure the count is at least more than 24, which is when the user is missing 1 day
                assertTrue(studyBurstSessionCount >= minAcceptableSessionsInBurst);

                // Make sure that the previous study burst is before the next one
                assertTrue(prevStudyBurst.getStartDate() < curStudyBurst.getStartDate());

                // Make sure that they are at least 24 weeks apart
                // Even when using the re-scheduling tool, the week number will remain the same
                // This is testing the week number variable, not really the actual burst dates
                // As those can vary based on re-scheduling
                int weeksBetweenBursts = curStudyBurst.getStartingWeekNum() -
                        prevStudyBurst.getStartingWeekNum();
                // Skip any major reschedules as they will not have an expected format
                if (!skipMajorReschedules.contains(fileId)) {
                    assertTrue(weeksBetweenBursts >= minWeeksBetweenBursts);
                }

                // These tests the actual scheduled study burst start times are far enough away
                double secondsBetweenBursts = curStudyBurst.getStartDate() -
                        prevStudyBurst.getStartDate();
                // Skip any major reschedules as they will not have an expected format
                if (!skipMajorReschedules.contains(fileId)) {
                    assertTrue(secondsBetweenBursts >= minSecondsBetweenBursts);
                }

                // Even in major re-scheduling, there should be a check for MOST study bursts
                // being the correct spacing apart, both in week number and absolite time
                if (weeksBetweenBursts > expectedWeeksBetweenBursts &&
                    secondsBetweenBursts > expectedSecondsBetweenBursts) {
                    expectedBurstSeperation += 1;
                }
            }

            // At least 8 of the 10 expected counts means only 1 study burst is effected
            assertTrue(expectedSessionsCount >= 7);
            // At least 8 out of 10 study bursts have not been re-scheduled or have errors
            assertTrue(expectedBurstSeperation >= 7);
        }
    }

    @Test
    public void test_createV1ScheduleMalformed() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String scheduleJsonStr = PathsHelper.readFile(scheduleMalformedJson);
        TestSchedule schedule = mapper.readValue(scheduleJsonStr, TestSchedule.class);
        SageV1Schedule v1Schedule = controller.createV1Schedule(schedule);

        // This is the exception to the normal schedule validation, this checks that
        // day 2 of study burst 3 has 3 sessions instead of the expected 4
        int malformedStudyBurstNumber = 3;

        assertNotNull(v1Schedule);
        assertEquals("America/Chicago (fixed (equal to current))", v1Schedule.getV1Timezone());
        assertEquals(10, v1Schedule.getStudyBursts().size());

        assertEquals(29, v1Schedule.getStudyBursts().get(0).getSessions().size());
        for (int i = 1; i < 10; i++) {
            if (i == malformedStudyBurstNumber) {
                // Malformed by missing one session in the third study burst
                assertEquals(27, v1Schedule.getStudyBursts().get(i).getSessions().size());
            } else {
                assertEquals(28, v1Schedule.getStudyBursts().get(i).getSessions().size());
            }
        }
    }

    @Test
    public void test_createV1Schedule() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String scheduleJsonStr = PathsHelper.readFile(scheduleJson);
        TestSchedule schedule = mapper.readValue(scheduleJsonStr, TestSchedule.class);
        SageV1Schedule v1Schedule = controller.createV1Schedule(schedule);

        assertNotNull(v1Schedule);
        assertEquals("Pacific Standard Time", v1Schedule.getV1Timezone());
        assertEquals(10, v1Schedule.getStudyBursts().size());

        assertEquals(29, v1Schedule.getStudyBursts().get(0).getSessions().size());
        for (int i = 1; i < 10; i++) {
            assertEquals(28, v1Schedule.getStudyBursts().get(i).getSessions().size());
        }
    }

    @Test
    public void test_createV2AvailabilityJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String availabilityJsonStr = PathsHelper.readFile(availabilityJson);
        WakeSleepSchedule availability = mapper.readValue(availabilityJsonStr, WakeSleepSchedule.class);
        SageV2Availability v2Availability =
                controller.createV2Availability(availability);

        assertNotNull(v2Availability);
        SageV2Availability expected =
               new SageV2Availability("08:00", "20:00");
        assertEquals(expected, v2Availability);
    }

    @Test
    public void test_createV2AvailabilityTests() {
        for(String time: timeTestMap.keySet()) {
            SageV2Availability expected = timeTestMap.get(time);

            WakeSleepSchedule schedule = new WakeSleepSchedule();
            schedule.wakeSleepData = new ArrayList<>();
            schedule.wakeSleepData.add(new WakeSleepSchedule.WakeSleepData());
            schedule.wakeSleepData.get(0).bed = time;
            schedule.wakeSleepData.get(0).wake = time;

            SageV2Availability actual =
                controller.createV2Availability(schedule);
            assertNotNull(actual);
            assertEquals(expected, actual);
        }
    }

    @Test
    public void test_availabilityMinutes() {
        SageV2Availability availability =
                new SageV2Availability("16:00", "00:00");
        assertEquals(480, availability.availabilityInMinutes());

        availability = new SageV2Availability("22:00", "06:00");
        assertEquals(480, availability.availabilityInMinutes());

        availability = new SageV2Availability("00:00", "08:00");
        assertEquals(480, availability.availabilityInMinutes());
    }

    @Test
    public void test_availabilityRandomSessions() {
        DateTimeFormatter formatter =
                SageV2Availability.Companion.getFormatter();
        SageV2Availability availability =
                new SageV2Availability("16:00", "00:00");
        assertEquals(480, availability.availabilityInMinutes());
        List<LocalTime> randomSessionTimes = availability.randomSessionTimes();
        assertEquals(4, randomSessionTimes.size());
        assertEquals("16:00", formatter.print(randomSessionTimes.get(0)));
        assertEquals("18:00", formatter.print(randomSessionTimes.get(1)));
        assertEquals("20:00", formatter.print(randomSessionTimes.get(2)));
        assertEquals("22:00", formatter.print(randomSessionTimes.get(3)));

        availability = new SageV2Availability("22:00", "06:00");
        assertEquals(480, availability.availabilityInMinutes());
        randomSessionTimes = availability.randomSessionTimes();
        assertEquals(4, randomSessionTimes.size());
        assertEquals("00:00", formatter.print(randomSessionTimes.get(0)));
        assertEquals("02:00", formatter.print(randomSessionTimes.get(1)));
        assertEquals("04:00", formatter.print(randomSessionTimes.get(2)));
        assertEquals("22:00", formatter.print(randomSessionTimes.get(3)));

        availability = new SageV2Availability("00:00", "08:00");
        assertEquals(480, availability.availabilityInMinutes());
        randomSessionTimes = availability.randomSessionTimes();
        assertEquals(4, randomSessionTimes.size());
        assertEquals("00:00", formatter.print(randomSessionTimes.get(0)));
        assertEquals("02:00", formatter.print(randomSessionTimes.get(1)));
        assertEquals("04:00", formatter.print(randomSessionTimes.get(2)));
        assertEquals("06:00", formatter.print(randomSessionTimes.get(3)));
    }

    @Test
    public void test_allAvailability() throws IOException {
        int highestFileInAllSession = 2420;
        for(int fileId = 0; fileId <= highestFileInAllSession; fileId++) {

            if (withdrawnUsers.contains(fileId)) {
                continue;
            }

            String availabilityJson = getAvailabilityJson(fileId + "");
            if (availabilityJson == null) {
                continue;
            }

            ObjectMapper mapper = new ObjectMapper();
            WakeSleepSchedule wakeSleepSchedule = mapper.readValue(availabilityJson, WakeSleepSchedule.class);
            SageV2Availability availability = controller
                    .createV2Availability(wakeSleepSchedule);

            assertNotNull(availability);
            assertTrue(availability.availabilityInMinutes() >= 480);

            // Run the simulation 1000 times for each availability, and check that
            // all the sessions are at least 2 hours a part from each other
            List<LocalTime> randomSessions;
            int twoHoursInMinutes = 120;
            for (int i = 0; i < 1000; i++) {
                randomSessions = availability.randomSessionTimes();
                for (int j = 0; j < randomSessions.size() - 1; j++) {
                    int minutesBetweenSessions = SageV2Availability.Companion
                            .minutesBetween(randomSessions.get(j), randomSessions.get(j+1));
                    // Make sure each test is two hours apart
                    assertTrue(minutesBetweenSessions >= twoHoursInMinutes);
                    // Make sure they are sorted in ascending order
                    assertTrue(randomSessions.get(j).isBefore(randomSessions.get(j+1)));
                }
            }
        }
    }

    @Test
    public void testTimeZoneConversion_allSessions() throws IOException  {
        int highestFileInAllSession = 2420;
        int nullTzCount = 0;
        int successTzCount = 0;
        for(int fileId = 0; fileId <= highestFileInAllSession; fileId++) {
            if (withdrawnUsers.contains(fileId) ||
                    skipNeedsManualIntervention.contains(fileId)) {
                continue;
            }

            String scheduleJson = getScheduleJson(fileId + "");
            if (scheduleJson == null) {
                continue;
            }

            ObjectMapper mapper = new ObjectMapper();
            TestSchedule schedule = mapper.readValue(scheduleJson, TestSchedule.class);
            SageV1Schedule v1Schedule = controller.createV1Schedule(schedule);

            assertNotNull(v1Schedule);
            if (v1Schedule.getV1Timezone() == null) {
                // Some schedules have null timezones, there is nothing we can do about this
                nullTzCount += 1;
                continue;
            }

            // For timezones that are not-null, let's make sure we translate them correctly
            String iANATimezone = controller
                    .convertToIANATimezone(v1Schedule.getV1Timezone(), null);
            assertNotNull(iANATimezone);
            // If they are not equal, it means there was a conversion to the correct format
            assertNotEquals(v1Schedule.getV1Timezone(), iANATimezone);
            successTzCount += 1;
        }

        // Null time zone count will need dealt with by specifying a default TZ,
        // This ideally should be set based on Study ID, and is not a major issue
        // If it is off by a few hours,
        System.out.println("Null Tz Count " + nullTzCount);
        System.out.println("Successful Tz Count " + successTzCount);
    }

    @Test
    public void testTimeZoneConversion()  {
        for (String rawTimezone : arcTimeZoneAnswerMap.keySet()) {
            String expectedTimezone = arcTimeZoneAnswerMap.get(rawTimezone);
            assertEquals("Raw timezone " + rawTimezone + " failed to become " + expectedTimezone,
                    controller.convertToIANATimezone(rawTimezone, null), expectedTimezone);
        }
    }
}