package org.sagebionetworks.dian.datamigration.tools.schedulev2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.joda.time.DateTime;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;
import org.sagebionetworks.bridge.rest.model.AdherenceRecord;
import org.sagebionetworks.bridge.rest.model.AdherenceRecordList;
import org.sagebionetworks.bridge.rest.model.ScheduledSession;
import org.sagebionetworks.bridge.rest.model.StudyActivityEventList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.Timeline;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.HmDataModel;
import org.sagebionetworks.dian.datamigration.PathsHelper;
import org.sagebionetworks.dian.datamigration.tools.adherence.CompletedTestV2;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageEarningsControllerV2;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageScheduleController;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageUserClientData;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1Schedule;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV1StudyBurst;
import org.sagebionetworks.dian.datamigration.tools.adherence.SageV2Availability;
import org.sagebionetworks.dian.datamigration.tools.adherence.ScheduledSessionStart;
import org.sagebionetworks.dian.datamigration.tools.adherence.earnings.EarningDetails;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.util.Comparator.*;

public class ScheduleV2Migration {

    public static ObjectMapper objectMapper = new ObjectMapper();
    public static Gson gson = new Gson();
    public static SageScheduleController controller = new SageScheduleController();

    public static void main(String[] args) throws IOException {
        BridgeJavaSdkUtil.initialize(args[0], args[1], args[2]);
        runV2Migration();
    }

    public static void runV2Migration() throws IOException {

        // wbfxxk is the ScheduleTemplate study, it is a test study for figuring out the V2 schedule
        // Once we are ready to deploy this for all studies, use a curated list of all Study IDs
        for (String arcId : BridgeJavaSdkUtil.getArcIdsInStudy("wbfxxk")) {
            StudyParticipant p = BridgeJavaSdkUtil.getParticipantByExternalId(arcId);

            if (p.getStudyIds() == null || p.getStudyIds().isEmpty()) {
                System.out.println(arcId + " has withdrawn");
                continue; // user has withdrawn, no need to migrate
            }

            String uId = p.getId();
            String sId = p.getStudyIds().get(0);

            // Check for a user that has already migrated to V2 and signed into the app
            SageUserClientData clientData = SageUserClientData.Companion.fromStudyParticipant(gson, p);
            if (SageUserClientData.Companion.hasMigrated(clientData)) {
                System.out.println(arcId + " has already migrated to V2, leave their info alone");
                continue;
            }

            SageV1Schedule schedule = createV2Schedule(uId, sId);
            if (schedule == null) {
                System.out.println(arcId + " has no schedule");
                continue;
            }

            SageV2Availability availability = createV2Availability(
                    uId, getAvailabilityJsonFromBridge(uId));
            if (availability == null) {
                System.out.println(arcId + " has no availability");
                continue;
            }

            System.out.println("Performing V2 migration on " + arcId);

            SageEarningsControllerV2 earningsController =
                    createEarningsController(uId, schedule, getCompletedTestsJsonFromBridge(uId));

            StudyActivityEventList eventList = BridgeJavaSdkUtil.getAllTimelineEvents(uId, sId);
            Timeline timeline = BridgeJavaSdkUtil.getParticipantsTimeline(uId, sId);

            updateUserClientData(timeline, uId, availability, earningsController);
            updateAdherenceRecords(timeline, eventList, uId, sId, schedule, earningsController);
        }
    }

