package org.sagebionetworks.dian.datamigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.internal.LinkedTreeMap;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.json.JSONObject;
import org.sagebionetworks.bridge.rest.ClientManager;
import org.sagebionetworks.bridge.rest.api.AdherenceRecordsApi;
import org.sagebionetworks.bridge.rest.api.AssessmentsApi;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantReportsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.api.SchedulesV2Api;
import org.sagebionetworks.bridge.rest.api.StudyActivityEventsApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.AdherenceRecord;
import org.sagebionetworks.bridge.rest.model.AdherenceRecordList;
import org.sagebionetworks.bridge.rest.model.AdherenceRecordUpdates;
import org.sagebionetworks.bridge.rest.model.AdherenceRecordsSearch;
import org.sagebionetworks.bridge.rest.model.ClientInfo;
import org.sagebionetworks.bridge.rest.model.ExternalIdentifier;
import org.sagebionetworks.bridge.rest.model.ReportData;
import org.sagebionetworks.bridge.rest.model.Schedule2;
import org.sagebionetworks.bridge.rest.model.ScheduledSession;
import org.sagebionetworks.bridge.rest.model.SearchTermPredicate;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.Study;
import org.sagebionetworks.bridge.rest.model.StudyActivityEventList;
import org.sagebionetworks.bridge.rest.model.StudyActivityEventRequest;
import org.sagebionetworks.bridge.rest.model.StudyList;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;
import org.sagebionetworks.bridge.rest.model.Timeline;
import org.sagebionetworks.dian.datamigration.tools.rescheduler.TestSchedule;

