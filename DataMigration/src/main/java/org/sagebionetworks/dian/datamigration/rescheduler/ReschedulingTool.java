package org.sagebionetworks.dian.datamigration.rescheduler;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.client.exceptions.SynapseException;
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

/**
 * See DIAN-186
 *
 * Requirements:
 * Need to be able to run commands that allow schedule to be moved.
 * Need to be able to run this script after the session window was missed to keep data integrity
 * Need to be able to set start time of next session as well as date.
 * Need to be able to shift start day either by entering a specific time/date
 * combo or by entering a future date that follows their availability window.
 *
 * Happy Path: P missed their 4th burst cycle because they deleted the app.
 * and has requested to push the start date to 4 days in the future.
 * P has appointment at 8am 4 days into the future to restart the burst.
 * We set the start and the P can complete all 4 sessions on that day.
 *
 * Edge Case: P missed their 4th burst cycle because they deleted the app.
 * and has requested to push the start date to 4 days in the future.
 * P has appointment at 4pm 4 days into the future to restart the burst.
 * We should set the start day to be at the users available start time
 * (that they set for 8am ) for 5 days into the future.
 * The coordinator can then go through the app with the P
 * and would start at 8am 5 days into the future.
 *
 * Out of scope: Doing any of this in BSM
 *
 * Moving dates for all bursts of a user.
 * This will only apply to the missed cycle and the schedule will not shift for remaining cycles.
 */
public class ReschedulingTool {

    private static final int SECONDS_IN_AN_HOUR = 60 * 60;

    public static void main(String[] args) throws IOException {
        BridgeJavaSdkUtil.initialize(args[0], args[1], args[2]);
        Scanner in = new Scanner(System.in);
        ReschedulingTool.rescheduleUser(in);
    }

    public static void rescheduleUser(Scanner in) throws IOException {
        System.out.println("What is the Arc ID of the user you want to reschedule?");
        String arcId = in.nextLine();

        System.out.println("Checking participant...");
        StudyParticipant participant = BridgeJavaSdkUtil.getParticipantByExternalId(arcId);

        System.out.println("Reading participant schedule...");
        String scheduleJson = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                participant.getId(), BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID);

        // Convert Test Schedule JSON String to the expected HM data model
        ObjectMapper mapper = new ObjectMapper();
        TestSchedule testSchedule = mapper.readValue(scheduleJson, TestSchedule.class);

        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionsByCycle =
                convertToMap(testSchedule.sessions);

        ZoneOffset userTimeZone = parseZone(testSchedule.timezone_offset);

        for (Integer key : sessionsByCycle.keySet()) {
            TestSchedule.TestScheduleSession day0Session0 = findFirstTest(sessionsByCycle.get(key));
            String sessionDate = sessionDateString(day0Session0, userTimeZone);
            System.out.println("Test Cycle " + key + ": " + sessionDate);
        }

        System.out.println("Which test cycle would you like to change?");
        int testCycleToChange = Integer.parseInt(in.nextLine());

        TestSchedule.TestScheduleSession day0Session0 = findFirstTest(
                sessionsByCycle.get(testCycleToChange));

        System.out.println("Test cycle: " + testCycleToChange + " " +
                sessionDateString(day0Session0, userTimeZone));

        System.out.println("At what date would you like to re-schedule to? YYYY-MM-DD");
        long timeDiff = timeDifference(in.nextLine(), day0Session0, userTimeZone);

        System.out.println("Re-scheduling...");
        for (TestSchedule.TestScheduleSession session : sessionsByCycle.get(testCycleToChange)) {
            String previousDate = sessionDateString(session, userTimeZone);
            session.session_date += timeDiff;
            String newDate = sessionDateString(session, userTimeZone);
            System.out.println(
                    "Day " + session.day +
                    ", Session " + session.session + "\n" +
                    "   from: " + previousDate + "\n" +
                    "     to: " + newDate + "\n");
        }

        System.out.println("Does this look correct? (y/n)");
        if (!shouldContinueYN(in)) {
            System.out.println("You answered no. The program will exit, please try again.");
            System.exit(0);
        }

        System.out.println("Great! Would you like to make any changes to a specific session? (y/n)");
        while(shouldContinueYN(in)) {

            System.out.println("Which one? Enter 0,1 for day 0, session 0, etc.");
            String[] daySession = in.nextLine().split(",");
            int dayTarget = Integer.parseInt(daySession[0]);
            int sessionTarget = Integer.parseInt(daySession[1]);

            TestSchedule.TestScheduleSession sessionToChange = findSession(
                    sessionsByCycle.get(testCycleToChange), dayTarget, sessionTarget);

            if (sessionToChange != null) {
                String date = sessionDateString(sessionToChange, userTimeZone);
                System.out.println(
                        "Change will effect Day " + sessionToChange.day +
                                ", Session " + sessionToChange.session + "\n" +
                                "   at: " + date);

                System.out.println("How many hours would you like to change this by? -5 " +
                        "to move 5 hours into the past, 5 to move 5 hours into the future, etc.");

                // Move specific session by X hours
                int moveByHours = Integer.parseInt(in.nextLine()) * SECONDS_IN_AN_HOUR;
                sessionToChange.session_date += moveByHours;
                String newSessionDate = sessionDateString(sessionToChange, userTimeZone);

                System.out.println("This session will now be at\n" + newSessionDate);
                System.out.println("Does this look correct? (y/n)");
                if (!shouldContinueYN(in)) {
                    System.out.println("Ok, we reverted the changes.");
                    // Move the session back to its original time
                    sessionToChange.session_date -= moveByHours;
                }
            } else {
                System.out.println("Sorry, we could not find that session.");
            }
            System.out.println("Would you like to make any more changes to a specific session? (y/n)");
        }

