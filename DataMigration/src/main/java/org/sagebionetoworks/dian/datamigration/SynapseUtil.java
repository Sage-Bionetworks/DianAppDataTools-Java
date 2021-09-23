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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.EntityChildrenRequest;
import org.sagebionetworks.repo.model.EntityChildrenResponse;
import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.EntityType;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.file.FileHandleAssociateType;
import org.sagebionetworks.repo.model.file.FileHandleAssociation;
import org.sagebionetworks.schema.generator.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public class SynapseUtil {

    // Do not change the name of this variable, as it is used in CI to replace the token
    // However, to get the app to run, you must provide your own synapse PAT,
    // NEVER commit your personal access token to github
    public static String synapsePersonalAccessToken = "";

    public static SynapseClient synapse;

    private static Project project;
    private static String projectId = "syn25791302";

    public static String TEST_SESSIONS_FOLDER_NAME = "test_session";
    public static String SESSION_SCHEDULE_FOLDER_NAME = "test_session_schedule";
    public static String WAKE_SLEEP_FOLDER_NAME = "wake_sleep_schedule";
    public static String HASD_PARTICIPANTS_FOLDER_NAME = "hasd_participants";
    public static String EXR_PARTICIPANTS_FOLDER_NAME = "exr_participants";

    public static String EXTRACTED_SUFFIX = "_extracted";
    public static String ZIP = ".zip";

    public static String HASD_PREFIX = "hasd";
    public static String EXR_PREFIX = "exr";
    public static String PARTICIPANT_FILE_SUFFIX = "participant_json.zip";

    public static String CONTAINS_SITE_LOC = "-site_location-";
    public static String CONTAINS_PARTICIPANT_SITE_LOC = "-participant_site_location-";
    public static String CONTAINS_PARTICIPANT = "-participant-";
    public static String CONTAINS_PHONE = "-participant_phone-";

    public static String[] IMPORTANT_FOLDERS = new String[] {
            TEST_SESSIONS_FOLDER_NAME,
            SESSION_SCHEDULE_FOLDER_NAME,
            WAKE_SLEEP_FOLDER_NAME
    };

    public static void initializeSynapse() {
        // Create Synapse API access
        synapse = new SynapseClientImpl();
        synapse.setBearerAuthorizationToken(synapsePersonalAccessToken);

        // Setup project
        project = new Project();
        project.setId(projectId);
    }

    /**
     * Clear all downloaded content
     */
    public static void clearAllFiles() {
        File[] folders = new File[] {
                new File(TEST_SESSIONS_FOLDER_NAME),
                new File(TEST_SESSIONS_FOLDER_NAME + EXTRACTED_SUFFIX),
                new File(SESSION_SCHEDULE_FOLDER_NAME),
                new File(SESSION_SCHEDULE_FOLDER_NAME + EXTRACTED_SUFFIX),
                new File(WAKE_SLEEP_FOLDER_NAME),
                new File(WAKE_SLEEP_FOLDER_NAME + EXTRACTED_SUFFIX),
                new File(HASD_PARTICIPANTS_FOLDER_NAME),
                new File(HASD_PARTICIPANTS_FOLDER_NAME + EXTRACTED_SUFFIX),
                new File(EXR_PARTICIPANTS_FOLDER_NAME),
                new File(EXR_PARTICIPANTS_FOLDER_NAME + EXTRACTED_SUFFIX)
        };
        for (File folder: folders) {
            if (folder.exists()) {
                FileUtils.recursivelyDeleteDirectory(folder);
            }
        }
    }

    /**
     * @param files to download
     * @throws SynapseException throws if download fails
     */
    public static DianFileFolders downloadDianFiles(DianFiles files) throws SynapseException, IOException {
        // Download all test session files
        File testSessionFolder = createFolderIfNecessary(TEST_SESSIONS_FOLDER_NAME);
        File testSessionExtractedFolder = createFolderIfNecessary(
                TEST_SESSIONS_FOLDER_NAME + EXTRACTED_SUFFIX);
        if (testSessionExtractedFolder != null && testSessionFolder != null) {
            for (FileHandleAssociation file : files.testSessions) {
                File download = new File(TEST_SESSIONS_FOLDER_NAME,
                        file.getAssociateObjectId() + ZIP);
                System.out.println("Downloading file " + file.getAssociateObjectId() + ".zip");
                synapse.downloadFile(file, download);
                System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
                UnzipUtil.unzip(download.getAbsolutePath(),
                        testSessionExtractedFolder.getAbsolutePath());
            }
        }

        // Download all test session schedule files
        File testSessionScheduleFolder = createFolderIfNecessary(SESSION_SCHEDULE_FOLDER_NAME);
        File testSessionScheduleExtractedFolder = createFolderIfNecessary(
                SESSION_SCHEDULE_FOLDER_NAME + EXTRACTED_SUFFIX);
        if (testSessionScheduleExtractedFolder != null && testSessionScheduleFolder != null) {
            for (FileHandleAssociation file : files.sessionSchedules) {
                File download = new File(SESSION_SCHEDULE_FOLDER_NAME +
                        File.separator + file.getAssociateObjectId() + ZIP);
                System.out.println("Downloading file " + file.getAssociateObjectId() + ".zip");
                synapse.downloadFile(file, download);
                System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
                UnzipUtil.unzip(download.getAbsolutePath(),
                        testSessionScheduleExtractedFolder.getAbsolutePath());
            }
        }

        // Download all wake sleep schedule files
        File wakeSleepScheduleFolder = createFolderIfNecessary(WAKE_SLEEP_FOLDER_NAME);
        File wakeSleepScheduleExtractedFolder = createFolderIfNecessary(
                WAKE_SLEEP_FOLDER_NAME + EXTRACTED_SUFFIX);
        if (wakeSleepScheduleExtractedFolder != null && wakeSleepScheduleFolder != null) {
            for (FileHandleAssociation file : files.wakeSleepSchedules) {
                File download = new File(WAKE_SLEEP_FOLDER_NAME +
                        File.separator + file.getAssociateObjectId() + ZIP);
                System.out.println("Downloading file " + file.getAssociateObjectId() + ".zip");
                synapse.downloadFile(file, download);
                System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
                UnzipUtil.unzip(download.getAbsolutePath(),
                        wakeSleepScheduleExtractedFolder.getAbsolutePath());
            }
        }

        DianFileFolders folders = new DianFileFolders();
        folders.testSessions = testSessionExtractedFolder;
        folders.sessionSchedules = testSessionScheduleExtractedFolder;
        folders.wakeSleepSchedules = wakeSleepScheduleExtractedFolder;
        return folders;
    }

    /**
     * @param files to download
     * @throws SynapseException throws if download fails
     */
    public static DianParticipantFileFolders downloadDianParticipantFiles(
            DianParticipantFiles files) throws SynapseException, IOException {

        // Download and unzip the hasd and exr participants JSON
        File hasdParticipantsFolder = createFolderIfNecessary(HASD_PARTICIPANTS_FOLDER_NAME);
        File hasdParticipantsExtractedFolder = createFolderIfNecessary(
                HASD_PARTICIPANTS_FOLDER_NAME + EXTRACTED_SUFFIX);
        if (hasdParticipantsFolder != null && hasdParticipantsExtractedFolder != null) {
            if (files.hasdParticipants != null) {
                FileHandleAssociation file = files.hasdParticipants;
                File download = new File(HASD_PARTICIPANTS_FOLDER_NAME +
                        File.separator + file.getAssociateObjectId() + ZIP);
                System.out.println("Downloading file " + file.getAssociateObjectId() + ".zip");
                synapse.downloadFile(file, download);
                System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
                UnzipUtil.unzip(download.getAbsolutePath(),
                        hasdParticipantsExtractedFolder.getAbsolutePath());
            }
        }

        File exrParticipantsFolder = createFolderIfNecessary(EXR_PARTICIPANTS_FOLDER_NAME);
        File exrParticipantsExtractedFolder = createFolderIfNecessary(
                EXR_PARTICIPANTS_FOLDER_NAME + EXTRACTED_SUFFIX);
        if (exrParticipantsFolder != null && exrParticipantsExtractedFolder != null) {
            if (files.exrParticipants != null) {
                FileHandleAssociation file = files.exrParticipants;
                File download = new File(EXR_PARTICIPANTS_FOLDER_NAME +
                        File.separator + file.getAssociateObjectId() + ZIP);
                System.out.println("Downloading file " + file.getAssociateObjectId() + ".zip");
                synapse.downloadFile(file, download);
                System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
                UnzipUtil.unzip(download.getAbsolutePath(),
                        exrParticipantsExtractedFolder.getAbsolutePath());
            }
        }

        DianParticipantFileFolders folders = new DianParticipantFileFolders();
        folders.hasdParticipants = hasdParticipantsExtractedFolder;
        folders.exrParticipants = exrParticipantsExtractedFolder;
        return folders;
    }

    /**
     * @param testSessionExtractedFolder folder containing a list of folders, all of those
     *                                  folders contain json files of completed test sessions
     * @return a map of ARC IDs to their test sessions
     * @throws IOException if a JSON file cannot be read
     */
    public static @NonNull Map<String, CompletedTestList> completedTestMap(
            @NonNull File testSessionExtractedFolder) throws IOException {

        Map<String, CompletedTestList> completedTestMap = new HashMap<>();
        Map<String, List<TestSession>> map = new HashMap<>();

        int migrationFileCount = 0;
        List<String> failedToParseList = new ArrayList<>();

        List<String> filePathList = jsonFilePathList(testSessionExtractedFolder);

        // Iterate through the list of folders of days unzipped
        for (String filePath : filePathList) {
            File file = new File(filePath);
            migrationFileCount++;
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new FileInputStream(file);
            TestSession sessionObj = mapper.readValue(is, TestSession.class);

            if (sessionObj == null) {
                failedToParseList.add(filePath);
            } else {
                List<TestSession> sessions = map.get(sessionObj.participant_id);
                if (sessions == null) {
                    sessions = new ArrayList<>();
                }
                sessions.add(sessionObj);
                map.put(sessionObj.participant_id, sessions);
            }
        }

        System.out.println("Test sessions parsing complete\nParsed " + migrationFileCount + " files.");

        if (!failedToParseList.isEmpty()) {
            System.out.println("Failed to parse file(s) " +
                    String.join(", ", failedToParseList));
        }

        for(String key: map.keySet()) {
            List<TestSession> sessionList = map.get(key);
            CompletedTestList completedTestList = new CompletedTestList(sessionList);
            completedTestMap.put(key, completedTestList);
        }

        return completedTestMap;
    }

    public static @NonNull Map<String, File> sessionScheduleMap(
            @NonNull File testSessionScheduleExtractedFolder) throws IOException {

        Map<String, File> map = new HashMap<>();

        int migrationFileCount = 0;
        List<String> failedToParseList = new ArrayList<>();

        List<String> filePathList = jsonFilePathList(testSessionScheduleExtractedFolder);

        // The ZIPs have json files that all follow the format at the end of the name like this
        int filenameDateSize = "2020-02-20T12-31-13Z.json".length();

        // Iterate through the list of folders of days unzipped
        for (String filePath : filePathList) {
            File file = new File(filePath);
            migrationFileCount++;
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new FileInputStream(file);
            ParticipantScheduleData obj = mapper.readValue(is, ParticipantScheduleData.class);

            if (obj == null || obj.participant_id == null) {
                failedToParseList.add(filePath);
            } else {
                String key = obj.participant_id;
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

        System.out.println("Test sessions parsing complete\nParsed " + migrationFileCount + " files.");

        if (!failedToParseList.isEmpty()) {
            System.out.println("Failed to parse file(s) " +
                    String.join(", ", failedToParseList));
        }

        return map;
    }

    private static List<String> jsonFilePathList(@NonNull File extractedFolder) {
        List<String> retVal = new ArrayList<>();
        File[] fileList = extractedFolder.listFiles();
        if (fileList == null || fileList.length <= 0) {
            return retVal;
        }

        List<String> ignoredFileList = new ArrayList<>();

        // Extracted ZIPs are one
        // Move through the folder structure to get to the JSON files
        for (File file: fileList) {
            // Iterate through the folder/file structure of the unzipped test session content
            if (file.isDirectory()) {
                String[] filePathList = new String[0];
                String[] expectedDirPathList = file.list();
                if (expectedDirPathList != null) {
                    filePathList = expectedDirPathList;
                }
                // Iterate through the list of folders of days unzipped
                for (String filename : filePathList) {
                    // Ignore everything but JSON files
                    if (filename.endsWith(".json")) {
                        retVal.add(file.getAbsolutePath() + File.separator + filename);
                    } else {
                        ignoredFileList.add(filename);
                    }
                }
            }
        }

        if (!ignoredFileList.isEmpty()) {
            System.out.println("Ignored parsing file(s) " +
                    String.join(", ", ignoredFileList));
        }

        return retVal;
    }

    public static @Nullable File createFolderIfNecessary(String folderName) {
        File folder = new File(folderName);
        if (!folder.exists() || !folder.isDirectory()) {
            if (!folder.mkdir()) {
                System.out.println("Could not create folder " + folderName);
                return null;
            }
        }
        return folder;
    }

    /**
     * @return all the relevant ZIP files to download for the migration
     */
    public static DianFiles findRelevantFiles() {
        DianFiles files = new DianFiles();

        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));

        try {
            // Find folders
            EntityChildrenResponse response = synapse.getEntityChildren(request);
            if (response.getPage() == null || response.getPage().isEmpty()) {
                return files;
            }

            List<FileHandleAssociation> testSessions = new ArrayList<>();
            List<FileHandleAssociation> wakeSleepSchedules = new ArrayList<>();
            List<FileHandleAssociation> sessionSchedules = new ArrayList<>();

            List<EntityHeader> relevantFolders = filterInvalidFolderFormats(response);
            for (EntityHeader folder: relevantFolders) {
                // Get the children of the folder
                EntityChildrenRequest subFolderRequest = new EntityChildrenRequest();
                subFolderRequest.setParentId(folder.getId());
                subFolderRequest.setIncludeTypes(Lists.newArrayList(EntityType.folder));
                EntityChildrenResponse subFolderResponse = synapse.getEntityChildren(subFolderRequest);

                // Each folder at this point, should have 3 child folders,
                // test_session, test_session_schedule, and wake_sleep_schedule
                for (String zipFolderName: IMPORTANT_FOLDERS) {
                    EntityHeader zipHeader = findFolderWithName(
                            zipFolderName, subFolderResponse);
                    if (zipHeader != null) {
                        FileHandleAssociation zipFile = zipFileFromFolder(zipHeader);
                        if (zipFile != null) {
                            if (zipFolderName.equals(TEST_SESSIONS_FOLDER_NAME)) {
                                testSessions.add(zipFile);
                            } else if (zipFolderName.equals(SESSION_SCHEDULE_FOLDER_NAME)) {
                                sessionSchedules.add(zipFile);
                            } else {
                                wakeSleepSchedules.add(zipFile);
                            }
                        } else {
                            System.out.println("Could not find a zip file in folder" + zipFolderName);
                        }
                    } else {
                        System.out.println("Could not find expected folder " +
                                zipFolderName + " in folder " + folder.getName());
                    }
                }
            }

            files.testSessions = testSessions;
            files.sessionSchedules = sessionSchedules;
            files.wakeSleepSchedules = wakeSleepSchedules;

        } catch (Exception e) {
            System.out.println("Failed to find all the ZIP files for testSessions, " +
                    "sessionSchedules, and wakeSleepSchedules. Error: " + e.getLocalizedMessage());
        }

        return files;
    }

    /**
     * @return all the relevant participant ZIP files to download for the migration
     */
    public static DianParticipantFiles findRelevantParticipantFiles() {

        DianParticipantFiles files = new DianParticipantFiles();

        try {
            // Now get the HASD / EXR participant ZIP files
            EntityChildrenRequest fileRequest = new EntityChildrenRequest();
            fileRequest.setParentId(project.getId());
            fileRequest.setIncludeTypes(Lists.newArrayList(EntityType.file));

            EntityChildrenResponse fileResponse = synapse.getEntityChildren(fileRequest);
            if (fileResponse.getPage() == null || fileResponse.getPage().isEmpty()) {
                return files;
            }

            for (EntityHeader fileHeader: fileResponse.getPage()) {
                FileEntity fileEntity = null;

                // We only care about the hasd and exr participant JSON ZIP files
                if(fileHeader.getName().startsWith(HASD_PREFIX) &&
                        fileHeader.getName().endsWith(PARTICIPANT_FILE_SUFFIX)) {
                    fileEntity = synapse.getEntity(fileHeader.getId(), FileEntity.class);
                }
                if(fileHeader.getName().startsWith(EXR_PREFIX) &&
                        fileHeader.getName().endsWith(PARTICIPANT_FILE_SUFFIX)) {
                    fileEntity = synapse.getEntity(fileHeader.getId(), FileEntity.class);
                }

                if (fileEntity != null) {
                    String fileHandleId = fileEntity.getDataFileHandleId();
                    FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
                    fileHandleAssociation.setFileHandleId(fileHandleId);
                    fileHandleAssociation.setAssociateObjectId(fileHeader.getId());
                    fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);

                    if (fileHeader.getName().startsWith(HASD_PREFIX)) {
                        System.out.println("Found HASD participants file " + fileHeader.getName());
                        files.hasdParticipants = fileHandleAssociation;
                    } else if (fileHeader.getName().startsWith(EXR_PREFIX)) {
                        System.out.println("Found EXR participants file " + fileHeader.getName());
                        files.exrParticipants = fileHandleAssociation;
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("Failed to find all the ZIP files for the participant JSON. " +
                    "Error: " + e.getLocalizedMessage());
        }

        return files;
    }

    /**
     * @param folders containing the JSON files for test sessions, schedule, and wake sleep survey
     * @return a list of users with unique ARC IDs, and there corresponding user report data
     * @throws IOException on a data processing error
     */
    public static @NonNull List<HmUserData> createHmUserData(DianFileFolders folders) throws IOException {
        List<HmUserData> data = new ArrayList<>();

        Map<String, SynapseUtil.CompletedTestList> testMap =
                completedTestMap(folders.testSessions);
        Map<String, File> testScheduleMap =
                sessionScheduleMap(folders.sessionSchedules);
        Map<String, File> wakeSleepScheduleMap =
                sessionScheduleMap(folders.wakeSleepSchedules);

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
            data.add(user);
        }

        // Sort by Arc ID
        data.sort((u1, u2) -> u1.arcId.compareTo(u2.arcId));

        return data;
    }

    /**
     * @param folders containing both hasd and exr participant JSON
     * @return the full list of hasd and exr users
     * @throws IOException if something goes wrong
     */
    public static @NonNull List<HmUserData> createHmUserRaterData(
            DianParticipantFileFolders folders) throws IOException {

        List<HmUserData> data = new ArrayList<>();

        data.addAll(findAllParticipantsInTableRowData(folders.hasdParticipants));
        data.addAll(findAllParticipantsInTableRowData(folders.exrParticipants));

        // Sort by Arc ID
        data.sort((u1, u2) -> u1.arcId.compareTo(u2.arcId));

        return data;
    }

    /**
     * @param folder that is an unzipped folder with 4-5 participant JSON files
     * @return all the existing participants, their ARC ID, and their site location (rater) email
     * @throws IOException if something goes wrong
     */
    public static @NonNull List<HmUserData> findAllParticipantsInTableRowData(File folder) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<HmUserData> userList = new ArrayList<>();

        if (folder == null) {
            return userList;
        }

        TableRow.ParticipantFiles files = findParticipantFiles(folder);
        TableRow.ParticipantSiteLocation[] userAndSiteLocList = mapper.readValue(
                new FileInputStream(files.participantSiteLocations),
                TableRow.ParticipantSiteLocation[].class);
        TableRow.Participant[] participantList = mapper.readValue(
                new FileInputStream(files.participants),
                TableRow.Participant[].class);
        TableRow.SiteLocation[] siteLocList = mapper.readValue(
                new FileInputStream(files.siteLocations),
                TableRow.SiteLocation[].class);

        TableRow.ParticipantPhone[] phoneList = new TableRow.ParticipantPhone[0];
        if (files.phone != null) {
            phoneList = mapper.readValue(
                    new FileInputStream(files.phone),
                    TableRow.ParticipantPhone[].class);
        }

        for (TableRow.ParticipantSiteLocation userAndSiteLoc: userAndSiteLocList) {
            TableRow.SiteLocation site = TableRow.findSiteLocation(
                    userAndSiteLoc.site_location, siteLocList);
            TableRow.Participant participant = TableRow.findParticipant(
                    userAndSiteLoc.participant, participantList);
            TableRow.ParticipantPhone phone = TableRow.findParticipantPhone(
                    userAndSiteLoc.participant, phoneList);

            if (site != null && participant != null) {
                HmUserData user = new HmUserData();
                user.arcId = participant.participant_id;
                user.siteLocationEmail = site.contact_email;
                user.siteLocationName = site.name;
                if (phone != null) {
                    user.phone = phone.phone;
                }
                if (participant.name != null) {
                    user.name = participant.name;
                }
                userList.add(user);
            } else {
                System.out.println("Failed to create hm user with table id " + userAndSiteLoc.participant);
            }
        }
        return userList;
    }

    /**
     * @param containingFolder that is an unzipped folder with 4-5 participant JSON files
     * @return never null, but the files within the return value may be null if not found
     */
    public static TableRow.ParticipantFiles findParticipantFiles(File containingFolder) {
        TableRow.ParticipantFiles files = new TableRow.ParticipantFiles();

        files.participants = findFileContaining(
                containingFolder, CONTAINS_PARTICIPANT);
        files.participantSiteLocations = findFileContaining(
                containingFolder, CONTAINS_PARTICIPANT_SITE_LOC);
        files.phone = findFileContaining(
                containingFolder, CONTAINS_PHONE);
        files.siteLocations = findFileContaining(
                containingFolder, CONTAINS_SITE_LOC);

        return files;
    }

    /**
     * @param root containing the file to find
     * @param partOfName to look for in the filename for a match
     * @return the file if there was a match, null otherwise
     */
    public static @Nullable File findFileContaining(File root, String partOfName) {
        File[] fileList = root.listFiles();
        if (fileList == null) {
            return null;
        }
        for (File file: fileList) {
            if (file.getName().contains(partOfName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * @param baseFolder to search for the first ZIP file
     * @return the first ZIP file in the folder, null otherwise
     * @throws SynapseException
     */
    public static @Nullable FileHandleAssociation zipFileFromFolder(
            @NonNull EntityHeader baseFolder) throws SynapseException {

        EntityChildrenRequest testSessionZipRequest = new EntityChildrenRequest();
        testSessionZipRequest.setParentId(baseFolder.getId());
        testSessionZipRequest.setIncludeTypes(Lists.newArrayList(EntityType.file));
        EntityChildrenResponse testSessionZipResponse = synapse.getEntityChildren(testSessionZipRequest);

        if (testSessionZipResponse == null ||
                testSessionZipResponse.getPage() == null ||
                testSessionZipResponse.getPage().isEmpty()) {
            return null;
        }

        // Iterate through and get the first ZIP file
        for (EntityHeader fileHeader: testSessionZipResponse.getPage()) {
            String fileName = fileHeader.getName();
            String fileId = fileHeader.getId();
            if (fileName.endsWith(ZIP)) {
                FileEntity fileEntity = synapse.getEntity(fileId, FileEntity.class);
                if (fileEntity != null) {
                    String fileHandleId = fileEntity.getDataFileHandleId();
                    FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
                    fileHandleAssociation.setFileHandleId(fileHandleId);
                    fileHandleAssociation.setAssociateObjectId(fileId);
                    fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);
                    return fileHandleAssociation;
                }
            }
        }

        return null;
    }

    /**
     *
     * @param name of the folder entity to search for
     * @return the folder entity with the designated name, null otherise
     */
    public static @Nullable EntityHeader findFolderWithName(
            @NonNull String name, EntityChildrenResponse response) {

        if (response == null || response.getPage() == null || response.getPage().isEmpty()) {
            return null;
        }

        for (EntityHeader folder: response.getPage()) {
            if (name.equals(folder.getName())) {
                return folder;
            }
        }

        return null;
    }

    /**
     * @param response the synapse response, contain only folder children
     * @return the list of folder entities that match the expected date format
     */
    public static List<EntityHeader> filterInvalidFolderFormats(EntityChildrenResponse response) {
        List<EntityHeader> relevantFolders = new ArrayList<>();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

        // Filter bad folder names
        for (EntityHeader header: response.getPage()) {
            try {
                // Make sure that the folder is a date formatted folder
                Date date = dateFormatter.parse(header.getName());
                relevantFolders.add(header);
            } catch (ParseException exception) {
                System.out.println("Ignoring folder with invalid format " + header.getName());
            }
        }
        return relevantFolders;
    }

    public static class DianFiles {
        public List<FileHandleAssociation> testSessions = new ArrayList<>();
        public List<FileHandleAssociation> wakeSleepSchedules = new ArrayList<>();
        public List<FileHandleAssociation> sessionSchedules = new ArrayList<>();
    }

    public static class DianFileFolders {
        public File testSessions;
        public File wakeSleepSchedules;
        public File sessionSchedules;
    }

    public static class DianParticipantFiles {
        public FileHandleAssociation hasdParticipants;
        public FileHandleAssociation exrParticipants;
    }

    public static class DianParticipantFileFolders {
        public File hasdParticipants;
        public File exrParticipants;
    }

    public static class HmUserData {
        public String arcId;
        public String phone;
        public String name;
        public String siteLocationEmail;
        public String siteLocationName;
        public CompletedTestList completedTests;
        public File wakeSleepSchedule;
        public File testSessionSchedule;
    }

    public static class CompletedTestList {
        public CompletedTestList() {
        }

        /**
         *
         * @param testSessionList from the Synapse data.  These will have duplicates,
         *                        as well as incomplete test sessions. Constructing the
         *                        CompletedTestList through this constructor will create
         *                        a list of only completed, unique test sessions (day, week, session)
         */
        public CompletedTestList(List<TestSession> testSessionList) {
            completed = new ArrayList<>();

            // Loop through and add all unique, completed sessions
            for (TestSession testSession: testSessionList) {
                boolean unique = true;
                if (testSession.finished_session != 1) {
                    unique = false; // skip unfinished schedules
                }
                for (CompletedTest completedTest: completed) {
                    if (completedTest.week == testSession.week &&
                            completedTest.day == testSession.day &&
                            completedTest.session == testSession.session) {
                        unique = false;
                    }
                }
                if (unique) {
                    CompletedTest test = new CompletedTest();
                    test.week = testSession.week;
                    test.day = testSession.day;
                    test.session = testSession.session;
                    test.completedOn = testSession.session_date;
                    completed.add(test);
                }
            }

            // Sort in order of week, day, session
            completed.sort((test1, test2) -> {
                if (test1.week != test2.week) {
                    return Integer.compare(test1.week, test2.week);
                }
                if (test1.day != test2.day) {
                    return Integer.compare(test1.day, test2.day);
                }
                return Integer.compare(test1.session, test2.session);
            });
        }

        public List<CompletedTest> completed;
    }

    public static class CompletedTest {
        public int week;
        public int day;
        public int session;
        public double completedOn;
    }

    /**
     * Classes used to parse hasd and exr participant data
     */
    public static class TableRow {

        public static SiteLocation findSiteLocation(String id, SiteLocation[] sites) {
            for (SiteLocation site: sites) {
                if (site.id != null && site.id.equals(id)) {
                    return site;
                }
            }
            return null;
        }

        public static Participant findParticipant(String id, Participant[] participants) {
            for (Participant participant: participants) {
                if (participant.id != null && participant.id.equals(id)) {
                    return participant;
                }
            }
            return null;
        }

        public static ParticipantPhone findParticipantPhone(String id, ParticipantPhone[] phoneList) {
            for (ParticipantPhone phone: phoneList) {
                if (phone != null && phone.participant_id.equals(id)) {
                    return phone;
                }
            }
            return null;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantFiles {
            public File siteLocations;
            public File participantSiteLocations;
            public File phone;
            public File participants;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class SiteLocation {
            // Table row ID
            public String id;
            // Site location name
            public String name;
            public String contact_phone;
            public String contact_email;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Participant {
            // Table row ID
            public String id;
            // This participant_id is actually the user's ARC ID
            public String participant_id;
            // Only used in QA data sets
            public String name;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantSiteLocation {
            // Table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant;
            // This site_location is the SiteLocationTableRow id field
            public String site_location;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class ParticipantPhone {
            // Table row ID
            public String id;
            // This participant is the ParticipantTableRow id field
            public String participant_id;
            // Internationally formatted phone number, always starting with "+"
            public String phone;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParticipantScheduleData {
        public String participant_id;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TestSession {

        public TestSession() {

        }

        public String participant_id;
        public String os_type;
        public int finished_session; // 1 == finished, 0 == unfinished
        public int day;
        public int session;
        public int week;
        public double session_date;

        @Override
        public boolean equals(Object v) {
            boolean retVal = false;

            if (v instanceof TestSession) {
                TestSession ptr = (TestSession) v;
                retVal = ptr.week == this.week &&
                        ptr.day == this.day &&
                        ptr.session == this.session;
            }

            return retVal;
        }

        @Override
        public int hashCode() {
            return (String.valueOf(week) + String.valueOf(day) + String.valueOf(session)).hashCode();
        }
    }
}
