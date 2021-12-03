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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class SynapseUtil {

    public static String synapsePersonalAccessToken = System.getenv("SYN_PAT");
    private static String projectId = System.getenv("SYN_PROJ_ID");

    public static SynapseClient synapse;

    private static Project project;

    // Directory name where all files are downloaded to and unzipped
    public static String DOWNLOAD_DIR = "Downloads";

    // Enum containing all the download folder names, as well as some util functions
    public enum DownloadFolder {
        test_session,
        test_session_schedule,
        wake_sleep_schedule,
        hasd,
        exr;
        public Path downloadFolder() {
            return Paths.get(DOWNLOAD_DIR).resolve(name());
        }
        public Path unzippedFolder() {
            return Paths.get(DOWNLOAD_DIR).resolve(name()).resolve("unzipped");
        }

        public static ArrayList<DownloadFolder> dataFolders() {
            return Lists.newArrayList(test_session, test_session_schedule, wake_sleep_schedule);
        }
        public static ArrayList<DownloadFolder> userFolders() {
            return Lists.newArrayList(hasd, exr);
        }
    }

    public static String ZIP = ".zip";
    public static String PARTICIPANT_FILE_SUFFIX = "participant_json.zip";

    /**
     * Must be called to initialize the Synapse API, before calling download functions.
     * @throws IOException if we cannot create the directories to store download data
     */
    public static void initializeSynapse() throws IOException {
        // Create Synapse API access
        synapse = new SynapseClientImpl();
        synapse.setBearerAuthorizationToken(synapsePersonalAccessToken);

        // Setup project
        project = new Project();
        project.setId(projectId);

        createDownloadDirs();
    }

    /**
     * Creates a temporary directories where all Synapse download/unzipped files are stored
     */
    public static void createDownloadDirs() throws IOException {
        PathsHelper.createFolderIfNecessary(Paths.get(DOWNLOAD_DIR));
        for (DownloadFolder folder: DownloadFolder.values()) {
            PathsHelper.createFolderIfNecessary(folder.downloadFolder());
            PathsHelper.createFolderIfNecessary(folder.unzippedFolder());
        }
    }

    /**
     * Clear all downloaded content
     */
    public static void clearAllFiles() {
        FileUtils.recursivelyDeleteDirectory(new File(DOWNLOAD_DIR));
    }

    /**
     * Finds and unzips all the relevant participant ZIP files for the data migration
     */
    public static void downloadAndUnzipAllUserDataFiles() throws SynapseException, IOException {
        List<EntityHeader> folderEntityList =
                getAllEntityChildren(project.getId(), EntityType.folder);

        // Each day's exported data will be stored in a directory
        // with the format "YYYY-MM-DD" as the directory name
        for (EntityHeader folder: folderEntityList) {
            List<EntityHeader> entityHeaderList =
                    getAllEntityChildren(folder.getId(), EntityType.folder);

            // Each folder at this point, should have 3 child folders,
            // test_session, test_session_schedule, and wake_sleep_schedule
            for (DownloadFolder downloadFolder: DownloadFolder.dataFolders()) {
                // Get the corresponding folder entity, and get all files within that folder
                EntityHeader downloadFolderEntity =
                        findFolderWithName(downloadFolder.name(), entityHeaderList);
                List<EntityHeader> fileEntityList =
                        getAllEntityChildren(downloadFolderEntity.getId(), EntityType.file);

                // Get all ZIP files in the data folder
                List<EntityHeader> zipEntityList = getAllZipFilesFromEntityList(fileEntityList);
                for (EntityHeader zipEntity : zipEntityList) {
                    // Download and unzip each data archive individually
                    FileHandleAssociation file = createFileHandlAssociation(zipEntity);

                    String downloadFileName = file.getAssociateObjectId() + ".zip";
                    System.out.println("Downloading file " + downloadFileName);
                    File downloadFolderFile = downloadFolder
                            .downloadFolder().resolve(downloadFileName).toFile();
                    synapse.downloadFile(file, downloadFolderFile);

                    System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
                    UnzipUtil.unzip(downloadFolderFile.getAbsolutePath(),
                            downloadFolder.unzippedFolder().toFile().getAbsolutePath());
                }
            }
        }
    }

    /**
     * Finds and unzips all the relevant participant ZIP files for the user migration
     */
    public static void downloadAndUnzipAllParticipantFiles() throws SynapseException, IOException {
        // Now get all the HASD / EXR participant ZIP files,
        // that exist in the root directory of the project
        List<EntityHeader> entityHeaderList =
                getAllEntityChildren(project.getId(), EntityType.file);

        for (DownloadFolder downloadFolder: DownloadFolder.userFolders()) {
            EntityHeader zipEntity = zipFileFromEntityList(
                    entityHeaderList, downloadFolder.name(), PARTICIPANT_FILE_SUFFIX);
            FileHandleAssociation file = createFileHandlAssociation(zipEntity);

            String downloadFileName = file.getAssociateObjectId() + ".zip";
            System.out.println("Downloading file " + downloadFileName);
            File downloadFolderFile = downloadFolder
                    .downloadFolder().resolve(downloadFileName).toFile();
            synapse.downloadFile(file, downloadFolderFile);

            System.out.println("Unzipping file " + file.getAssociateObjectId() + ".zip");
            UnzipUtil.unzip(downloadFolderFile.getAbsolutePath(),
                    downloadFolder.unzippedFolder().toFile().getAbsolutePath());
        }
    }

    /**
     * @param parentId of the folder to look for in EntityHeaders
     * @param type of files to look for, usually folder or file
     * @return the full list of entity headers on all pages of result
     */
    public static @NonNull List<EntityHeader> getAllEntityChildren(
            String parentId, EntityType type) throws SynapseException {

        List<EntityHeader> entityHeaderList = new ArrayList<>();

        EntityChildrenRequest fileRequest = new EntityChildrenRequest();
        fileRequest.setParentId(parentId);
        fileRequest.setIncludeTypes(Lists.newArrayList(type));

        while (true) {
            EntityChildrenResponse fileResponse = synapse.getEntityChildren(fileRequest);
            if (fileResponse.getPage() != null) {
                entityHeaderList.addAll(fileResponse.getPage());
            }
            String nextPage = fileResponse.getNextPageToken();
            if (nextPage == null) {
                break;
            } else {
                fileRequest.setNextPageToken(nextPage);
            }
        }

        return entityHeaderList;
    }

    /**
     * @param entityHeaderList to search for all ZIP files
     * @returns all ZIP files in the folder, an empty list otherwise
     */
    public static @NonNull List<EntityHeader> getAllZipFilesFromEntityList(
            @NonNull List<EntityHeader> entityHeaderList) {

        List<EntityHeader> zipFileList = new ArrayList<>();
        // Iterate through and get all the ZIP files available
        for (EntityHeader fileHeader: entityHeaderList) {
            String fileName = fileHeader.getName();
            if (fileName.endsWith(ZIP)) {
                zipFileList.add(fileHeader);
            }
        }
        return zipFileList;
    }

    /**
     * @param entityHeaderList to search for the first ZIP file
     * @param prefix if null, returns the first ZIP,
     *               if non-null, ZIP filename must be prefixed with this string
     * @param suffix if null, returns the first ZIP,
     *               if non-null, ZIP filename must be suffixed with this string
     * @return the first ZIP file in the folder, null otherwise
     * @throws SynapseException
     */
    public static @Nullable EntityHeader zipFileFromEntityList(
            @NonNull List<EntityHeader> entityHeaderList,
            @Nullable String prefix, @Nullable String suffix) {

        // Iterate through and get the first ZIP file
        for (EntityHeader fileHeader: entityHeaderList) {
            String fileName = fileHeader.getName();
            if (fileName.endsWith(ZIP) &&
                    (prefix == null || fileName.startsWith(prefix)) &&
                    (suffix == null || fileName.endsWith(suffix))) {
                return fileHeader;
            }
        }
        return null;
    }

    /**
     * @param entityHeader to convert to a FileHandleAssociation
     * @return the FileHandleAssociation created from the EntityHeader
     * @throws SynapseException if creating the FileHandleAssociation has an error
     */
    private static FileHandleAssociation createFileHandlAssociation(
            EntityHeader entityHeader) throws SynapseException {
        String fileId = entityHeader.getId();
        FileEntity fileEntity = synapse.getEntity(fileId, FileEntity.class);
        String fileHandleId = fileEntity.getDataFileHandleId();
        FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
        fileHandleAssociation.setFileHandleId(fileHandleId);
        fileHandleAssociation.setAssociateObjectId(fileId);
        fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);
        return fileHandleAssociation;
    }

    /**
     *
     * @param name of the folder entity to search for
     * @return the folder entity with the designated name, null otherise
     */
    public static @Nullable EntityHeader findFolderWithName(
            @NonNull String name, List<EntityHeader> entityHeaderList) {
        for (EntityHeader folder: entityHeaderList) {
            if (name.equals(folder.getName())) {
                return folder;
            }
        }
        return null;
    }
}
