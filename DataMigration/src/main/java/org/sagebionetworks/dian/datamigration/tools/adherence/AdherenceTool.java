package org.sagebionetworks.dian.datamigration.tools.adherence;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.HmDataModel;
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningDetails;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.ReschedulingTool;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule;
import org.sagebionetworks.research.sagearc.AdherenceSageEarningsController;
import org.sagebionetworks.research.sagearc.SageEarningsController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Requirements:
 * Need to be able to run commands that allow a researcher to check a participants adherence.
 * This simply prints out a JSON string of the participant's completed tests.
 */
public class AdherenceTool {

    public static void main(String[] args) throws IOException {
        BridgeJavaSdkUtil.initialize(args[0], args[1], args[2]);
        Scanner in = new Scanner(System.in);
        AdherenceTool.checkUser(in);
        in.close();
    }

    public static void checkUser(Scanner in) throws IOException {
        System.out.println("What is the Arc ID of the user you want to check?");
        String arcId = in.nextLine();

        System.out.println("Checking participant...");
        StudyParticipant participant = BridgeJavaSdkUtil.getParticipantByExternalId(arcId);

        System.out.println("Reading participant\'s completed tests...");
        String completedTestJson = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                participant.getId(), BridgeJavaSdkUtil.COMPLETED_TESTS_REPORT_ID);
        // Convert Test Schedule JSON String to the expected HM data model
        ObjectMapper mapper = new ObjectMapper();
        HmDataModel.CompletedTestList completedTests = mapper.readValue(
                completedTestJson, HmDataModel.CompletedTestList.class);

        String scheduleJson = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                participant.getId(), BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID);
        // Convert Test Schedule JSON String to the expected HM data model
        TestSchedule testSchedule = mapper.readValue(
                scheduleJson, TestSchedule.class);

        Map<Integer, List<TestSchedule.TestScheduleSession>> sessionsByCycle =
                ReschedulingTool.convertToMap(testSchedule.sessions);

        // Find the earliest session date, which will be the study start date
        TestSchedule.TestScheduleSession baselineSession =
                ReschedulingTool.findFirstTest(sessionsByCycle.get(1));
        DateTime studyStartDate = new DateTime(
                baselineSession.session_date.longValue() * 1000L, DateTimeZone.UTC);

        // Calculate the earnings
        AdherenceSageEarningsController earningsController = new AdherenceSageEarningsController();
        earningsController.setStudyStartDate(studyStartDate);
        earningsController.setCompletedTests(completedTests.completed);
        EarningDetails earnings = earningsController.getCurrentEarningsDetails();
        completedTests.completed = earningsController
                .filterAndConvertTests(completedTests.completed);

        // Calculate test completion percentage per cycle
        Map<Integer, Integer> completionMap = new HashMap<>();
        for (HmDataModel.CompletedTest test : completedTests.completed) {
            int count = 0;
            if (completionMap.get(test.week) != null) {
                count = completionMap.get(test.week);
            }
            count += 1;
            completionMap.put(test.week, count);
        }
        List<Integer> completionKeyArray = new ArrayList<>();
        for (Integer key : completionMap.keySet()) {
            completionKeyArray.add(key);
        }

        System.out.println("\nCycle   Start Date      End Date        Adherence   Earned");

        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("MM/dd/yyyy");
        for (Integer cycle: sessionsByCycle.keySet()) {
            AdherenceTableRow adherenceTableRow = new AdherenceTableRow();
            List<TestSchedule.TestScheduleSession> sessions = sessionsByCycle.get(cycle);

            int applicableSessionsCount = 0;
            for (TestSchedule.TestScheduleSession session : sessions) {
                if (session.session_date < System.currentTimeMillis()) {
                    // Ignore baseline session
                    if (!baselineSession.session_date.equals(session.session_date)) {
                        applicableSessionsCount += 1;
                    }
                }
            }
            int percentage = 0;
            if (applicableSessionsCount > 0 &&
                    (cycle-1) < completionKeyArray.size()) {
                int completedCount = completionMap.get(completionKeyArray.get(cycle-1));
                percentage = (int)(100 *
                        ((float)completedCount / (float)applicableSessionsCount));
            }
            adherenceTableRow.adherence = String.format("%02d%%", percentage);

            TestSchedule.TestScheduleSession first = ReschedulingTool.findFirstTest(sessions);
            adherenceTableRow.startDate = dtfOut.print(
                    new DateTime(first.session_date.longValue() * 1000L, DateTimeZone.UTC));
            TestSchedule.TestScheduleSession last = findLastTest(sessions);
            adherenceTableRow.endDate = dtfOut.print(
                    new DateTime(last.session_date.longValue() * 1000L, DateTimeZone.UTC));
            String total = "$0.00";
            if ((cycle-1) < earnings.cycles.size()) {
                total = earnings.cycles.get(cycle-1).total;
            }
            adherenceTableRow.earned = total;

            System.out.format("%2d      %s      %s      %s         %s",
                    cycle, adherenceTableRow.startDate, adherenceTableRow.endDate,
                    adherenceTableRow.adherence, adherenceTableRow.earned);
            System.out.println();
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("\nWould you also like to see the RAW JSON?");
        if (ReschedulingTool.shouldContinueYN(scanner)) {
            System.out.println("Raw JSON:");
            String prettyPrintedJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(completedTests);
            System.out.println("\n" + prettyPrintedJson);
        }
    }

    public static TestSchedule.TestScheduleSession findLastTest(
            List<TestSchedule.TestScheduleSession> sessionList) {
        TestSchedule.TestScheduleSession lastSession = null;
        for (TestSchedule.TestScheduleSession session : sessionList) {
            if (lastSession == null ||
                    (lastSession.session_date < session.session_date)) {
                lastSession = session;
            }
        }
        return lastSession;
    }

    public static class AdherenceTableRow {
        public String startDate = "";
        public String endDate = "";
        public String adherence = "";
        public String earned = "";
    }
}
