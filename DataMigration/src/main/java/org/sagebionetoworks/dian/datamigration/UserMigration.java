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

import java.io.IOException;
import java.util.List;

/**
 * This executable file reads a ZIP of user participant files from Synapse
 * in HM's JSON export format, and creates the users on Bridge automatically.
 *
 * This must be run BEFORE the DataMigration executable can succeed.
 */
public class UserMigration {

    public static void main(String[] args) {
        System.out.println("Beginning Data Migration");

        try {
            SynapseUtil.initializeSynapse();

            SynapseUtil.DianParticipantFiles participantFiles =
                    SynapseUtil.findRelevantParticipantFiles();
            SynapseUtil.DianParticipantFileFolders participantFolders =
                    SynapseUtil.downloadDianParticipantFiles(participantFiles);

            List<SynapseUtil.HmUserData> hmUsers =
                    SynapseUtil.createHmUserRaterData(participantFolders);

            // TODO: mdephillips 9/22/21 create users on bridge

        } catch (IOException | SynapseException e) {
            System.out.println("Failed to migrate data " + e.getLocalizedMessage());
        }

        // Delete all traces of the algorithm
        SynapseUtil.clearAllFiles();
    }
}


