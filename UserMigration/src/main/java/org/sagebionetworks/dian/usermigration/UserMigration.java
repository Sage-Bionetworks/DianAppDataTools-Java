package org.sagebionetworks.dian.usermigration;

import org.sagebionetworks.dian.datamigration.HmDataModel;
import org.sagebionetworks.dian.datamigration.MigrationUtil;
import org.sagebionetworks.dian.datamigration.SynapseUtil;
import org.sagebionetworks.client.exceptions.SynapseException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserMigration {

    /**
     * This function reads a ZIP of user participant files from Synapse
     * in HM's JSON export format, and creates all the users on Bridge.
     *
     * This must be run BEFORE the DataMigration executable can succeed,
     * as there will be no users to write the user reports to otherwise.
     *
     * After successfully creating the users on Bridge,
     * CSV files will be written to the file system
     * that can be imported to LastPass to store credentials.
     */
    public static void main(String[] args) throws SynapseException, IOException {
        try {
            runUserMigration();
        } finally {
            // Delete all traces of the algorithm.
            // This is for enhanced data privacy,
            // to ensure user data does not remain in the environment.
            SynapseUtil.clearAllFiles();
        }
    }

    private static void runUserMigration() throws SynapseException, IOException {
        System.out.println("Beginning User Migration");

        // TODO: mdephillips 10/2/21 testing docker build, uncomment after
        SynapseUtil.initializeSynapse();
        SynapseUtil.downloadAndUnzipAllParticipantFiles();

        List<File> folders = new ArrayList<>();
        for (SynapseUtil.DownloadFolder downloadFolder : SynapseUtil.DownloadFolder.userFolders()) {
            folders.add(downloadFolder.unzippedFolder());
        }
        List<HmDataModel.HmUser> hmUsers = MigrationUtil.createHmUserRaterData(folders);

        // If the user is missing an associated site location, use this one instead
        String missingSiteName = "Missing site location";

        Map<HmDataModel.TableRow.SiteLocation, List<HmDataModel.HmUser>> usersBySite =
                MigrationUtil.organizeBySiteLocation(hmUsers, missingSiteName);

        MigrationUtil.createAndSaveLastPassCsvImport(usersBySite);

        // TODO: mdephillips 9/22/21 create users on bridge
        System.out.println("Completed user migration successfully");
    }
}