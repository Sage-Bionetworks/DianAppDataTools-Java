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
package org.sagebionetworks.dian.datamigration;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.sagebionetworks.dian.datamigration.HmDataModel.*;
import org.sagebionetworks.dian.datamigration.HmDataModel.TableRow.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MigrationUtil {

    // Enum containing all the possible participant file identifiers
    public enum ParticipantFileEnum {
        PARTICIPANT("-participant-"),
        RATER("-rater-"),
        PHONE("-participant_phone-"),
        SITE_LOCATION("-site_location-"),
        PARTICIPANT_SITE_LOCATION("-participant_site_location-"),
        PARTICIPANT_RATER("participant_rater-"),
        PARTICIPANT_DEVICE_ID("-participant_device-"),
        PARTICIPANT_NOTES("-participant_note-");

        ParticipantFileEnum(String identifier) {
            this.identifier = identifier;
        }
        public String identifier;
    }

    // The ZIPs have json files that all follow the format at the end of the name like this
    public static int FILENAME_DATE_SUFFIX_LENGTH = "2020-02-20T12-31-13Z.json".length();

    // The length of all Arc IDs
    public static int PARTICIPANT_ID_LENGTH = 6;

    // Send users without site locations or Device IDs over to a study ID named HappyMedium
    public static String ERROR_STUDY_ID = "Happy-Medium-Errors";
    public static String NO_DEVICE_ID = "No-Device-Id";

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
            Path testSessionUnzippedDir,
            Path testSessionScheduleUnzippedDir,
            Path wakeSleepScheduleUnzippedDir
    ) throws IOException {

        List<HmUserData> userList = new ArrayList<>();

        Map<String, HmDataModel.CompletedTestList> testMap =
                completedTestMap(testSessionUnzippedDir);
        Map<String, Path> testScheduleMap =
                sessionScheduleMap(testSessionScheduleUnzippedDir);
        Map<String, Path> wakeSleepScheduleMap =
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
            @NonNull Path testSessionExtractedFolder) throws IOException {

        Map<String, HmDataModel.CompletedTestList> completedTestMap = new HashMap<>();
        Map<String, List<HmDataModel.TestSession>> testSessionMap = new HashMap<>();

        int migrationFileCount = 0;
        List<String> failedToParseList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        List<Path> filePathList = PathsHelper
                .findAllJsonFilesInDirectory(testSessionExtractedFolder);

        // Iterate through the list of folders of days unzipped
        for (Path file: filePathList) {
            migrationFileCount++;

            String filename = file.getFileName().toString();
            System.out.println("Parsing file " + filename);
            try (InputStream is = Files.newInputStream(file)) {
                HmDataModel.TestSession sessionObj = mapper.readValue(is, HmDataModel.TestSession.class);
                if (sessionObj == null) {
                    failedToParseList.add(filename);
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

    public static @NonNull Map<String, Path> sessionScheduleMap(
            @NonNull Path testSessionScheduleExtractedFolder) throws IOException {

        Map<String, Path> map = new HashMap<>();

        int migrationFileCount = 0;
        List<String> failedToParseList = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        List<Path> filePathList = PathsHelper
                .findAllJsonFilesInDirectory(testSessionScheduleExtractedFolder);

        // Iterate through the list of folders of days unzipped
        for (Path file : filePathList) {
            migrationFileCount++;

            String filename = file.getFileName().toString();
            System.out.println("Parsing file " + filename);
            try (InputStream is = Files.newInputStream(file)) {
                HmDataModel.ParticipantScheduleData obj =
                        mapper.readValue(is, HmDataModel.ParticipantScheduleData.class);

                if (obj == null || obj.participant_id == null) {
                    failedToParseList.add(filename);
                } else {
                    String key = fixParticipantId(obj.participant_id);
                    Path existingFile = map.get(key);
                    if (existingFile == null) {
                        map.put(key, file);
                    } else {
                        // There are multiple schedule entries per user,
                        // So we want to get the most recent one by looking at the filename
                        // which will always end with an iso 8601 date and ".json"
                        String existingFilePath = existingFile.getFileName().toString();
                        if (existingFilePath.length() >= FILENAME_DATE_SUFFIX_LENGTH) {
                            existingFilePath = existingFilePath.substring(
                                    existingFilePath.length() - FILENAME_DATE_SUFFIX_LENGTH);
                        }
                        String newFilePath = filename;
                        if (newFilePath.length() >= FILENAME_DATE_SUFFIX_LENGTH) {
                            newFilePath = newFilePath.substring(
                                    newFilePath.length() - FILENAME_DATE_SUFFIX_LENGTH);
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
    public static Map<ParticipantFileEnum, Path> findParticipantPaths(
            Path containingFolder) throws IOException {

        Map<ParticipantFileEnum, Path> pathMap = new HashMap<>();
        for (ParticipantFileEnum fileEnum: ParticipantFileEnum.values()) {
            Path path = PathsHelper.findFileContaining(containingFolder, fileEnum.identifier);
            if (path != null) {
                pathMap.put(fileEnum, path);
                System.out.println("Found participant file " +
                        fileEnum.identifier + " at path " + path.toString());
            }
        }
        return pathMap;
    }

    /**
     * @param participantId raw participant ID from HM data model file
     *                      this may be any length 0 to 6
     * @return the participant ID with a length of 6, with leading "0"s added
     */
    public static String fixParticipantId(String participantId) {
        if (participantId.length() >= PARTICIPANT_ID_LENGTH) {
            return participantId.substring(0, PARTICIPANT_ID_LENGTH);
        }
        int zerosToAdd = PARTICIPANT_ID_LENGTH - participantId.length();
        String zerosPrefix = StringUtils.repeat("0", zerosToAdd);
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
            List<Path> participantJsonFolder) throws IOException {

        List<HmUser> data = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();

        for (Path folder : participantJsonFolder) {

            List<HmUser> userList = new ArrayList<>();
            Map<ParticipantFileEnum, Path> pathList = MigrationUtil.findParticipantPaths(folder);

            Participant[] participantList = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.PARTICIPANT), Participant[].class);
            Rater[] raters = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.RATER), Rater[].class);
            SiteLocation[] siteLocList = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.SITE_LOCATION), SiteLocation[].class);
            ParticipantPhone[] phoneList = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.PHONE), ParticipantPhone[].class);
            ParticipantNotes[] participantNotes = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.PARTICIPANT_NOTES), ParticipantNotes[].class);
            ParticipantDeviceId[] participantDeviceIds = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.PARTICIPANT_DEVICE_ID), ParticipantDeviceId[].class);
            ParticipantRater[] participantRater = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.PARTICIPANT_RATER), ParticipantRater[].class);
            ParticipantSiteLocation[] participantSiteLocList = TableRow.parseTableRow(mapper, pathList.get(
                    ParticipantFileEnum.PARTICIPANT_SITE_LOCATION), ParticipantSiteLocation[].class);

            for (Participant user : participantList) {
                SiteLocation site = TableRow.findSiteLocation(user.id, participantSiteLocList, siteLocList);
                Participant participant = TableRow.findParticipant(user.id, participantList);
                Rater rater = TableRow.findParticipantRater(user.id, participantRater, raters);
                ParticipantDeviceId deviceId = TableRow.findParticipantDeviceId(user.id, participantDeviceIds);
                ParticipantNotes note = TableRow.findParticipantNotes(user.id, participantNotes);

                // Phones are optional, as they only apply to EXR
                ParticipantPhone phone = null;
                if (phoneList != null) {
                    phone = TableRow.findParticipantPhone(user.id, phoneList);
                }

                HmUser userMatch = new HmUser();
                userMatch.arcId = fixParticipantId(participant.participant_id);

                // Assign the user's Study ID based on various parameters
                assignStudyId(userMatch, site, deviceId, note);

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

                userList.add(userMatch);
            }

            data.addAll(userList);
        }

        // Sort by Arc ID
        data.sort((u1, u2) -> u1.arcId.compareTo(u2.arcId));

        return data;
    }

    public static void assignStudyId(HmUser user, SiteLocation site,
                                     ParticipantDeviceId deviceId, ParticipantNotes notes) {

        // Assign notes or empty string
        String notesStr = notes == null ? "" : notes.note;

        // In this case, there is nothing we can do for migrating the user.
        // They are an invalid user that does not belong to a site yet.
        // Send them to the Happy Medium Error sub-study for tracking purposes.
        if (site == null || site.name == null) {
            String errorNote = " Could not find site location ";
            System.out.println(errorNote + " for user " + user.arcId);
            notesStr += errorNote;

            user.studyId = ERROR_STUDY_ID;
            user.externalId = user.arcId;
            user.password = SecureTokenGenerator.BRIDGE_PASSWORD.nextBridgePassword();
            user.deviceId = deviceId == null ? NO_DEVICE_ID : deviceId.device_id;
            user.notes = notesStr;
            return;
        }

        site.name = bridgifySiteName(site.name);
        // We can assign these for the remaining cases
        user.studyId = site.name;
        user.notes = notesStr;
        user.siteLocation = site;

        // In this case, a user has been created for a site location,
        // but that user has not signed in yet.
        // In this case, make a new account that the site can use later.
        if (deviceId == null) {
            System.out.println("Unused user for site " + user.arcId);
            user.deviceId = NO_DEVICE_ID;
            user.externalId = user.arcId;
            user.password = SecureTokenGenerator.BRIDGE_PASSWORD.nextBridgePassword();
            return;
        }

        // In this case, the user has a valid device-id,
        // which means they have been using the HappyMedium app.
        // We need to create a temporary data holding account that only they can access to.
        // When they update to the Sage Bridge app, they can use their
        // device-id to download their data and create a new account on Bridge.
        System.out.println("Migrating user account as device-id " + user.arcId);
        user.deviceId = deviceId.device_id;
        user.externalId = user.deviceId;
        user.password = user.deviceId;
    }

    /**
     * Bridge does not allow apostrophes, periods, or spaces in site names,
     * and HappyMedium had some, so we must remove them.
     * @param siteName to convert to a bridge acceptable site name
     * @return the converted site name
     */
    public static String bridgifySiteName(String siteName) {
        if (siteName == null) {
            return null;
        }
        return siteName.replace("'", "")
                .replace(".", "")
                .replace(" ", "");
    }

    public static HmUserData findMatchingData(HmUser user, List<HmUserData> dataList) {
        for (HmUserData data: dataList) {
            if (user.arcId.equals(data.arcId)) {
                return data;
            }
        }
        return null;
    }
}
