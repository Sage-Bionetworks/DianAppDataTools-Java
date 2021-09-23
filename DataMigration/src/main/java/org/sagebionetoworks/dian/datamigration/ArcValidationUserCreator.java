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

import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.file.FileHandleAssociateType;
import org.sagebionetworks.repo.model.file.FileHandleAssociation;
import org.sagebionetworks.schema.generator.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * This executable file reads a ZIP of QA user participant files
 * that match HM's format, and creates the users on Bridge automatically.
 *
 * This is a helper class created by mdephillips to re-create bridge users
 * every time a new app release is made.
 */
public class ArcValidationUserCreator {

    public static void main(String[] args) {
        System.out.println("Arc Validation User Creation");

        try {
            SynapseUtil.initializeSynapse();

            // This is the file sage_qa_bridge_setup.zip,
            // which mdephillips created to automate QA tester creation
            FileHandleAssociation qaFile = new FileHandleAssociation();
            qaFile.setAssociateObjectId("syn26235874");
            qaFile.setFileHandleId("81987693");
            qaFile.setAssociateObjectType(FileHandleAssociateType.FileEntity);

            // Download and extract the ZIP into JSON files
            File download = new File("qa_participants.zip");
            File downloadExt = SynapseUtil.createFolderIfNecessary(
                    "sage_qa_participant_data");
            System.out.println("Downloading file");
            SynapseUtil.synapse.downloadFile(qaFile, download);
            System.out.println("Unzipping file");
            UnzipUtil.unzip(download.getAbsolutePath(), downloadExt.getAbsolutePath());

            SynapseUtil.DianParticipantFileFolders participantFolders =
                    new SynapseUtil.DianParticipantFileFolders();
            participantFolders.hasdParticipants = new File(
                    "sage_qa_participant_data/sage_qa_bridge_setup");

            List<SynapseUtil.HmUserData> hmUsers =
                    SynapseUtil.createHmUserRaterData(participantFolders);

            System.out.println("Accounts ready to create on bridge " + hmUsers.size());

        } catch (IOException | SynapseException e) {
            System.out.println("Failed to migrate data " + e.getLocalizedMessage());
        }

        File[] folders = new File[] {
                new File("qa_participants.zip"),
                new File("qa_zip_extracted"),
        };
        for (File folder: folders) {
            if (folder.exists()) {
                FileUtils.recursivelyDeleteDirectory(folder);
            }
        }
        System.out.println("Cache successfully deleted");
    }
}


