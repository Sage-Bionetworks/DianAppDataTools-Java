package org.sagebionetworks.dian.datamigration.tools.newparticipants;

import com.google.common.collect.ImmutableMap;
import com.google.gson.internal.LinkedTreeMap;

import org.sagebionetworks.bridge.rest.model.SharingScope;
import org.sagebionetworks.bridge.rest.model.SignUp;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.dian.datamigration.BridgeJavaSdkUtil;
import org.sagebionetworks.dian.datamigration.PasswordGenerator;
import org.sagebionetworks.dian.datamigration.tools.schedulev2.AdherenceToolV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class AddParticipantTool {

    private static final int TOTAL_ARC_ID_COUNT = 999999;

    public static void main(String[] args) throws IOException, SynapseException {
        if (args.length < 4) {
            throw new IllegalArgumentException("\nAt least 4 arguments must be provided," +
                    "java -jar progam_file.jar bridge_email bridge_pw bridge_project_id new_user_count\n" +
                    "Synapse-backed Bridge accounts are not supported at this time");
        }

        int newUserCount = Integer.parseInt(args[3]);

        String randomStr = "random";
        String testUserStr = "test_user";
        List<String> argsList = Arrays.asList(args);

        boolean testUsers = argsList.contains(testUserStr);
        boolean isRandom = argsList.contains(randomStr);

        String customPw = null;
        String lastArg = argsList.get(argsList.size() - 1);
        if (argsList.size() > 4 &&
                !lastArg.equals(randomStr) &&
                !lastArg.equals(testUserStr)) {
            customPw = lastArg;
        }

        Scanner scanner = new Scanner(System.in);
        addNewParticipant(scanner, args[0], args[1], args[2],
                testUsers, newUserCount, isRandom, customPw);

        scanner.close();
    }

    /**
     * @param email Bridge account email, can't be backed by a Synapse account
     * @param pw Bridge account password
     * @param bridgeId Bridge Project ID
     * @param isTestUser true if new users should have the data group "test_user"
     * @param newUserCount the number of new users to create
     */
    public static void addNewParticipant(Scanner scanner,
                                         String email, String pw, String bridgeId,
                                         boolean isTestUser, int newUserCount,
                                         boolean isRandom, String customPw) throws IOException {

        BridgeJavaSdkUtil.initialize(email, pw, bridgeId);
        Map<String, List<String>> arcIdMap = BridgeJavaSdkUtil.getAllUsers();
        LinkedTreeMap<String, String> newUserArcIdMap = new LinkedTreeMap<>();

        List<String> allArcIds = createTotalArcIdList(100000);
        if (isRandom) {
            Collections.shuffle(allArcIds);
        }

        int i = 0;
        while (newUserArcIdMap.keySet().size() < newUserCount) {
            String arcId = allArcIds.get(i);
            String arcPw = PasswordGenerator.INSTANCE.nextPassword();
            if (customPw != null && isTestUser) {
                arcPw = customPw;
            }
            if (!arcIdExists(arcId, arcIdMap)) {
                newUserArcIdMap.put(arcId, arcPw);
            }
            i++;
            if (i > TOTAL_ARC_ID_COUNT) {
                throw new IllegalStateException("Could not find enough free Arc IDs");
            }
        }

        System.out.println("\nNew Users:");
        for (String arcId : newUserArcIdMap.keySet()) {
            System.out.println(arcId + "   " + newUserArcIdMap.get(arcId));
        }

        System.out.println("Would you like to create these on Bridge? (y/n)");
        if (!AdherenceToolV2.shouldContinueYN(scanner)) {
            System.exit(0);
        }

        System.out.println("\nWhich study would you like to add the users to? " +
                "Please select one from the list above");
        String studyId = scanner.nextLine();

        for (String arcId : newUserArcIdMap.keySet()) {
            String password = newUserArcIdMap.get(arcId);
            SignUp signUp = createSignUp(studyId, arcId, password, isTestUser);
            BridgeJavaSdkUtil.createParticipant(signUp);
            System.out.println("Successfully created " + arcId);
        }

        System.out.println("New users added successfully");
    }

    private static SignUp createSignUp(String studyId, String arcId,
                                       String password, boolean isTestUser) {

        Map<String, String> attributes = new HashMap<>();
        attributes.put(BridgeJavaSdkUtil.ATTRIBUTE_VERIFICATION_CODE, password);

        SignUp signUp = new SignUp()
                .externalIds(ImmutableMap.of(studyId, arcId))
                .password(password)
                .sharingScope(SharingScope.ALL_QUALIFIED_RESEARCHERS)
                .attributes(attributes);

        if (isTestUser) {
            signUp.dataGroups(Collections.singletonList("test_user"));
        }

        return signUp;
    }

    private static List<String> createTotalArcIdList() {
        return createTotalArcIdList(0);
    }

    private static List<String> createTotalArcIdList(int startingNumber) {
        if (startingNumber > TOTAL_ARC_ID_COUNT) {
            throw new IllegalStateException("Starting ARC ID cannot" +
                    " be greater than " + TOTAL_ARC_ID_COUNT);
        }
        List<String> allArcIds = new ArrayList<>();
        for (int i = startingNumber; i < TOTAL_ARC_ID_COUNT; i++) {
            allArcIds.add(String.format("%06d", i));
        }
        return allArcIds;
    }

    private static boolean arcIdExists(String newArcId, Map<String, List<String>> arcIdMap) {
        for (String key : arcIdMap.keySet()) {
            List<String> arcIdList = arcIdMap.get(key);
            for (String existingArcId : arcIdList) {
                if (existingArcId.equals(newArcId)) {
                    return true;
                }
            }
        }
        return false;
    }
}