import java.io.IOException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class BridgeJavaSdkUtil {
    // Bridge Authentication vars
    public static String BRIDGE_EMAIL = System.getenv("BR_EMAIL");
    public static String BRIDGE_PW = System.getenv("BR_PW");
    private static String BRIDGE_ID = System.getenv("BR_ID");

    // User attribute keys
    public static final String ATTRIBUTE_ARC_ID = "ARC_ID";
    private static final String ATTRIBUTE_RATER_EMAIL = "RATER_EMAIL";
    private static final String ATTRIBUTE_SITE_NOTES = "SITE_NOTES";
    public static final String ATTRIBUTE_VERIFICATION_CODE = "VERIFICATION_CODE";
    private static final String ATTRIBUTE_PHONE_NUM = "PHONE_NUMBER";
    protected static final String ATTRIBUTE_IS_MIGRATED = "IS_MIGRATED";
    protected static final String ATTRIBUTE_VALUE_FALSE = "false";
    protected static final String ATTRIBUTE_VALUE_TRUE = "true";

    // The name of the rater when one has not been assigned yet
    public static String NO_RATER_ASSIGNED_YET_EMAIL = "No rater assigned yet";

    // Flag migration accounts as test_users so that we can filter them out in Bridge or Synapse
    public static String MIGRATION_DATA_GROUP = "test_user";

    // Report constants
    private static LocalDate REPORT_DATE = LocalDate.parse("1970-01-01");
    public static String AVAILABILITY_REPORT_ID = "Availability";
    public static String TEST_SCHEDULE_REPORT_ID = "TestSchedule";
    public static String COMPLETED_TESTS_REPORT_ID = "CompletedTests";
    public static String EARNINGS_REPORT_ID = "Earnings";

    // Maximum character count for user attributes
    private static final int ATTRIBUTE_LENGTH_MAX = 255;

    private static ObjectMapper objectMapper = new ObjectMapper();

    private static ForResearchersApi researcherApi;
    private static ParticipantReportsApi reportsApi;
    private static ParticipantsApi participantsApi;
    private static StudyActivityEventsApi activityEventsApi;
    private static AssessmentsApi assessmentsApi;
    private static SchedulesV2Api scheduleApi;
    private static AdherenceRecordsApi adherenceRecordsApi;

    @VisibleForTesting
    protected static void mockInitialize(ForResearchersApi mockResearcherApi,
                                         ParticipantReportsApi mockReportsApi,
                                         ParticipantsApi mockParticipantsApi,
                                         StudyActivityEventsApi mockActivityEventsApi,
                                         AssessmentsApi mockAssessmentsApi,
                                         SchedulesV2Api mockScheduleApi,
                                         AdherenceRecordsApi mockAdherenceApi) {
        researcherApi = mockResearcherApi;
        reportsApi = mockReportsApi;
        participantsApi = mockParticipantsApi;
        activityEventsApi = mockActivityEventsApi;
        assessmentsApi = mockAssessmentsApi;
        scheduleApi = mockScheduleApi;
        adherenceRecordsApi = mockAdherenceApi;
    }

    /**
     * Authenticates the admin user using the parameters provided instead of with env vars.
     * Must call this before any other functions in this class will succeed.
     * @param email account for accessing bridge
     * @param password for email account for accessing bridge
     * @param bridgeId bridge project identifier
     * @throws IOException if something went wrong with the network request
     */
    public static void initialize(String email, String password, String bridgeId) throws IOException {
        BRIDGE_EMAIL = email;
        BRIDGE_PW = password;
        BRIDGE_ID = bridgeId;

        initialize();
    }

    /**
     * Authenticates the admin user using the environmental vars for email/password.
     * Must call this before any other functions in this class will succeed.
     * @throws IOException if something went wrong with the network request
     */
    public static void initialize() throws IOException {

        ClientInfo clientInfo = new ClientInfo()
                .appName("DianDataMigration")
                .deviceName("Sage-Bionetworks Device")
                .appVersion(1);

        SignIn signIn = new SignIn()
                .appId(BRIDGE_ID)
                .email(BRIDGE_EMAIL)
                .password(BRIDGE_PW);

        ClientManager clientManager = new ClientManager.Builder()
                .withClientInfo(clientInfo)
                .withSignIn(signIn)
                .withAcceptLanguage(Lists.newArrayList("en")).build();

        AuthenticationApi authApi = clientManager.getClient(AuthenticationApi.class);
        researcherApi = clientManager.getClient(ForResearchersApi.class);
        reportsApi = clientManager.getClient(ParticipantReportsApi.class);
        participantsApi = clientManager.getClient(ParticipantsApi .class);
        activityEventsApi = clientManager.getClient(StudyActivityEventsApi.class);
        assessmentsApi = clientManager.getClient(AssessmentsApi.class);
        scheduleApi = clientManager.getClient(SchedulesV2Api.class);
        adherenceRecordsApi = clientManager.getClient(AdherenceRecordsApi.class);
    }

    /**
     * Call to migrate user and their data.
     * @param user HappyMedium user to migrate
     * @param data app data associated with the user, from HappyMedium's servers
     * @throws IOException if something goes wrong make server calls to Bridge
     */
    public static void migrateUser(HmDataModel.HmUser user,
                                   HmDataModel.HmUserData data) throws IOException {

        try {
            StudyParticipant participant = researcherApi
                    .getParticipantByExternalId(user.externalId, false).execute().body();

            if (isParticipantMigrated(participant, user)) {
                clearMigrationData(participant.getId(), user);
            } else {
                System.out.println("Updating migration data for user " + user.externalId);
                writeUserReports(participant.getId(), data);
            }
        } catch (EntityNotFoundException exception) {
            // Temporary migration user has not been created yet
            System.out.println("Creating migration account for user " + user.externalId);
            SignUp signUp = createSignUpObject(user);
            String userId = createParticipant(signUp);
            writeUserReports(userId, data);
        }
    }

    public static String createParticipant(SignUp signUp) throws IOException {
        return researcherApi.createParticipant(signUp).execute().body().getIdentifier();
    }

    /**
     * @param userId to download reports from
     * @param reportId of the specific report to download
     * @return the client data string for the singleton report downloaded from bridge
     * @throws IOException if something goes wrong
     */
    public static String getParticipantReportClientDataString(
            String userId, String reportId, boolean isOptional) throws IOException {

        List<ReportData> reports = reportsApi.getUsersParticipantReportRecordsV4(
                userId, reportId,
                REPORT_DATE.minusDays(2).toDateTimeAtStartOfDay(),
                REPORT_DATE.plusDays(2).toDateTimeAtStartOfDay(),
                null, 50).execute().body().getItems();

        if (reports.size() != 1) {
            if (isOptional) {
                return null;
            } else {
                throw new IllegalStateException(reportId +
                        " report query had none, or more than one result.");
            }
        }

        if (reports.get(0).getData() instanceof LinkedTreeMap) {
            // Some users may have their data organized as a Map instead of a JSON String
            return new Gson().toJson((LinkedTreeMap)reports.get(0).getData());
        }
        return (String)reports.get(0).getData();
    }

    public static String getParticipantReportClientDataString(
            String userId, String reportId) throws IOException {
        return getParticipantReportClientDataString(userId, reportId, false);
    }

    public static StudyParticipant getParticipantByExternalId(String externalId) throws IOException {
        return researcherApi.getParticipantByExternalId(externalId, false).execute().body();
    }

    /**
     * Create a SignUp object to sign a user up on Bridge.
     * @param user HappyMedium user to sign up
     * @return a SignUp object that can be used to sign up a user on Bridge.
     */
    protected static SignUp createSignUpObject(HmDataModel.HmUser user) {
        SharingScope sharingScope = SharingScope.ALL_QUALIFIED_RESEARCHERS;
        List<String> dataGroups = new ArrayList<>();
        // Migration accounts should be flagged as a "test_user"
        // They should also not share data with Synapse
        if (user.externalId.equals(user.deviceId)) {
            dataGroups.add(MIGRATION_DATA_GROUP);
            sharingScope = SharingScope.NO_SHARING;
        }

        return new SignUp()
                .externalIds(ImmutableMap.of(user.studyId, user.externalId))
                .password(user.password)
                .dataGroups(dataGroups)
                .sharingScope(sharingScope)
                .attributes(newUserAttributes(user));
    }

    /**
     * This will clear the data on the temporary data holding account.
     * This should only be called on a user with device-id as their external id.
     * @param userId from bridge StudyParticipant
     * @param user HappyMedium user
     * @throws IOException if something goes wrong deleting user report data and attributes
     */
    @VisibleForTesting
    protected static void clearMigrationData(
            String userId, HmDataModel.HmUser user) throws IOException {
        System.out.println("Clearing migration data for user " + user.externalId);

        // Delete all user study reports
        reportsApi.deleteAllParticipantReportRecords(userId, COMPLETED_TESTS_REPORT_ID).execute();
        reportsApi.deleteAllParticipantReportRecords(userId, TEST_SCHEDULE_REPORT_ID).execute();
        reportsApi.deleteAllParticipantReportRecords(userId, AVAILABILITY_REPORT_ID).execute();

        StudyParticipant newParticipant = new StudyParticipant()
                .attributes(migratedUserAttributes(user));

        participantsApi.updateParticipant(userId, newParticipant).execute();
    }

    public static void updateParticipantClientData(
            String userId, JsonElement clientDataJson) throws IOException {
        StudyParticipant participant = new StudyParticipant()
                .clientData(clientDataJson);
        participantsApi.updateParticipant(userId, participant).execute();
    }

    @VisibleForTesting
    protected static Map<String, String> migratedUserAttributes(HmDataModel.HmUser user) {
        // Set the new attributes to be blank, except for IS_MIGRATED = true, and ARC_ID
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_TRUE);
        attributes.put(ATTRIBUTE_ARC_ID, user.arcId);
        return attributes;
    }

    /**
     * Write all user reports that have data reflected in the data parameter
     * @param userId of the Bridge user
     * @param data that will be uploaded as user reports
     * @throws IOException if something goes wrong writing the reports to Bridge.
     */
    @VisibleForTesting
    protected static void writeUserReports(String userId, HmDataModel.HmUserData data) throws IOException {
        if (data == null) {
            return; // no data to write
        }
        if (data.completedTests != null) {
            System.out.println("Writing completed tests report");
            reportsApi.addParticipantReportRecordV4(userId, COMPLETED_TESTS_REPORT_ID,
                    makeReportData(objectMapper.writeValueAsString(data.completedTests))).execute();
        }
        if (data.testSessionSchedule != null) {
            System.out.println("Writing schedule report");
            reportsApi.addParticipantReportRecordV4(userId, TEST_SCHEDULE_REPORT_ID,
                    makeReportData(PathsHelper.readFile(data.testSessionSchedule))).execute();
        }
        if (data.wakeSleepSchedule != null) {
            System.out.println("Writing availability report");
            reportsApi.addParticipantReportRecordV4(userId, AVAILABILITY_REPORT_ID,
                    makeReportData(PathsHelper.readFile(data.wakeSleepSchedule))).execute();
        }
    }

    public static void writeUserReport(String userId, String reportId, String json) throws IOException {
        System.out.println("Writing report " + reportId);
        reportsApi.addParticipantReportRecordV4(userId, reportId, makeReportData(json)).execute();
    }

    /**
     * @param participant from Bridge
     * @return true if the user has already migrated, false otherwise.
     */
    protected static boolean isParticipantMigrated(
            StudyParticipant participant, HmDataModel.HmUser user) {

        // No attributes, user is not migrated
        if (participant.getAttributes() == null) {
            return false;
        }
        // We consider external IDs that is a user's ArcId as not able to migrate,
        // Only accounts with device-id as external ID are able to migrate
        if (user.externalId.equals(user.arcId)) {
            return false;
        }
        String isMigratedStr = participant.getAttributes().get(ATTRIBUTE_IS_MIGRATED);
        return ATTRIBUTE_VALUE_TRUE.equals(isMigratedStr);
    }

    /**
     * Create user attributes for signing up a user to Bridge.
     * @param user the user that will be signed up on Bridge.
     * @return the user attributes to attach to the SignUp object.
     */
    protected static Map<String, String> newUserAttributes(HmDataModel.HmUser user) {

        Map<String, String> attributeMap = new HashMap<>();
        String raterEmail = NO_RATER_ASSIGNED_YET_EMAIL;
        if (user.rater != null && user.rater.email != null) {
            raterEmail = user.rater.email;
        }
        if (user.notes != null) {
            attributeMap.put(ATTRIBUTE_SITE_NOTES, user.notes);
        }
        if (user.phone != null) {
            attributeMap.put(ATTRIBUTE_PHONE_NUM, user.phone);
        }

        // If the new user is a 6-digit Arc ID, there is no need for IS_MIGRATED to have a value.
        // IS_MIGRATED is meant for migration accounts that have long Device IDs as their external IDs.
        if (user.externalId.length() != MigrationUtil.PARTICIPANT_ID_LENGTH) {
            attributeMap.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_FALSE);
        }

        attributeMap.put(ATTRIBUTE_ARC_ID, user.arcId);
        attributeMap.put(ATTRIBUTE_RATER_EMAIL, raterEmail);

        // This is a critical part of the user management system for the DIAN project.
        // A user's password is stored securely as an attribute for study managers to see.
        // They will use this to sign their study participants in.
        // User attributes are encrypted at rest and during transit.
        attributeMap.put(ATTRIBUTE_VERIFICATION_CODE, user.password);

        // Make sure the attributes are supported by Bridge
        bridgifyAttributes(attributeMap);

        return attributeMap;
    }

    /**
     * Bridge requires attributes to be less than a certain length
     * This function truncates attributes that are too long so that they save to bridge.
     * @param attributes to make sure they will upload to bridge
     */
    protected static void bridgifyAttributes(Map<String, String> attributes) {
        for (String key: attributes.keySet()) {
            String val = attributes.get(key);
            attributes.put(key, val.substring(0, Math.min(val.length(), ATTRIBUTE_LENGTH_MAX)));
        }
    }

    /**
     * Make a "singleton" report as we call it, where the report is at Jan. 1st, 1970.
     * Sage mobile apps use this to store information that should only be stored once.
     * @param clientData to attach to the report
     * @return a report that can be uploaded to bridge.
     */
    @VisibleForTesting
    protected static ReportData makeReportData(String clientData) {
        ReportData reportData = new ReportData();
        reportData.setLocalDate(REPORT_DATE);
        reportData.setData(clientData);
        return reportData;
    }

    /**
     * Per DIAN-181: If an existing HM user has deleted their app in between test cycles,
     * they will no longer have access to their Device ID, which means they will
     * not be able to migrate to the Sage app automatically by simply opening the app.
     *
     * Once WashU verified that a user is in this state, Ann Campton needs to be able to run a
     * JAR tool that will manually migrate their account similar to how the Sage mobile app does it.
     * Once this migration is complete, when the participant re-installs the app,
     * they will be prompted to sign in with Arc ID and Verification code,
     * which will be available in the Bridge dashboard under this user's Arc ID.
     *
     * @param deviceId to migrate
     * @throws IOException if something goes wrong
     */
    public static void manuallyMigrateUser(String deviceId) throws IOException {
        StudyParticipant participant =
                researcherApi.getParticipantByExternalId(deviceId, false).execute().body();

        System.out.println("Manually migrating Arc ID " +
                participant.getAttributes().get(ATTRIBUTE_ARC_ID));

        System.out.println("Downloading availability report...");
        String availability = getParticipantReportClientDataString(
                participant.getId(), AVAILABILITY_REPORT_ID);
        System.out.println("Downloading test schedule report...");
        String testSchedule = getParticipantReportClientDataString(
                participant.getId(), TEST_SCHEDULE_REPORT_ID);
        System.out.println("Downloading completing test report...");
        String completedTests = getParticipantReportClientDataString(
                participant.getId(), COMPLETED_TESTS_REPORT_ID, true);

        Map<String, String> migratedAttributes = new HashMap<>();
        for (String attrKey : participant.getAttributes().keySet()) {
            // Skip verification code and isMigrated attributes
            if (!attrKey.equals(ATTRIBUTE_VERIFICATION_CODE) &&
                    !attrKey.equals(ATTRIBUTE_IS_MIGRATED)) {
                migratedAttributes.put(attrKey, participant.getAttributes().get(attrKey));
            }
        }

        String arcId = migratedAttributes.get(ATTRIBUTE_ARC_ID);
        if (arcId.length() != 6) {
            throw new IllegalStateException("ARC_ID attribute is not 6 characters long");
        }
        String password = PasswordGenerator.INSTANCE.nextPassword();
        migratedAttributes.put(ATTRIBUTE_VERIFICATION_CODE, password);

        String studyId = participant.getStudyIds().get(0);

        SignUp signUp = new SignUp()
                .externalIds(ImmutableMap.of(studyId, arcId))
                .password(password)
                .dataGroups(new ArrayList<>())
                .sharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .attributes(migratedAttributes);

        System.out.println("Creating participant account on bridge " + arcId);
        String userId = researcherApi.createParticipant(signUp).execute().body().getIdentifier();

        System.out.println("Writing availability report");
        reportsApi.addParticipantReportRecordV4(userId, AVAILABILITY_REPORT_ID,
                makeReportData(availability)).execute();

        System.out.println("Writing test schedule report");
        reportsApi.addParticipantReportRecordV4(userId, TEST_SCHEDULE_REPORT_ID,
                makeReportData(testSchedule)).execute();

        System.out.println("Writing completed tests report");
        if (completedTests == null) {  // Empty completed list
            completedTests = "{\"completed\":[]}";
        }
        reportsApi.addParticipantReportRecordV4(userId, COMPLETED_TESTS_REPORT_ID,
                makeReportData(completedTests)).execute();

        markDeviceIdAccountAsMigrated(participant);

        System.out.println("User successfully migrated");
    }

    public static void markDeviceIdAccountAsMigrated(StudyParticipant participant) throws IOException {
        System.out.println("Setting Device ID account IS_MIGRATED set to true...");
        Map<String, String> deviceIdAttributes = new HashMap<>();
        for (String key : participant.getAttributes().keySet()) {
            if (key.equals(ATTRIBUTE_IS_MIGRATED)) {
                deviceIdAttributes.put(ATTRIBUTE_IS_MIGRATED, ATTRIBUTE_VALUE_TRUE);
            } else {
                deviceIdAttributes.put(key, participant.getAttributes().get(key));
            }
        }
        StudyParticipant updatedDeviceIdParticipant = new StudyParticipant();
        updatedDeviceIdParticipant.setAttributes(deviceIdAttributes);
        researcherApi.updateParticipant(participant.getId(), updatedDeviceIdParticipant).execute();
    }

    /**
     * @return  All users in all studies that have a 6 digit ARC ID as their External ID
     * @throws IOException if something goes wrong
     */
    public static HashSet<String> getAllUsersList() throws IOException {
        HashSet<String> userSet = new HashSet<>();

        System.out.println("Getting all users from Study IDs:");
        List<Study> studyList = researcherApi.getStudies(
                0, 50, false).execute().body().getItems();
        for (Study study : studyList) {
            userSet.addAll(getArcIdsInStudy(study.getIdentifier()));
        }

        return userSet;
    }

    /**
     * @return All users in all studies that have a 6 digit ARC ID as their External ID
     * @param studyId the study ID to search in
     * @throws IOException if something goes wrong
     */
    public static HashSet<String> getArcIdsInStudy(String studyId) throws IOException {
        HashSet<String> userSet = new HashSet<>();

        System.out.println("Getting all users from Study ID " + studyId);

        int offset = 0;
        List<ExternalIdentifier> externalIdList;
        do {
            externalIdList =
                    researcherApi.getExternalIdsForStudy(
                                    studyId, offset, 100, null)
                            .execute().body().getItems();

            for (ExternalIdentifier identifier : externalIdList) {
                if (identifier.getIdentifier().length() == 6) {
                    userSet.add(identifier.getIdentifier());
                }
            }
            offset += 100;
        } while(externalIdList.size() >= 100);

        return userSet;
    }

    /**
     * @return  All users in all studies
     * @throws IOException if something goes wrong
     */
    public static Map<String, List<String>> getAllUsers() throws IOException {
        Map<String, List<String>> userMap = new HashMap<>();

        System.out.println("Getting all users from Study IDs:");
        List<Study> studyList = researcherApi.getStudies(
                0, 50, false).execute().body().getItems();
        for (Study study : studyList) {
            System.out.println(study.getIdentifier());
            int offset = 0;
            List<ExternalIdentifier> externalIdList;
            do {
                externalIdList =
                        researcherApi.getExternalIdsForStudy(
                                study.getIdentifier(), offset, 100, null)
                                .execute().body().getItems();

                for (ExternalIdentifier identifier : externalIdList) {
                    StudyParticipant participant =
                            researcherApi.getParticipantByExternalId(
                                    identifier.getIdentifier(), false).execute().body();
                    String arcID = participant.getAttributes().get("ARC_ID");
                    if (userMap.get(arcID) == null) {
                        userMap.put(arcID, new ArrayList<>());
                    }
                    List<String> accounts = userMap.get(arcID);
                    accounts.add(identifier.getIdentifier());
                }
                offset += 100;
            } while(externalIdList.size() >= 100);
        }

        return userMap;
    }

    public static List<Study> getAllStudies() throws IOException {
        return researcherApi.getStudies(
                0, 50, false).execute().body().getItems();
    }

    public static Timeline getParticipantsTimeline(String userId, String studyId) throws IOException {
        return scheduleApi.getStudyParticipantTimeline(studyId, userId).execute().body();
    }

    public static StudyActivityEventList getAllTimelineEvents(String userId, String studyId) throws IOException {
        return activityEventsApi.getStudyParticipantStudyActivityEvents(studyId, userId).execute().body();
    }


    public static void updateStudyBurst(String userId, String studyId, String eventId,
                                        DateTime dateTime, String timezone) throws IOException {
        StudyActivityEventRequest request = new StudyActivityEventRequest();
        request.setEventId(eventId);
        request.setTimestamp(dateTime);
        request.setClientTimeZone(timezone);
        activityEventsApi.createStudyParticipantStudyActivityEvent(studyId, userId, request).execute();
    }

    public static void updateAdherence(String userId, String studyId, List<AdherenceRecord> records) throws IOException {
        AdherenceRecordUpdates adherenceUpdate = new AdherenceRecordUpdates();
        adherenceUpdate.setRecords(records);
        adherenceRecordsApi.updateStudyParticipantAdherenceRecords(
                studyId, userId, adherenceUpdate).execute();
    }

    public static AdherenceRecordList getUserAdherenceRecords(String userId, String studyId) throws IOException {
        AdherenceRecordsSearch search = new AdherenceRecordsSearch();
        search.setPageSize(500);  // This should always include the entire adherence record list
        return adherenceRecordsApi.searchForStudyParticipantAdherenceRecords(
                studyId, userId, search).execute().body();
    }
}
