package org.sagebionetworks.dian.datamigration.rescheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ReschedulingTool {

    public static void rescheduleUser(String args[]) throws IOException {

        BridgeJavaSdkUtil.initialize(args[0], args[1], args[2]);

        // Using Scanner for Getting Input from User
        Scanner in = new Scanner(System.in);

        System.out.println("What is the Arc ID of the user you want to reschedule?");
        String arcId = in.nextLine();

        System.out.println("Checking participant...");
        StudyParticipant participant = BridgeJavaSdkUtil.getParticipantByExternalId(arcId);
        System.out.println("Reading participant schedule...");
        String scheduleJson = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                participant.getId(), BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID);
        ObjectMapper mapper = new ObjectMapper();
        TestSchedule testSchedule = mapper.readValue(scheduleJson, TestSchedule.class);

        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionsByCycle = new HashMap<>();
        for (TestSchedule.TestScheduleSession session : testSchedule.sessions) {
            int testCycle = (int)(Math.round((double)session.week / 26.0) + 1);
            List<TestSchedule.TestScheduleSession> weeksTests = sessionsByCycle.get(testCycle);
            if (weeksTests == null) {
                weeksTests = new ArrayList<>();
            }
            weeksTests.add(session);
            sessionsByCycle.put(testCycle, weeksTests);
        }

        ZoneOffset userTimeZone = null;
        if (testSchedule.timezone_offset.startsWith("UTC")) {
            String zoneId = testSchedule.timezone_offset.replace("UTC", "");
            userTimeZone = ZoneOffset.of(zoneId);
        } else {
            userTimeZone = ZoneOffset.ofHours(Integer.parseInt(testSchedule.timezone_offset));
        }

        for (Integer key : sessionsByCycle.keySet()) {
            List<TestSchedule.TestScheduleSession> weeksTests = sessionsByCycle.get(key);
            TestSchedule.TestScheduleSession day0Session0 = findFirstTest(sessionsByCycle.get(key));
            String sessionDate = sessionDateString(day0Session0, userTimeZone);
            System.out.println("Test Cycle " + key + ": " + sessionDate);
        }

        System.out.println("Which test cycle would you like to change?");
        int testCycleToChange = Integer.parseInt(in.nextLine());

        TestSchedule.TestScheduleSession day0Session0 = findFirstTest(
                sessionsByCycle.get(testCycleToChange));

        Instant day0Instant = Instant.ofEpochSecond(day0Session0.session_date.longValue());
        OffsetDateTime day0DateTime = day0Instant.atOffset(userTimeZone);

        System.out.println("Test cycle: " + testCycleToChange + " " +
                sessionDateString(day0Session0, userTimeZone));

        System.out.println("At what date would you like to re-schedule to? YYYY-MM-DD");
        String newStartDayStr = in.nextLine();
        LocalDate newStartDay = LocalDate.parse(newStartDayStr);
        LocalDateTime newStartDateTime = LocalDateTime.of(newStartDay, day0DateTime.toLocalTime());
        OffsetDateTime newDateTime = newStartDateTime.atOffset(userTimeZone);

        System.out.println("Re-scheduling...");
        long timeDiff = newDateTime.toEpochSecond() - day0DateTime.toEpochSecond();
        for (TestSchedule.TestScheduleSession session : sessionsByCycle.get(testCycleToChange)) {
            String previousDate = sessionDateString(session, userTimeZone);
            session.session_date = session.session_date + timeDiff;
            String newDate = sessionDateString(session, userTimeZone);
            System.out.println(
                    "Day " + session.day +
                    ", Session " + session.session + "\n" +
                    "   at: " + previousDate + "\n");
        }

        System.out.println("Does this look correct? (y/n)");
        String yesNo = in.nextLine();
        if (!yesNo.toLowerCase().equals("y")) {
            System.out.println("You answered no. The program will exit, please try again.");
        }

        System.out.println("Great! Writing changes to Bridge...");

        String json = mapper.writeValueAsString(testSchedule);
        BridgeJavaSdkUtil.writeUserReport(participant.getId(),
                BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID, json);

        in.close();

        System.out.println("Re-scheduling was successful!");
    }

    private static TestSchedule.TestScheduleSession findFirstTest(
            List<TestSchedule.TestScheduleSession> sessionList) {
        TestSchedule.TestScheduleSession day0Session0 = null;
        for (TestSchedule.TestScheduleSession session : sessionList) {
            if (session.day == 0 && session.session == 0) {
                day0Session0 = session;
            }
        }
        // If we missed it, try and get day 1's first session
        if (day0Session0 == null) {
            for (TestSchedule.TestScheduleSession session : sessionList) {
                if (session.day == 1 && session.session == 0) {
                    day0Session0 = session;
                }
            }
        }
        return day0Session0;
    }

    private static String sessionDateString(
            TestSchedule.TestScheduleSession session, ZoneOffset userTimeZone) {
        Instant instant = Instant.ofEpochSecond(session.session_date.longValue());
        OffsetDateTime dateTime = instant.atOffset(userTimeZone);
        return DateTimeFormatter.ISO_DATE_TIME.format(dateTime);
    }
}
