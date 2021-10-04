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

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.util.Strings;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public class FileHelper {

    /**
     * @param file containing text
     * @return the string contents of the file
     * @throws IOException if somethings goes wrong
     */
    public static String readFile(File file) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(file.getAbsolutePath()));
        return new String(encoded, StandardCharsets.UTF_8);
    }

    /**
     * @param json to write to the file
     * @param file where to save the json file
     * @throws IOException if something goes wrong writing the file
     */
    public static void writeToFile(String json, File file) throws IOException {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        Files.write(Paths.get(file.getAbsolutePath()), jsonBytes);
    }

    /**
     * @param folder to create
     * @throws IllegalStateException if we could not make the folder
     */
    public static void createFolderIfNecessary(File folder) throws IllegalStateException {
        if (!folder.exists()) {
            if (!folder.mkdir()) {
                System.out.println("Could not create folder " + folder.getName());
                throw new IllegalStateException("Could not create folder " + folder.getName());
            }
        }
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
        System.out.println("Full file list for " + root.getName() + " is " + StringUtils.join(fileList, ", "));
        for (File file: fileList) {
            if (file.getName().contains(partOfName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * @param extractedFolder folder containing a list of folders, that contain JSON files
     * @return a list of the file paths to all the JSON files in the extractedFolder
     */
    public static List<String> findAllJsonFilesInFolder(@NonNull File extractedFolder) {
        List<String> retVal = new ArrayList<>();

        File[] fileList = extractedFolder.listFiles();
        if (fileList == null || fileList.length <= 0) {
            return retVal;
        }

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
                    }
                }
            }
        }

        return retVal;
    }
}