    public static String getScheduleJsonFromBridge(String uId) throws IOException {
        String jsonFromBridge = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                uId, BridgeJavaSdkUtil.TEST_SCHEDULE_REPORT_ID, true);
        if ("".equals(jsonFromBridge)) {
            jsonFromBridge = null;
        }
        return jsonFromBridge;
    }

    public static String getAvailabilityJsonFromBridge(String uId) throws IOException {
        String jsonFromBridge = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                uId, BridgeJavaSdkUtil.AVAILABILITY_REPORT_ID, true);
        if ("".equals(jsonFromBridge)) {
            jsonFromBridge = null;
        }
        return jsonFromBridge;
    }

    public static void writeScheduleJsonToFile(String fileId, String data) throws IOException {
        PathsHelper.writeToFile(data,
                Paths.get("DataMigration", "src", "test", "resources")
                        .resolve("bridge2MigrationTests")
                        .resolve("all_schedules")
                        .resolve(fileId + ".json"));
    }

    public static void writeAvailabilityFile(String fileId, String data) throws IOException {
        PathsHelper.writeToFile(data,
                Paths.get("DataMigration", "src", "test", "resources")
                        .resolve("bridge2MigrationTests")
                        .resolve("all_availability")
                        .resolve(fileId + ".json"));
    }

    public static String getCompletedTestsJsonFromBridge(String uId) throws IOException {
        String jsonFromBridge = BridgeJavaSdkUtil.getParticipantReportClientDataString(
                uId, BridgeJavaSdkUtil.COMPLETED_TESTS_REPORT_ID, true);
        if ("".equals(jsonFromBridge)) {
            jsonFromBridge = null;
        }
        return jsonFromBridge;
    }

    public static void writeCompletedTestsJsonToFile(String fileId, String data) throws IOException {
        PathsHelper.writeToFile(data,
                Paths.get("DataMigration", "src", "test", "resources")
                        .resolve("bridge2MigrationTests")
                        .resolve("all_completed")
                        .resolve(fileId + ".json"));
    }

    public static TestSchedule createHMSchedule(String uId, String scheduleJson) throws IOException {
        if (scheduleJson == null || "".equals(scheduleJson)) {
            return null;
        }
        return objectMapper.readValue(scheduleJson, TestSchedule.class);
    }

    public static SageV1Schedule
        createV1Schedule(String uId, String scheduleJson) throws IOException {
        TestSchedule testSchedule = createHMSchedule(uId, scheduleJson);
        if (testSchedule == null) {
            System.out.println("User " + uId + " has no schedule yet");
            return null;
        }
        return controller.createV1Schedule(testSchedule);
    }

    public static SageV1Schedule
        createV2Schedule(String uId, String sId) throws IOException {

        SageV1Schedule v1Schedule = createV1Schedule(
                uId, getScheduleJsonFromBridge(uId));

        if (v1Schedule == null) {
            return null;
        }

        String iANATimezone = getTimezone(v1Schedule);
        for (int i = 0; i < v1Schedule.getStudyBursts().size(); i++) {
            SageV1StudyBurst studyBurst = v1Schedule.getStudyBursts().get(i);

            // V1 HM scheduling had the practice (baseline) test included and set to
            // the first study burst start date. This shifted all study bursts by 1 day.
            // The new V2 Sage scheduling does not include the practice test, and so
            // the study as a whole, and the first study burst schedule starts.
            DateTime startDate = SageScheduleController.Companion
                    .createDateTime(studyBurst.getStartDate()).plusDays(1);

            if (i == 0) {
                // Make sure this is sent first to create the schedule at the correct study start date
                BridgeJavaSdkUtil.updateStudyBurst(
                        uId, sId, SageScheduleController.ACTIVITY_EVENT_CREATE_SCHEDULE,
                        startDate,
                        iANATimezone);
            }
            BridgeJavaSdkUtil.updateStudyBurst(
                // i+1 for burst index, as Bridge designates 01 as first, not 00
                uId, sId, SageScheduleController.Companion.studyBurstActivityEventId(i+1),
                    startDate,
                iANATimezone);
        }

        return v1Schedule;
    }

    public static String getTimezone(SageV1Schedule v1Schedule) {
        // Default to UTC for users that do not have a timezone selected
        // Even if the timezone is not UTC,
        // the calendar day should be the same for V2 Sage scheduling
        return controller.convertToIANATimezone(v1Schedule.getV1Timezone(), "UTC");
    }

    public static SageUserClientData createUserClientData(
            Timeline timeline,
            SageV2Availability availability,
            SageEarningsControllerV2 earningsController) {

        EarningDetails earnings = earningsController.getCurrentEarningsDetails();
        List<String> earningsList = new ArrayList<>();
        for(EarningDetails.Cycle cycle: earnings.cycles) {
            earningsList.add(cycle.total);
        }

        List<ScheduledSessionStart> sessionStartList =
                controller.createAvailableTimeList(timeline.getSchedule(), availability);

        // If we are using this code to write their client data, they have not migrated yet
        return new SageUserClientData(false, sessionStartList, availability, earningsList);
    }

    public static void updateUserClientData(
            Timeline timeline,
            String uId, SageV2Availability availability,
            SageEarningsControllerV2 earningsController) throws IOException {
        SageUserClientData clientData =
                createUserClientData(timeline, availability, earningsController);
        JsonElement clientDataJson = new Gson().toJsonTree(clientData);
        BridgeJavaSdkUtil.updateParticipantClientData(uId, clientDataJson);
    }

    public static WakeSleepSchedule
        createHMAvailability(String uId, String availabilityJson) throws IOException {
        if (availabilityJson == null) {
            return null;
        }
        return objectMapper.readValue(availabilityJson, WakeSleepSchedule.class);
    }

    public static SageV2Availability
        createV2Availability(String uId, String availabilityJson) throws IOException {
        WakeSleepSchedule wakeSleepSchedule = createHMAvailability(uId, availabilityJson);
        if (wakeSleepSchedule == null) {
            System.out.println("User " + uId + " has no availability yet");
            return null;
        }
        return controller.createV2Availability(wakeSleepSchedule);
    }

    public static HmDataModel.CompletedTestList
        createCompletedTests(String completedTestsJson) throws IOException {
        if (completedTestsJson == null || "".equals(completedTestsJson)) {
            return new HmDataModel.CompletedTestList(new ArrayList<>());
        }
        HmDataModel.CompletedTestList fullTestList = objectMapper.readValue(
                completedTestsJson, HmDataModel.CompletedTestList.class);
        fullTestList.completed.sort(comparingDouble(o -> o.completedOn));
        return fullTestList;
    }

    public static SageEarningsControllerV2 createEarningsController(
            String uId, SageV1Schedule v1Schedule,
            String completedTestsJson) throws IOException {

        HmDataModel.CompletedTestList fullTestList = createCompletedTests(completedTestsJson);
        SageEarningsControllerV2 earningsController = new SageEarningsControllerV2();
        earningsController.initializeWithStudyBursts(v1Schedule, fullTestList.completed);

        return earningsController;
    }

    public static void updateAdherenceRecords(
            Timeline timeline, StudyActivityEventList eventList,
            String userId, String studyId, SageV1Schedule v1Schedule,
            SageEarningsControllerV2 earningsController) throws IOException {

        List<AdherenceRecord> adherenceRecordList = createAdherenceRecords(
                timeline, eventList, v1Schedule, earningsController);

        if (!adherenceRecordList.isEmpty()) {
            BridgeJavaSdkUtil.updateAdherence(userId, studyId, adherenceRecordList);
        }
    }

    public static List<AdherenceRecord> createAdherenceRecords(
            Timeline timeline, StudyActivityEventList eventList,
            SageV1Schedule v1Schedule,
            SageEarningsControllerV2 earningsController) {

        String iANATimezone = getTimezone(v1Schedule);

        List<AdherenceRecord> adherenceRecordList = new ArrayList<>();
        for(ScheduledSession session : timeline.getSchedule()) {
            List<ScheduledSession> allSessionsOfDay =
                    controller.allSessionsOfDay(timeline.getSchedule(),
                            session.getStartEventId(), session.getStartDay());
            CompletedTestV2 completed = controller
                    .findCompletedTest(session, allSessionsOfDay, earningsController);
            // Test was completed, make an adherence record for it
            if (completed != null) {
                // To make V2 of the earnings controller more simple and get rid of the
                // the odd first study burst day offset, let's move all week 0 session a day backwards
                // so that they match the rest of the study bursts, and have day as 0 for the first day.
                if ((completed.getWeek() == 0 || completed.getWeek() == 1) && completed.getDay() > 0) {
                    completed = new CompletedTestV2(completed.getEventId(), completed.getWeek(),
                            completed.getDay() - 1, completed.getSession(), completed.getCompletedOn());
                }
                adherenceRecordList.add(controller.createAdherenceRecord(session,
                        controller.eventTimestamp(session.getStartEventId(), eventList),
                        SageScheduleController.Companion.createDateTime(completed.getCompletedOn()),
                        gson.toJsonTree(completed), iANATimezone));
            }
        }

        return adherenceRecordList;
    }
}