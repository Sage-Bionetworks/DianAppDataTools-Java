package org.sagebionetworks.dian.datamigration.adherence;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;

import java.io.IOException;
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
        AdherenceCompletedTests completedTests = mapper.readValue(
                completedTestJson, AdherenceCompletedTests.class);

        String prettyPrintedJson = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(completedTests);
        System.out.println("\n" + prettyPrintedJson);
    }
}
