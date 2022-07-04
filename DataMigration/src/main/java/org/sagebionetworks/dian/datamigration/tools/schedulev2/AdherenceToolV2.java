package org.sagebionetworks.dian.datamigration.tools.schedulev2;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.HmDataModel;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageEarningsControllerV2;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageScheduleController;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1Schedule;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1StudyBurst;
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningDetails;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.ReschedulingTool;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule;
import org.sagebionetworks.research.sagearc.SageEarningsController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Scanner;

import static org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningOverview.TEST_SESSION;

/**
 * Requirements:
 * Need to be able to run commands that allow a researcher to check a participants adherence.
 * This simply prints out a JSON string of the participant's completed tests.
 */
public class AdherenceToolV2 {

    public static SageScheduleController controller = new SageScheduleController();

    public static void main(String[] args) throws IOException {
        BridgeJavaSdkUtil.initialize(args[0], args[1], args[2]);
        Scanner in = new Scanner(System.in);
        AdherenceToolV2.checkUser(in);
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
        TestSchedule testSchedule = mapper.readValue(scheduleJson, TestSchedule.class);

        SageV1Schedule v1Schedule = controller.createV1Schedule(testSchedule);

        // Calculate the earnings
        SageEarningsControllerV2 earningsController = new SageEarningsControllerV2();
        earningsController.initializeWithStudyBursts(v1Schedule, completedTests.completed);
        EarningDetails earnings = earningsController.getCurrentEarningsDetails();

        System.out.println("\nCycle   Start Date      End Date        Adherence   Earned");

        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("MM/dd/yyyy");

        for (int cycle = 0; cycle < v1Schedule.getStudyBursts().size(); cycle++) {
            SageV1StudyBurst studyBurst = v1Schedule.getStudyBursts().get(cycle);

            AdherenceTableRow adherenceTableRow = new AdherenceTableRow();

            DateTime burstStart = new DateTime((long)(studyBurst.getStartDate() * 1000L), DateTimeZone.UTC);
            adherenceTableRow.startDate = dtfOut.print(burstStart);
            adherenceTableRow.endDate = dtfOut.print(burstStart.plusDays(7));
            String total = "$0.00";

            adherenceTableRow.adherence = "00%";
            // If available, the all sessions goal will give an accurate adherence percentage
            if ((cycle) < earnings.cycles.size()) {
                EarningDetails.Cycle studyBurstEarnings = earnings.cycles.get(cycle);
                total = studyBurstEarnings.total;
                for (EarningDetails.Goal goal : studyBurstEarnings.details) {
                    if (goal.name.equals(TEST_SESSION)) {
                        adherenceTableRow.adherence = String.format("%02d%%", goal.progress);
                    }
                }
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

    public static class AdherenceTableRow {
        public String startDate = "";
        public String endDate = "";
        public String adherence = "";
        public String earned = "";
    }
}
