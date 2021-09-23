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

import com.google.common.collect.Lists;

import org.junit.BeforeClass;
import org.junit.Test;
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
import org.sagebionetworks.utils.MD5ChecksumHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SynapseTests {

    private static SynapseClient synapse;

    private static Project project;
    private static String projectId = "syn25791302";

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Create 2 users
        synapse = new SynapseClientImpl();
        synapse.setBearerAuthorizationToken(SynapseUtil.synapsePersonalAccessToken);

        project = new Project();
        project.setId(projectId);
    }

    @Test
    public void testNavigateFolderStructure() throws SynapseException {
        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse response = synapse.getEntityChildren(request);
        assertNotNull(response);
        assertNotNull(response.getPage());
        assertEquals(2, response.getPage().size());

        EntityHeader header = response.getPage().get(0);
        assertNotNull(header);
        assertEquals("2021-07-08", header.getName());

        EntityHeader header2 = response.getPage().get(1);
        assertNotNull(header2);
        assertEquals("2021-09-21", header2.getName());

        EntityChildrenRequest subFolderRequest = new EntityChildrenRequest();
        subFolderRequest.setParentId(header.getId());
        subFolderRequest.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse subFolderResponse = synapse.getEntityChildren(subFolderRequest);
        assertNotNull(subFolderResponse);
        assertNotNull(subFolderResponse.getPage());
        assertEquals(3, subFolderResponse.getPage().size());

        EntityHeader headerTestSession = subFolderResponse.getPage().get(0);
        assertNotNull(header);
        assertEquals("test_session", headerTestSession.getName());

        EntityHeader headerTestSessionSchedule = subFolderResponse.getPage().get(1);
        assertNotNull(header);
        assertEquals("test_session_schedule", headerTestSessionSchedule.getName());

        EntityHeader headerTestWakeSleep = subFolderResponse.getPage().get(2);
        assertNotNull(header);
        assertEquals("wake_sleep_schedule", headerTestWakeSleep.getName());
    }

    @Test
    public void testDownload_testSession_ZIP() throws SynapseException, IOException {
        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));
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
        assertNotNull(testSessionZipResponse);
        assertNotNull(testSessionZipResponse.getPage());
        assertEquals(1, testSessionZipResponse.getPage().size());

        String zipId = testSessionZipResponse.getPage().get(0).getId();
        String zipFilename = testSessionZipResponse.getPage().get(0).getName();

        FileEntity fileEntity = synapse.getEntity(zipId, FileEntity.class);
        String fileHandleId = fileEntity.getDataFileHandleId();
        FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
        fileHandleAssociation.setFileHandleId(fileHandleId);
        fileHandleAssociation.setAssociateObjectId(zipId);
        fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);

        File testSessionsZipFile = File.createTempFile("TestSessions.zip", null);
        testSessionsZipFile.deleteOnExit();
        synapse.downloadFile(fileHandleAssociation, testSessionsZipFile);

        String testSessionsChecksumActual = MD5ChecksumHelper.getMD5Checksum(testSessionsZipFile);
        // Expected was calculated by downloading the zip separately in a browser and getting the checksum
        assertEquals("cda0a862c9e90dacbe0ba33c4caecaaf", testSessionsChecksumActual);
    }

    @Test
    public void testDownload_SessionSchedule_Zip() throws SynapseException, IOException {
        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse response = synapse.getEntityChildren(request);

        // NavigateFolders
        EntityHeader header = response.getPage().get(0);
        EntityChildrenRequest subFolderRequest = new EntityChildrenRequest();
        subFolderRequest.setParentId(header.getId());
        subFolderRequest.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse subFolderResponse = synapse.getEntityChildren(subFolderRequest);
        EntityHeader headerTestSession = subFolderResponse.getPage().get(1);

        // Download ZIPs
        EntityChildrenRequest sessionScheduleZipRequest = new EntityChildrenRequest();
        sessionScheduleZipRequest.setParentId(headerTestSession.getId());
        sessionScheduleZipRequest.setIncludeTypes(Lists.newArrayList(EntityType.file));

        EntityChildrenResponse sessionScheduleZipResponse = synapse.getEntityChildren(sessionScheduleZipRequest);
        assertNotNull(sessionScheduleZipResponse);
        assertNotNull(sessionScheduleZipResponse.getPage());
        assertEquals(1, sessionScheduleZipResponse.getPage().size());

        String zipId = sessionScheduleZipResponse.getPage().get(0).getId();
        String zipFilename = sessionScheduleZipResponse.getPage().get(0).getName();

        FileEntity fileEntity = synapse.getEntity(zipId, FileEntity.class);
        String fileHandleId = fileEntity.getDataFileHandleId();
        FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
        fileHandleAssociation.setFileHandleId(fileHandleId);
        fileHandleAssociation.setAssociateObjectId(zipId);
        fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);

        File testSessionsZipFile = File.createTempFile("TestSessions.zip", null);
        testSessionsZipFile.deleteOnExit();
        synapse.downloadFile(fileHandleAssociation, testSessionsZipFile);

        String testSessionsChecksumActual = MD5ChecksumHelper.getMD5Checksum(testSessionsZipFile);
        // Expected was calculated by downloading the zip separately in a browser and getting the checksum
        assertEquals("c9a015e430f822d175a729b6c5733184", testSessionsChecksumActual);
    }

    @Test
    public void testDownload_WakeSleep_Zip() throws SynapseException, IOException {
        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse response = synapse.getEntityChildren(request);

        // NavigateFolders
        EntityHeader header = response.getPage().get(0);
        EntityChildrenRequest subFolderRequest = new EntityChildrenRequest();
        subFolderRequest.setParentId(header.getId());
        subFolderRequest.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse subFolderResponse = synapse.getEntityChildren(subFolderRequest);
        EntityHeader headerTestSession = subFolderResponse.getPage().get(2);

        // Download ZIPs
        EntityChildrenRequest wakeSleepZipRequest = new EntityChildrenRequest();
        wakeSleepZipRequest.setParentId(headerTestSession.getId());
        wakeSleepZipRequest.setIncludeTypes(Lists.newArrayList(EntityType.file));

        EntityChildrenResponse wakeSleepZipResponse = synapse.getEntityChildren(wakeSleepZipRequest);
        assertNotNull(wakeSleepZipResponse);
        assertNotNull(wakeSleepZipResponse.getPage());
        assertEquals(1, wakeSleepZipResponse.getPage().size());

        String zipId = wakeSleepZipResponse.getPage().get(0).getId();
        String zipFilename = wakeSleepZipResponse.getPage().get(0).getName();

        FileEntity fileEntity = synapse.getEntity(zipId, FileEntity.class);
        String fileHandleId = fileEntity.getDataFileHandleId();
        FileHandleAssociation fileHandleAssociation = new FileHandleAssociation();
        fileHandleAssociation.setFileHandleId(fileHandleId);
        fileHandleAssociation.setAssociateObjectId(zipId);
        fileHandleAssociation.setAssociateObjectType(FileHandleAssociateType.FileEntity);

        File testSessionsZipFile = File.createTempFile("TestSessions.zip", null);
        testSessionsZipFile.deleteOnExit();
        synapse.downloadFile(fileHandleAssociation, testSessionsZipFile);

        String testSessionsChecksumActual = MD5ChecksumHelper.getMD5Checksum(testSessionsZipFile);
        // Expected was calculated by downloading the zip separately in a browser and getting the checksum
        assertEquals("f20a35976d448e5e6a8d4385169b6bc6", testSessionsChecksumActual);
    }


    @Test
    public void testFilterInvalidFolderFormats() throws SynapseException {
        List<EntityHeader> entities = new ArrayList<>();
        entities.add(createEntity("2021-09-24"));
        entities.add(createEntity("2021-10-01"));
        entities.add(createEntity("2021-10-10"));
        entities.add(createEntity("abcdefg"));
        entities.add(createEntity("adsfefsefs2021-10-10"));
        EntityChildrenResponse response = new EntityChildrenResponse();
        response.setPage(entities);
        List<EntityHeader> actual = SynapseUtil.filterInvalidFolderFormats(response);
        assertNotNull(actual);
        assertEquals(3, actual.size());
        assertEquals("2021-09-24", actual.get(0).getName());
        assertEquals("2021-10-01", actual.get(1).getName());
        assertEquals("2021-10-10", actual.get(2).getName());
    }

    @Test
    public void testFindFilterWithName() throws SynapseException {
        List<EntityHeader> entities = new ArrayList<>();
        entities.add(createEntity("test_session"));
        entities.add(createEntity("test_session_schedule"));
        entities.add(createEntity("wake_sleep_schedule"));
        entities.add(createEntity("2021-09-24"));
        entities.add(createEntity("2021-10-01"));
        entities.add(createEntity("2021-10-10"));
        entities.add(createEntity("abcdefg"));
        entities.add(createEntity("adsfefsefs2021-10-10"));
        EntityChildrenResponse response = new EntityChildrenResponse();
        response.setPage(entities);

        // Test null finds
        assertNull(SynapseUtil.findFolderWithName("abcd", response));
        assertNull(SynapseUtil.findFolderWithName("2021-09-2", response));
        assertNull(SynapseUtil.findFolderWithName("test_session1", response));

        // Test matches
        EntityHeader testSession = SynapseUtil
                .findFolderWithName("test_session", response);
        assertNotNull(testSession);
        assertEquals("test_session", testSession.getName());

        EntityHeader testSessionSchedule = SynapseUtil
                .findFolderWithName("test_session_schedule", response);
        assertNotNull(testSessionSchedule);
        assertEquals("test_session_schedule", testSessionSchedule.getName());

        EntityHeader wakeSleepSchedule = SynapseUtil
                .findFolderWithName("wake_sleep_schedule", response);
        assertNotNull(wakeSleepSchedule);
        assertEquals("wake_sleep_schedule", wakeSleepSchedule.getName());
    }

    private EntityHeader createEntity(String name) {
        EntityHeader entity = new EntityHeader();
        entity.setName(name);
        return entity;
    }
}