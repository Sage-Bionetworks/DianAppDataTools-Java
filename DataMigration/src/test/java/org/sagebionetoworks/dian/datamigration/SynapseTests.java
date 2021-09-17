package org.sagebionetoworks.dian.datamigration;

import com.google.common.collect.Lists;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sagebionetworks.client.SynapseAdminClient;
import org.sagebionetworks.client.SynapseAdminClientImpl;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.SynapseClientImpl;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.EntityChildrenRequest;
import org.sagebionetworks.repo.model.EntityChildrenResponse;
import org.sagebionetworks.repo.model.EntityHeader;
import org.sagebionetworks.repo.model.EntityType;
import org.sagebionetworks.repo.model.FileEntity;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.download.AddBatchOfFilesToDownloadListRequest;
import org.sagebionetworks.repo.model.download.AddBatchOfFilesToDownloadListResponse;
import org.sagebionetworks.repo.model.download.DownloadListItem;
import org.sagebionetworks.repo.model.file.BulkFileDownloadRequest;
import org.sagebionetworks.repo.model.file.CloudProviderFileHandleInterface;
import org.sagebionetworks.repo.model.file.FileHandle;
import org.sagebionetworks.repo.model.file.FileHandleAssociateType;
import org.sagebionetworks.repo.model.file.FileHandleAssociation;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.utils.MD5ChecksumHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class SynapseTests {

    private static SynapseClient synapse;

    private static Project project;
    private static String projectId = "syn25791302";

    @BeforeClass
    public static void beforeClass() throws Exception {
        // Create 2 users
        synapse = new SynapseClientImpl();
        synapse.setBearerAuthorizationToken(DataMigration.synapsePersonalAccessToken);

        project = new Project();
        project.setId(projectId);
    }

    @Before
    public void before() throws SynapseException {

    }

    @After
    public void after() throws Exception {

    }

    @AfterClass
    public static void afterClass() throws Exception {

    }

    @Test
    public void testNavigateFolderStructure() throws SynapseException {
        EntityChildrenRequest request = new EntityChildrenRequest();
        request.setParentId(project.getId());
        request.setIncludeTypes(Lists.newArrayList(EntityType.folder));
        EntityChildrenResponse response = synapse.getEntityChildren(request);
        assertNotNull(response);
        assertNotNull(response.getPage());
        assertEquals(1, response.getPage().size());

        EntityHeader header = response.getPage().get(0);
        assertNotNull(header);
        assertEquals("2021-07-08", header.getName());

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
}