/*
 * BSD 3-Clause License
 *
 * Copyright 2021  Sage Bionetworks. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1.  Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2.  Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3.  Neither the name of the copyright holder(s) nor the names of any contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission. No license is granted to the trademarks of
 * the copyright holders even if such marks are included in this software.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.sagebionetoworks.dian.datamigration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.sagebionetoworks.dian.datamigration.HmDataModel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


public class MigrationUtil {

    public static String CONTAINS_SITE_LOC = "-site_location-";
    public static String CONTAINS_PARTICIPANT_SITE_LOC = "-participant_site_location-";
    public static String CONTAINS_PARTICIPANT = "-participant-";
    public static String CONTAINS_PHONE = "-participant_phone-";
    public static String CONTAINS_PARTICIPANT_RATER = "participant_rater-";
    public static String CONTAINS_RATERS = "-rater-";

    public static String NO_RATER_ASSIGNED_YET_EMAIL = "no_rater_assigned_yet";

    /**
     * Each file in the parameter list represents a directory
     * where the user's data JSON files were unzipped to.
     * These should map to SynapseUtil.DownloadFolder.dataFolders()
     * @param testSessionUnzippedDir directory containing the test session JSON files
     * @param testSessionScheduleUnzippedDir directory containing the test session schedule JSON files
     * @param wakeSleepScheduleUnzippedDir directory containing the wake sleep schedule JSON files
     * @return the HmUserData created from the unzipped folder files
     * @throws IOException if something went wrong in JSON parsing or reading the files
     */
    public static List<HmUserData> createHmUserData(
            File testSessionUnzippedDir,
            File testSessionScheduleUnzippedDir,
            File wakeSleepScheduleUnzippedDir
    ) throws IOException {

        List<HmUserData> userList = new ArrayList<>();

        Map<String, HmDataModel.CompletedTestList> testMap =
                completedTestMap(testSessionUnzippedDir);
        Map<String, File> testScheduleMap =
                sessionScheduleMap(testSessionScheduleUnzippedDir);
        Map<String, File> wakeSleepScheduleMap =
                sessionScheduleMap(wakeSleepScheduleUnzippedDir);

        Set<String> arcIdSet = new HashSet<>();
        arcIdSet.addAll(testMap.keySet());
        arcIdSet.addAll(testScheduleMap.keySet());
        arcIdSet.addAll(wakeSleepScheduleMap.keySet());

        for (String arcId: arcIdSet) {
            HmUserData user = new HmUserData();
            user.arcId = arcId;
            user.completedTests = testMap.get(arcId);
            user.testSessionSchedule = testScheduleMap.get(arcId);
            user.wakeSleepSchedule = wakeSleepScheduleMap.get(arcId);
            userList.add(user);
        }

        userList.sort((o1, o2) -> o1.arcId.compareTo(o2.arcId));

        return userList;
    }

    /**
     * @param testSessionExtractedFolder folder containing a list of folders, all of those
     *                                  folders contain json files of completed test sessions
     * @return a map of ARC IDs to their test sessions
     * @throws IOException if a JSON file cannot be read
     */
    public static @NonNull Map<String, HmDataModel.CompletedTestList> completedTestMap(
            @NonNull File testSessionExtractedFolder) throws IOException {

        Map<String, HmDataModel.CompletedTestList> completedTestMap = new HashMap<>();
        Map<String, List<HmDataModel.TestSession>> testSessionMap = new HashMap<>();

        int migrationFileCount = 0;
        List<String> failedToParseList = new ArrayList<>();

        List<String> filePathList = FileHelper
                .findAllJsonFilesInFolder(testSessionExtractedFolder);

        // Iterate through the list of folders of days unzipped
        for (String filePath : filePathList) {
            File file = new File(filePath);
            migrationFileCount++;
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new FileInputStream(file);
            System.out.println("Parsing file " + file.getName());
            HmDataModel.TestSession sessionObj = mapper.readValue(is, HmDataModel.TestSession.class);

            if (sessionObj == null) {
                failedToParseList.add(filePath);
            } else {
                String arcId = fixParticipantId(sessionObj.participant_id);
                List<HmDataModel.TestSession> sessions = testSessionMap.get(arcId);
                if (sessions == null) {
                    sessions = new ArrayList<>();
                }
                sessions.add(sessionObj);
                testSessionMap.put(arcId, sessions);
            }
        }

        System.out.println("Test sessions parsing complete\nParsed " + migrationFileCount + " files.");

        if (!failedToParseList.isEmpty()) {
            String errorMsg = "Failed to parse file(s) " +
                    String.join(", ", failedToParseList);
            System.out.println(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        for(String key: testSessionMap.keySet()) {
            List<HmDataModel.TestSession> sessionList = testSessionMap.get(key);
            HmDataModel.CompletedTestList completedTestList =
                    new HmDataModel.CompletedTestList(sessionList);
            completedTestMap.put(key, completedTestList);
        }

        return completedTestMap;
    }

    public static @NonNull Map<String, File> sessionScheduleMap(
            @NonNull File testSessionScheduleExtractedFolder) throws IOException {

        Map<String, File> map = new HashMap<>();

        int migrationFileCount = 0;
        List<String> failedToParseList = new ArrayList<>();

        List<String> filePathList = FileHelper
                .findAllJsonFilesInFolder(testSessionScheduleExtractedFolder);

        // The ZIPs have json files that all follow the format at the end of the name like this
        int filenameDateSize = "2020-02-20T12-31-13Z.json".length();

        // Iterate through the list of folders of days unzipped
        for (String filePath : filePathList) {
            File file = new File(filePath);
            migrationFileCount++;
            ObjectMapper mapper = new ObjectMapper();
            try (InputStream is = new FileInputStream(file)) {
                HmDataModel.ParticipantScheduleData obj =
                        mapper.readValue(is, HmDataModel.ParticipantScheduleData.class);

                if (obj == null || obj.participant_id == null) {
                    failedToParseList.add(filePath);
                } else {
                    String key = fixParticipantId(obj.participant_id);
                    File existingFile = map.get(key);
                    if (existingFile == null) {
                        map.put(key, file);
                    } else {
                        // There are multiple schedule entries per user,
                        // So we want to get the most recent one by looking at the filename
                        // which will always end with an iso 8601 date and ".json"
                        String existingFilePath = existingFile.getAbsolutePath();
                        if (existingFilePath.length() >= filenameDateSize) {
                            existingFilePath = existingFilePath.substring(
                                    existingFilePath.length() - filenameDateSize);
                        }
                        String newFilePath = file.getAbsolutePath();
                        if (newFilePath.length() >= filenameDateSize) {
                            newFilePath = newFilePath.substring(
                                    newFilePath.length() - filenameDateSize);
                        }
                        if (newFilePath.compareTo(existingFilePath) > 0) {
                            map.put(key, file);
                        }
                    }
                }
            }
        }

        System.out.println("Test sessions parsing complete\nParsed " + migrationFileCount + " files.");

        if (!failedToParseList.isEmpty()) {
            String errorMsg = "Failed to parse file(s) " +
                    String.join(", ", failedToParseList);
            System.out.println(errorMsg);
            throw new IllegalArgumentException(errorMsg);
        }

        return map;
    }

    /**
     * @param containingFolder that is an unzipped folder with 4-5 participant JSON files
     * @return never null, but the files within the return value may be null if not found
     */
    public static HmDataModel.TableRow.ParticipantFiles findParticipantFiles(File containingFolder) {
        HmDataModel.TableRow.ParticipantFiles files = new HmDataModel.TableRow.ParticipantFiles();

        files.participants = FileHelper.findFileContaining(
                containingFolder, CONTAINS_PARTICIPANT);
        if (files.participants != null) {
            System.out.println("Found participant file " + files.participants.getName());
        }

        files.participantSiteLocations = FileHelper.findFileContaining(
                containingFolder, CONTAINS_PARTICIPANT_SITE_LOC);
        if (files.participantSiteLocations != null) {
            System.out.println("Found participant site loc file " + files.participantSiteLocations.getName());
        }

        files.phone = FileHelper.findFileContaining(
                containingFolder, CONTAINS_PHONE);
        if (files.phone != null) {
            System.out.println("Found participant phone file " + files.phone.getName());
        }

        files.siteLocations = FileHelper.findFileContaining(
                containingFolder, CONTAINS_SITE_LOC);
        if (files.siteLocations != null) {
            System.out.println("Found site loc file " + files.siteLocations.getName());
        }

        files.participantRaters = FileHelper.findFileContaining(
                containingFolder, CONTAINS_PARTICIPANT_RATER);
        if (files.participantRaters != null) {
            System.out.println("Found participant rater file " + files.participantRaters.getName());
        }

        files.raters = FileHelper.findFileContaining(
                containingFolder, CONTAINS_RATERS);
        if (files.raters != null) {
            System.out.println("Found raters file " + files.raters.getName());
        }

        return files;
    }

    /**
     * @param participantId raw participant ID from HM data model file
     *                      this may be any length 0 to 6
     * @return the participant ID with a length of 6, with leading "0"s added
     */
    public static String fixParticipantId(String participantId) {
        int targetLength = 6;
        if (participantId.length() >= targetLength) {
            return participantId.substring(0, targetLength);
        }
        int zerosToAdd = targetLength - participantId.length();
        String zerosPrefix = StringUtils.repeat("0", targetLength).substring(0, zerosToAdd);
        return zerosPrefix + participantId;
    }

    /**
     * @param participantJsonFolder each file in this list represents a directory
     *                              where the participant JSON files were unzipped to
     *                              these should map to SynapseUtil.DownloadFolder.userFolders()
     * @return the full list of hasd and exr users
     * @throws IOException if something goes wrong
     */
    public static @NonNull List<HmUser> createHmUserRaterData(
            List<File> participantJsonFolder) throws IOException {

        List<HmUser> data = new ArrayList<>();

        for (File folder : participantJsonFolder) {

            ObjectMapper mapper = new ObjectMapper();
            List<HmUser> userList = new ArrayList<>();

            HmDataModel.TableRow.ParticipantFiles files = MigrationUtil.findParticipantFiles(folder);

            HmDataModel.TableRow.ParticipantSiteLocation[] userAndSiteLocList = mapper.readValue(
                    new FileInputStream(files.participantSiteLocations),
                    HmDataModel.TableRow.ParticipantSiteLocation[].class);
            System.out.println("Successfully parsed " + files.participantSiteLocations.getName());

            HmDataModel.TableRow.Participant[] participantList = mapper.readValue(
                    new FileInputStream(files.participants),
                    HmDataModel.TableRow.Participant[].class);
            System.out.println("Successfully parsed " + files.participants.getName());

            HmDataModel.TableRow.SiteLocation[] siteLocList = mapper.readValue(
                    new FileInputStream(files.siteLocations),
                    HmDataModel.TableRow.SiteLocation[].class);
            System.out.println("Successfully parsed " + files.siteLocations.getName());

            HmDataModel.TableRow.ParticipantRater[] userAndRater = mapper.readValue(
                    new FileInputStream(files.participantRaters),
                    HmDataModel.TableRow.ParticipantRater[].class);
            System.out.println("Successfully parsed " + files.participantRaters.getName());

            HmDataModel.TableRow.Rater[] raters = mapper.readValue(
                    new FileInputStream(files.raters), HmDataModel.TableRow.Rater[].class);
            System.out.println("Successfully parsed " + files.raters.getName());

            HmDataModel.TableRow.ParticipantPhone[] phoneList = new HmDataModel.TableRow.ParticipantPhone[0];
            if (files.phone != null) {
                phoneList = mapper.readValue(
                        new FileInputStream(files.phone),
                        HmDataModel.TableRow.ParticipantPhone[].class);
                System.out.println("Successfully parsed " + files.phone.getName());
            }

            for (HmDataModel.TableRow.Participant user : participantList) {
                HmDataModel.TableRow.SiteLocation site = HmDataModel.TableRow
                        .findSiteLocation(user.id, userAndSiteLocList, siteLocList);
                HmDataModel.TableRow.Participant participant = HmDataModel.TableRow
                        .findParticipant(user.id, participantList);
                HmDataModel.TableRow.ParticipantPhone phone = HmDataModel.TableRow
                        .findParticipantPhone(user.id, phoneList);
                HmDataModel.TableRow.Rater rater = HmDataModel.TableRow
                        .findParticipantRater(user.id, userAndRater, raters);

                HmUser userMatch = new HmUser();
                userMatch.arcId = participant.participant_id;

                // In production, site should never be null,
                // but the staging server has accounts without a valid site location
                userMatch.siteLocation = site;

                // If the rater is null at this point,
                // that means HM has created the user,
                // but they have not yet signed in.
                // Therefore, there is nothing to migrate.
                userMatch.rater = rater;

                // HASD users do not have phone numbers,
                // they only have Arc ID, and their pw is their RaterID
                if (phone != null) {
                    userMatch.phone = phone.phone;
                }

                // This is only used in Sage QA,
                // HM does not store a user's name
                if (participant.name != null) {
                    userMatch.name = participant.name;
                }

                // Assign and map the study_id # to a bridge study id string
                if (participant.study_id.equals(HmDataModel.TableRow.STUDY_ID_MAP_ARC_HASD)) {
                    userMatch.studyId = BridgeUtil.STUDY_ID_MAP_ARC_HASD;
                } else if (participant.study_id.equals(HmDataModel.TableRow.STUDY_ID_DIAN_ARC_EXR)) {
                    userMatch.studyId = BridgeUtil.STUDY_ID_DIAN_ARC_EXR;
                } else if (participant.study_id.equals(HmDataModel.TableRow.STUDY_ID_DIAN_OBS_EXR)) {
                    userMatch.studyId = BridgeUtil.STUDY_ID_DIAN_OBS_EXR;
                } else {
                    userMatch.studyId = participant.study_id;
                }

                // Assign the user a random password
                userMatch.password = SecureTokenGenerator.BRIDGE_PASSWORD.nextBridgePassword();

                System.out.println("Successfully added user with table row id " + participant.id);
                userList.add(userMatch);
            }

            data.addAll(userList);
        }

        // Sort by Arc ID
        data.sort((u1, u2) -> u1.arcId.compareTo(u2.arcId));

        return data;
    }

    /**
     * @param userList to organize by site location
     * @param missingSiteId the site location to be used, when the user is not associated with one
     * @return the user lists mapped to site location
     */
    public static Map<TableRow.SiteLocation, List<HmUser>>
        organizeBySiteLocation(List<HmUser> userList, String missingSiteId) {

        TableRow.SiteLocation missingSiteLocation =
                new TableRow.SiteLocation(missingSiteId, missingSiteId);

        Map<TableRow.SiteLocation, List<HmUser>> siteLocationUsers = new HashMap<>();

        for (HmUser user: userList) {
            TableRow.SiteLocation site = user.siteLocation;
            if (site == null) {
                site = missingSiteLocation;
            }
            List<HmUser> usersAtSite = siteLocationUsers.get(site);
            if (usersAtSite == null) {
                usersAtSite = new ArrayList<>();
            }
            usersAtSite.add(user);
            siteLocationUsers.put(site, usersAtSite);
        }
        return siteLocationUsers;
    }

    /**
     * Creates a CSV file for user credentials for each site location.
     * The format of the CSV matches the LastPass credential store import format
     * https://support.logmeininc.com/lastpass/help/how-do-i-import-stored-data-into-lastpass-using-a-generic-csv-file
     *
     * These files are stored in the project's root directory on your computer,
     * and contain user passwords in plain text.
     *
     * It is critical the files are never uploaded anywhere besides LastPass
     * and once they are, they should be deleted from the user's computer.
     *
     * CSV Format Example:
     * url,username,password,extra,name,grouping,fav,,
     * HASD,111111,abcde-fghijk-lmnop-qrstu-v,,111111,SiteAImport,0,,
     * HASD,222222,abcde-fghijk-lmnop-qrstu-v,,222222,SiteAImport,0,,
     * HASD,3333333,abcde-fghijk-lmnop-qrstu-v,,333333,SiteAImport,0,,
     *
     * @param usersBySite users grouped by site location
     */
    public static void createAndSaveLastPassCsvImport(
        Map<HmDataModel.TableRow.SiteLocation, List<HmDataModel.HmUser>> usersBySite)
            throws IOException {

        File root = new File("Credentials");
        FileHelper.createFolderIfNecessary(root);

        for (TableRow.SiteLocation site: usersBySite.keySet()) {
            List<HmDataModel.HmUser> userList = usersBySite.get(site);
            StringBuilder csvData = new StringBuilder();
            csvData.append("url,username,password,extra,name,grouping,fav,,\n");

            for (HmUser user: userList) {
                // URL for app deep link will follow the format
                // https://sagebionetworks.org/STUDY_ID/mobile-auth
                csvData.append("https://sagebionetworks.org/");
                csvData.append(user.studyId);
                csvData.append("/mobile-auth,");
                csvData.append(user.arcId); // username
                csvData.append(",");
                csvData.append(user.password);
                csvData.append(",,");
                csvData.append(user.arcId); // credential name
                csvData.append(",");
                csvData.append(site.name); // LastPass Folder name, by site
                csvData.append(",0,,\n");
            }

            String csvFilename = File.separator + site.name + ".csv";
            File file = new File(root.getAbsolutePath() + csvFilename);
            FileHelper.writeToFile(csvData.toString(), file);
        }
    }
}
