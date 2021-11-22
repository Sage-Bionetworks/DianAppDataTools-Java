package org.sagebionetworks.dian.datamigration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.rest.ClientManager;
import org.sagebionetworks.bridge.rest.api.AuthenticationApi;
import org.sagebionetworks.bridge.rest.api.ForResearchersApi;
import org.sagebionetworks.bridge.rest.api.ParticipantReportsApi;
import org.sagebionetworks.bridge.rest.api.ParticipantsApi;
import org.sagebionetworks.bridge.rest.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.rest.model.ClientInfo;
import org.sagebionetworks.bridge.rest.model.ReportData;
import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignIn;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.bridge.rest.model.StudyParticipant;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BridgeJavaSdkUtil {
    // Bridge Authentication vars
    public static String BRIDGE_EMAIL = System.getenv("BR_EMAIL");
    public static String BRIDGE_PW = System.getenv("BR_PW");
    private static String BRIDGE_ID = System.getenv("BR_ID");

    // User attribute keys
    private static final String ATTRIBUTE_ARC_ID = "ARC_ID";
    private static final String ATTRIBUTE_RATER_EMAIL = "RATER_EMAIL";
    private static final String ATTRIBUTE_SITE_NOTES = "SITE_NOTES";
    private static final String ATTRIBUTE_VERIFICATION_CODE = "VERIFICATION_CODE";
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

    // Maximum character count for user attributes
    private static final int ATTRIBUTE_LENGTH_MAX = 255;

    private static ObjectMapper objectMapper = new ObjectMapper();

    private static ForResearchersApi researcherApi;
    private static ParticipantReportsApi reportsApi;
    private static ParticipantsApi participantsApi;

    @VisibleForTesting
    protected static void mockInitialize(ForResearchersApi mockResearcherApi,
                                         ParticipantReportsApi mockReportsApi,
                                         ParticipantsApi mockParticipantsApi) {
        researcherApi = mockResearcherApi;
        reportsApi = mockReportsApi;
        participantsApi = mockParticipantsApi;
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
            String userId = researcherApi.createParticipant(signUp).execute().body().getIdentifier();
            writeUserReports(userId, data);
        }
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
}