        System.out.println("Great! Writing changes to Bridge...");
        in.close();

        // Write the final schedule changes to bridge
        writeScheduleToBridge(participant.getId(), testSchedule);

        System.out.println("Re-scheduling was successful!");
    }

    /**
     * @param newStartDayStr must be in the format "YYYY-MM-DD"
     * @param day0Session0 the first session in the test cycle
     * @param userTimeZone the time zone that provides the context for these sessions
     * @return the difference between the specified local date using the day0Session0's session local time,
     *         and the provided day0Session0 time when they are both in the participant's time zone
     */
    public static long timeDifference(String newStartDayStr,
                                       TestSchedule.TestScheduleSession day0Session0,
                                       ZoneOffset userTimeZone) {
        OffsetDateTime day0DateTime = Instant.ofEpochSecond(
                day0Session0.session_date.longValue()).atOffset(userTimeZone);
        LocalDate newStartDay = LocalDate.parse(newStartDayStr);
        LocalDateTime newStartDateTime = LocalDateTime.of(newStartDay, day0DateTime.toLocalTime());
        OffsetDateTime newDateTime = newStartDateTime.atOffset(userTimeZone);
        return newDateTime.toEpochSecond() - day0DateTime.toEpochSecond();
    }

    /**
     * @param offset that is parsable by the ZoneOffset class
     *               Acceptable formats can be seen in ZoneOffset class or
     *               any of those formats proceeded by "UTC"
     * @return ZoneOffset for that particular participant
     */
    public static ZoneOffset parseZone(String offset) {
        if (offset.startsWith("UTC") || offset.contains(":")) {
            String zoneId = offset.replace("UTC", "");
            return ZoneOffset.of(zoneId);
        } else {
            return ZoneOffset.ofHours(Integer.parseInt(offset));
        }
    }

    /**
     * @param allSessions in the user's test schedule
     * @return the test sessions mapped to test cycle
     */
    public static Map<Integer, List<TestSchedule.TestScheduleSession>> convertToMap(
            List<TestSchedule.TestScheduleSession> allSessions) {
        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionsByCycle = new HashMap<>();
        for (TestSchedule.TestScheduleSession session : allSessions) {
            int testCycle = (int)(Math.round((double)session.week / 26.0) + 1);
            List<TestSchedule.TestScheduleSession> weeksTests = sessionsByCycle.get(testCycle);
            if (weeksTests == null) {
                weeksTests = new ArrayList<>();
            }
            weeksTests.add(session);
            sessionsByCycle.put(testCycle, weeksTests);
        }
        return sessionsByCycle;
    }

    /**
     * @param userId of user on Bridge
     * @param testSchedule to write as the participant's test schedule report
     * @throws IOException if something goes wrong writing to Bridge
     */
    public static void writeScheduleToBridge(String userId, TestSchedule testSchedule) throws IOException {
        String json = new ObjectMapper().writeValueAsString(testSchedule);
        BridgeJavaSdkUtil.writeUserReport(userId,
                BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID, json);
    }

    /**
     * @param sessionList to find the particular session in
     * @return the first test session in the cycle, or null if it could not be found
     */
    public static TestSchedule.TestScheduleSession findFirstTest(
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

    /**
     * @param sessionList to find the particular session in
     * @param day number of the session in the test cycle (1-7)
     * @param sessionNum the session num within the day (0-3)
     * @return the session with these parameters, null if it can't be found
     */
    public static TestSchedule.TestScheduleSession findSession(
            List<TestSchedule.TestScheduleSession> sessionList, int day, int sessionNum) {
        for (TestSchedule.TestScheduleSession session : sessionList) {
            if (session.day == day && session.session == sessionNum) {
                return session;
            }
        }
        return null;
    }

    /**
     * @param session to print the session_date of
     * @param userTimeZone the time zone for the session date to be printed in
     * @return the session_date string printed in the participant's time zone
     */
    public static String sessionDateString(
            TestSchedule.TestScheduleSession session, ZoneOffset userTimeZone) {
        Instant instant = Instant.ofEpochSecond(session.session_date.longValue());
        OffsetDateTime dateTime = instant.atOffset(userTimeZone);
        return DateTimeFormatter.ISO_DATE_TIME.format(dateTime);
    }

    /**
     * @param scanner to get input from the user
     * @return true if user answers "y", false if the user answers "n"
     */
    public static boolean shouldContinueYN(Scanner scanner) {
        String yesNo = scanner.nextLine();
        while (!yesNo.toLowerCase().equals("y") &&
            !yesNo.toLowerCase().equals("n")) {
            System.out.println("Please enter a valid response.  y or n.");
            yesNo = scanner.nextLine();
        }
        return yesNo.toLowerCase().equals("y");
    }
}
