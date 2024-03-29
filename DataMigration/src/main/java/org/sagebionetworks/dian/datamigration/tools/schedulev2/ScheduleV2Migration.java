package org.sagebionetworks.dian.datamigration.tools.schedulev2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.sagebionetworks.bridge.rest.model.AdherenceRecord;
import org.sagebionetworks.bridge.rest.model.ScheduledSession;
import org.sagebionetworks.bridge.rest.model.Study;
import org.sagebionetworks.bridge.rest.model.StudyActivityEventList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.Timeline;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.FileLogger;
import org.sagebionetworks.dian.datamigration.HmDataModel;
import org.sagebionetworks.dian.datamigration.PathsHelper;
import org.sagebionetworks.dian.datamigration.SynapseUtil;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static java.util.Comparator.*;

public class ScheduleV2Migration {

    private static FileLogger fileLogger = new FileLogger();
    public static ObjectMapper objectMapper = new ObjectMapper();
    public static Gson gson = new Gson();
    public static SageScheduleController controller = new SageScheduleController();

    public static void main(String[] args) throws IOException, Throwable {
        fileLogger.openFile();

        BridgeJavaSdkUtil.initialize();
        runV2Migration();

        BridgeJavaSdkUtil.BRIDGE_ID = BridgeJavaSdkUtil.BRIDGE_ID2;

        BridgeJavaSdkUtil.initialize();
        runV2Migration();
        
        File file = fileLogger.closeFile();
        SynapseUtil.initializeSynapse();
        SynapseUtil.uploadToSynapse(file, SynapseUtil.projectId);
    }

    public static void runV2Migration() throws IOException {

        StringBuilder errorStrings = new StringBuilder();

        List<Study> studyList = BridgeJavaSdkUtil.getAllStudies();
        for (Study study : studyList) {
            String studyId = study.getIdentifier();

            fileLogger.write("Getting all users from Study ID " + studyId);
            HashSet<String> arcIdList = BridgeJavaSdkUtil.getArcIdsInStudy(studyId);

            // Once we are ready to deploy this for all studies, use a curated list of all Study IDs
            for (String arcId : arcIdList) {
                try {
                    StudyParticipant p = BridgeJavaSdkUtil.getParticipantByExternalId(arcId);

                    if (p.getStudyIds() == null || p.getStudyIds().isEmpty()) {
                        fileLogger.write(arcId + " has withdrawn");
                        continue; // user has withdrawn, no need to migrate
                    }

                    String uId = p.getId();
                    String sId = p.getStudyIds().get(0);

                    SageV2Availability availability = createV2Availability(
                            uId, getAvailabilityJsonFromBridge(uId));
                    if (availability == null) {
                        fileLogger.write(arcId + " has no availability");
                        continue;
                    }

                    SageV1Schedule v1Schedule = createV1Schedule(uId, getScheduleJsonFromBridge(uId));
                    if (v1Schedule == null) {
                        fileLogger.write(arcId + " has no schedule");
                        continue;
                    }

                    fileLogger.write("Performing V2 migration on " + arcId);

                    // Check for a user that has already migrated to V2 and signed into the app
                    SageUserClientData clientData = SageUserClientData.Companion.fromStudyParticipant(gson, p);
                    boolean hasMigrated = SageUserClientData.Companion.hasMigrated(clientData);

                    if (!hasMigrated) {
                        fileLogger.write("Creating schedule for " + arcId);
                        createV2Schedule(arcId, v1Schedule, uId, sId);
                    }

                    SageEarningsControllerV2 earningsController =
                            createEarningsController(uId, v1Schedule, getCompletedTestsJsonFromBridge(uId));

                    StudyActivityEventList eventList = BridgeJavaSdkUtil.getAllTimelineEvents(uId, sId);
                    Timeline timeline = BridgeJavaSdkUtil.getParticipantsTimeline(uId, sId);

                    // If the user has already signed into the V2 app, don't overwrite their client data,
                    // because it could overwrite any schedule or availability changes the user did in the mobile app.
                    if (!hasMigrated) {
                        fileLogger.write("Updating user client data for " + arcId);
                        updateUserClientData(timeline, p, availability, earningsController);
                    }
                    // However, always update their adherence record list as it should be safe
                    // and will not overwrite any data from the new V2 mobile app.
                    fileLogger.write("Updating adherence for " + arcId);
                    updateAdherenceRecords(timeline, eventList, uId, sId, v1Schedule, earningsController);
                } catch (Exception e) {
                    errorStrings.append("\nError migrating ").append(arcId)
                            .append("\n").append(e.getMessage()).append("\n");
                }
            }
        }

        if (errorStrings.length() == 0) {
            fileLogger.write("\nNoErrors");
        } else {
            fileLogger.write("\n" + errorStrings.toString());
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
            fileLogger.write("User " + uId + " has no schedule yet");
            return null;
        }
        return controller.createV1Schedule(testSchedule);
    }

