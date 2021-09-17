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
import com.google.common.collect.Lists;

import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseAdminClientImpl;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.repo.model.EntityChildrenRequest;
import org.sagebionetworks.repo.model.EntityChildrenResponse;
import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.EntityType;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.file.FileHandleAssociateType;
import org.sagebionetworks.repo.model.file.FileHandleAssociation;
import org.sagebionetworks.utils.MD5ChecksumHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataMigration {

    // Do not change the name of this variable, as it is used in CI to replace the token
    // However, to get the app to run, you must provide your own synapse PAT,
    // NEVER commit your personal access token
    public static String synapsePersonalAccessToken = "";

    private static SynapseAdminClient adminSynapse;
    private static SynapseClient synapse;

    private static Project project;
    private static String projectId = "syn25791302";

    public static void main(String[] args) {
        System.out.println("Beginning Data Migration");

        // Create 2 users
        adminSynapse = new SynapseAdminClientImpl();
        adminSynapse.setBearerAuthorizationToken(synapsePersonalAccessToken);
        synapse = new SynapseClientImpl();
        synapse.setBearerAuthorizationToken(synapsePersonalAccessToken);

        project = new Project();
        project.setId(projectId);

        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));

        try {
            EntityChildrenResponse response = synapse.getEntityChildren(request);

            // NavigateFolders
            EntityHeader header = response.getPage().get(0);
            EntityChildrenRequest subFolderRequest = new EntityChildrenRequest();
            subFolderRequest.setParentId(header.getId());
            subFolderRequest.setIncludeTypes(Lists.newArrayList(EntityType.folder));
            EntityChildrenResponse subFolderResponse = synapse.getEntityChildren(subFolderRequest);
            EntityHeader headerTestSession = subFolderResponse.getPage().get(0);

            // Download ZIPs
            EntityChildrenRequest testSessionZipRequest = new EntityChildrenRequest();
            testSessionZipRequest.setParentId(headerTestSession.getId());
            testSessionZipRequest.setIncludeTypes(Lists.newArrayList(EntityType.file));

            EntityChildrenResponse testSessionZipResponse = synapse.getEntityChildren(testSessionZipRequest);

            String zipId = testSessionZipResponse.getPage().get(0).getId();
            String zipFilename = testSessionZipResponse.getPage().get(0).getName();

            // this uses current methods
            FileEntity fileEntity = synapse.getEntity(zipId, FileEntity.class);
            String fileHandleId = fileEntity.getDataFileHandleId();
            FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
            fileHandleAssociation.setFileHandleId(fileHandleId);
            fileHandleAssociation.setAssociateObjectId(zipId);
            fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);

            System.out.println("Downloading ZIP from Synapse");
            File f = new File("TestSessions.zip");
            synapse.downloadFile(fileHandleAssociation, f);

            System.out.println("Unzipping download");
            UnzipUtility.unzip(f.getAbsolutePath(), "TestSessionsExtracted");

            Map<String, List<TestSession>> sessionMap = new HashMap<>();

            // Iterate through the folder/file structure of the unzipped content
            File extractedDir = new File("TestSessionsExtracted");
            String[] dirPathList = new String[0];
            String[] expectedDirPathList = extractedDir.list();
            if (expectedDirPathList != null) {
                dirPathList = expectedDirPathList;
            }

            List<String> failedToParseList = new ArrayList<>();
            List<String> ignoredFileList = new ArrayList<>();
            int migrationFileCount = 0;

            // Iterate through the list of folders of days unzipped
            for (String dirName : dirPathList) {
                String subDirectoryName = "TestSessionsExtracted" + File.separator + dirName;
                File expectedFile = new File(subDirectoryName);
                String[] filePathList = new String[0];
                if (expectedFile.exists()) {
                    String[] expectedFilePathList = expectedFile.list();
                    if (expectedFilePathList != null) {
                        filePathList = expectedFilePathList;
                    }
                }
                for (String pathname : filePathList) {
                    // Ignore everything but JSON files
                    if (pathname.endsWith(".json")) {
                        migrationFileCount++;
                        ObjectMapper mapper = new ObjectMapper();
                        File testFile = new File(subDirectoryName + File.separator + pathname);
                        InputStream is = new FileInputStream(testFile);
                        TestSession sessionObj = mapper.readValue(is, TestSession.class);

                        if (sessionObj == null) {
                            failedToParseList.add(pathname);
                        } else {
                            List<TestSession> sessions = sessionMap.get(sessionObj.participant_id);
                            if (sessions == null) {
                                sessions = new ArrayList<>();
                            }
                            sessions.add(sessionObj);
                            sessionMap.put(sessionObj.participant_id, sessions);
                        }
                    } else {
                        ignoredFileList.add(pathname);
                    }
                }
            }

            System.out.println("Data Migration Complete\nMigrated " + migrationFileCount + " files.");

            if (!failedToParseList.isEmpty()) {
                System.out.println("Failed to parse file(s) " +
                         String.join(", ", failedToParseList));
            }
            if (!ignoredFileList.isEmpty()) {
                System.out.println("Ignored parsing file(s) " +
                        String.join(", ", ignoredFileList));
            }

        } catch (Exception e) {
            System.out.println("Failed to download the ZIP file " + e.getLocalizedMessage());
        }
    }
}
