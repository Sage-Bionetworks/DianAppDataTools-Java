package org.sagebionetworks.dian.datamigration.tools.schedulev2;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.rest.model.AdherenceRecordList;
import org.sagebionetworks.bridge.rest.model.Study;
import org.sagebionetworks.bridge.rest.model.StudyActivityEvent;
import org.sagebionetworks.bridge.rest.model.StudyActivityEventList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageScheduleController;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageUserClientData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import kotlinx.datetime.LocalDateTime;

public class EarningsBugFix {

    public static Gson gson = new Gson();

    public static void main(String[] args) throws IOException, Throwable {

        BridgeJavaSdkUtil.initialize();
        runEarningsBugFix();

        // Switch to INV-ARC
        BridgeJavaSdkUtil.BRIDGE_ID = BridgeJavaSdkUtil.BRIDGE_ID2;

        BridgeJavaSdkUtil.initialize();
        runEarningsBugFix();
    }

    public static void runEarningsBugFix() throws IOException {

        NewEarningsController earningsController = new NewEarningsController();

        List<Study> studyList = BridgeJavaSdkUtil.getAllStudies();
        for (Study study : studyList) {
            String studyId = study.getIdentifier();
            HashSet<String> arcIdList = BridgeJavaSdkUtil.getArcIdsInStudy(studyId);

            for (String arcId : arcIdList) {
                StudyParticipant p = BridgeJavaSdkUtil.getParticipantByExternalId(arcId);
                String uId = p.getId();

                StudyBurstSession.StudyBurstSchedule schedule = createSchedule(p);
                if (schedule != null) {
                    String sId = p.getStudyIds().get(0);
                    AdherenceRecordList adherence =
                            BridgeJavaSdkUtil.getUserAdherenceRecords(uId, sId);
                    if (adherence.getItems().size() > 0) {
                        earningsController.initializeWithStudyBursts(
                                schedule, adherence.getItems(), false);

                        List<String> earnings = earningsController.recalculateEarnings();
                        SageUserClientData clientData = SageUserClientData.Companion
                                .fromStudyParticipant(gson, p);

                        if (!isEarningsDifferent(earnings, clientData)) {
                            System.out.println("\n" + arcId);
                            System.out.println("From:");
                            System.out.println(clientData.getEarnings());
                            System.out.println("To:");
                            System.out.println(earnings);

                            clientData.setEarnings(earnings);
                            JsonElement newClientDataJson = gson.toJsonTree(clientData);
                            BridgeJavaSdkUtil.updateParticipantClientData(p, newClientDataJson);
                        }
                    }
                }
            }
        }
    }

    public static StudyBurstSession.StudyBurstSchedule createSchedule(StudyParticipant p) throws IOException {
        String uId = p.getId();

        if (p.getStudyIds() == null || p.getStudyIds().size() <= 0) {
            return null;
        }

        String sId = p.getStudyIds().get(0);

        StudyActivityEventList activityEventList =
                BridgeJavaSdkUtil.getAllTimelineEvents(uId, sId);

        List<DateTime> studyBurstDates = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String expectedEvent = String.format(SageScheduleController
                    .ACTIVITY_EVENT_STUDY_BURST_FORMAT, i);
            for (StudyActivityEvent event : activityEventList.getItems()) {
                if (event.getEventId().equals(expectedEvent)) {
                    studyBurstDates.add(event.getTimestamp());
                }
            }
        }

        if (studyBurstDates.size() < 10) {
            return null;
        }

        List<StudyBurstSession.StudyBurst> bursts = new ArrayList<>();
        int burstIdx = 0;
        // Build an BridgeJavaSdk outline of the user's schedule that matches how the app has it
        for (DateTime eventTime : studyBurstDates) {
            burstIdx++;
            String burstId = String.format(SageScheduleController.
                    ACTIVITY_EVENT_STUDY_BURST_FORMAT, burstIdx);
            LocalDateTime burstStart = new LocalDateTime(
                    eventTime.getYear(),
                    eventTime.getMonthOfYear(), eventTime.getDayOfMonth(),
                    eventTime.getHourOfDay(), eventTime.getMinuteOfHour(),
                    eventTime.getSecondOfMinute(), 0);
            DateTime endTime = eventTime.plusDays(7);
            LocalDateTime burstEnd = new LocalDateTime(endTime.getYear(),
                    endTime.getMonthOfYear(), endTime.getDayOfMonth(),
                    endTime.getHourOfDay(), endTime.getMinuteOfHour(),
                    endTime.getSecondOfMinute(), 0);

            List<List<StudyBurstSession>> sessions = new ArrayList<>();
            sessions.add(Collections.singletonList(
                    new StudyBurstSession(eventTime.toString() + "a",
                            burstId, eventTime.toString(), burstStart, burstStart)));
            sessions.add(Collections.singletonList(
                    new StudyBurstSession(eventTime.toString() + "b",
                            burstId, eventTime.toString(), burstEnd, burstEnd)));
            bursts.add(new StudyBurstSession.StudyBurst(sessions));
        }
        return new StudyBurstSession.StudyBurstSchedule(bursts);
    }

    public static boolean isEarningsDifferent(List<String> earnings, SageUserClientData clientData) {
        if (earnings.size() > 0 && clientData.getEarnings() == null) {
            return false;
        }

        int actualSize = clientData.getEarnings().size();
        if (earnings.size() != actualSize) {
            if (earnings.size() != (actualSize - 1)) {
                return false;
            } else {
                // The edge case of the last earnings being $0 is an acceptable use case
                // that does not need fixed, but check specifically for it not being that
                if ((!clientData.getEarnings().get(actualSize - 1).equals("$0.00")) &&
                        (!clientData.getEarnings().get(actualSize - 1).equals("$0,00"))) {
                    return false;
                }
            }
        }

        for (int e = 0; e < earnings.size(); e++) {
            // Some earnings are in euro notation, so switch commas over to periods for comparison
            if (!earnings.get(e).replace(",", ".").equals(
                    clientData.getEarnings().get(e).replace(",", "."))) {
                return false;
            }
        }

        return true;
    }
}