    public static void
    createV2Schedule(String arcId, SageV1Schedule v1Schedule, String uId, String sId) throws IOException {

        String iANATimezone = getTimezone(v1Schedule);
        fileLogger.write("User timezone is " + iANATimezone);

        DateTime studyStart = SageScheduleController.Companion
                .createDateTime(v1Schedule.getStudyBursts().get(0).getStartDate(), iANATimezone)
                // Putting this date at noon, gives Bridge the most flexibility with
                // how it adjusts local times and time zones to get the correct start day
                .withTimeAtStartOfDay().plusHours(12);

        for (int i = 0; i < v1Schedule.getStudyBursts().size(); i++) {
            SageV1StudyBurst studyBurst = v1Schedule.getStudyBursts().get(i);

            // The new V2 Sage scheduling does not include the practice test, and so
            // the study as a whole, and the first study burst schedule starts.
            DateTime startDate = SageScheduleController.Companion
                    .createDateTime(studyBurst.getStartDate(), iANATimezone)
                    // Putting this date at 1 PM, gives Bridge the most flexibility with
                    // how it adjusts local times and time zones to get the correct start day
                    // Add one additional hour to account for any daylight savings issues
                    .withTimeAtStartOfDay().plusHours(13);

            // HappyMedium's app had future study bursts that were off by 1 day and these
            // make the dashboard looks disjointed.
            // This doesn't happen all the time, so first check for the situation.
            // To fix it, move each future study burst start date by 1 day
            if (startDate.isAfterNow()) {
                // plus 1 hour accounts for daylight savings time issues
                int daysFromStart = Days.daysBetween(studyStart, startDate).getDays();
                int expectedDays = SageScheduleController.Companion.getWeeksBetweenStudyBursts() *
                        SageScheduleController.Companion.getDaysInAllStudyBursts() * i;
                if ((expectedDays - daysFromStart) == 1) {
                    startDate = startDate.plusDays(1);
                    fileLogger.write(arcId + " study burst " + (i+1) +
                            " is off by 1 day, moving to 1 day in the future");
                } else if ((expectedDays - daysFromStart) == -1) {
                    startDate = startDate.minusDays(1);
                    fileLogger.write(arcId + " study burst " + (i+1) +
                            " is off by 1 day, moving to 1 day in the past");
                }
            }

            if (i == 0) {
                // the first study burst start date. This shifted all study bursts by 1 day.
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

        String baselineCompletionDate = null;
        if (earningsController.getBaselineTestComplete() != null) {
            DateTime completionDate = SageScheduleController.Companion.createDateTime(
                    earningsController.getBaselineTestComplete().completedOn);
            DateTimeFormatter dtf = DateTimeFormat.forPattern("MM/dd/yyyy HH:mm:ss");
            baselineCompletionDate = dtf.print(completionDate);
        }

        // If we are using this code to write their client data, they have not migrated yet
        return new SageUserClientData(false, baselineCompletionDate,
                sessionStartList, availability, earningsList);
    }

    public static void updateUserClientData(
            Timeline timeline,
            StudyParticipant participant, SageV2Availability availability,
            SageEarningsControllerV2 earningsController) throws IOException {
        SageUserClientData clientData =
                createUserClientData(timeline, availability, earningsController);
        JsonElement clientDataJson = new Gson().toJsonTree(clientData);
        BridgeJavaSdkUtil.updateParticipantClientData(participant, clientDataJson);
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
            fileLogger.write("User has no availability yet");
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
            SageEarningsControllerV2 earningsController) throws IOException {

        String iANATimezone = getTimezone(v1Schedule);

        int i = 0;

        List<AdherenceRecord> adherenceRecordList = new ArrayList<>();
        for(ScheduledSession session : timeline.getSchedule()) {
            List<ScheduledSession> allSessionsOfDay =
                    controller.allSessionsOfDay(timeline.getSchedule(),
                            session.getStartEventId(), session.getStartDay());
            CompletedTestV2 completed = controller
                    .findCompletedTest(session, allSessionsOfDay, earningsController);

            // Test was completed, make an adherence record for it
            if (completed != null) {
                i++;
                // To make V2 of the earnings controller more simple and get rid of the
                // the odd first study burst day offset, let's move all week 0 session a day backwards
                // so that they match the rest of the study bursts, and have day as 0 for the first day.
                if ((completed.getWeek() == 0 || completed.getWeek() == 1) && completed.getDay() > 0) {
                    completed = new CompletedTestV2(completed.getEventId(), 0,
                            completed.getDay() - 1, completed.getSession(), completed.getCompletedOn());
                }
                adherenceRecordList.add(controller.createAdherenceRecord(session,
                        controller.eventTimestamp(session.getStartEventId(), eventList),
                        SageScheduleController.Companion.createDateTime(completed.getCompletedOn()),
                        gson.toJsonTree(completed), iANATimezone));
            }
        }
        fileLogger.write(i + " adherence records created");

        return adherenceRecordList;
    }
}
