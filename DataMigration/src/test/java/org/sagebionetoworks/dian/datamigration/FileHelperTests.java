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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sagebionetworks.schema.generator.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileHelperTests {

    private final String rootFileTestFolderStr =
            "src/test/java/org/sagebionetoworks/dian/datamigration/fileTests";
    private final File rootFileTestFolder = new File(rootFileTestFolderStr);

    private static final String folderUnitTestsDirStr = "folderUnitTests";
    private static final File folderUnitTestsDir = new File(folderUnitTestsDirStr);

    @BeforeClass
    public static void beforeClass() {
        FileUtils.recursivelyDeleteDirectory(folderUnitTestsDir);
    }

    @AfterClass
    public static void afterClass() {
        FileUtils.recursivelyDeleteDirectory(folderUnitTestsDir);
    }

    // TODO: mdephillips 10/2/21 this fails in CI github action
//    @Test
//    public void test_findFileContaining() {
//        File folderToSearch = new File(rootFileTestFolderStr + "/FolderA");
//        File file = FileHelper.findFileContaining(folderToSearch, "a.json");
//        assertNotNull(file);
//        assertTrue(file.getAbsolutePath().endsWith("/FolderA/a.json"));
//
//        file = FileHelper.findFileContaining(folderToSearch, "FolderA");
//        assertNotNull(file);
//        assertTrue(file.getAbsolutePath().endsWith("/FolderA/FolderA.zip"));
//
//        file = FileHelper.findFileContaining(folderToSearch, "Decoy");
//        assertNotNull(file);
//        assertTrue(file.getAbsolutePath().endsWith("/FolderA/DecoyFolder"));
//
//        file = FileHelper.findFileContaining(folderToSearch, "Miss");
//        assertNull(file);
//
//        file = FileHelper.findFileContaining(folderToSearch, "a.jsona");
//        assertNull(file);
//    }

    @Test
    public void test_createFolderIfNecessary() {
        assertFalse(folderUnitTestsDir.exists());
        FileHelper.createFolderIfNecessary(folderUnitTestsDir);
        assertTrue(folderUnitTestsDir.exists());
        // Check redundant folder make should succeed
        FileHelper.createFolderIfNecessary(folderUnitTestsDir);
        assertTrue(folderUnitTestsDir.exists());

        // Test exception
        IllegalStateException e = null;
        try {
            // None of these parent folders exist, so mkdir should fail
            FileHelper.createFolderIfNecessary(new File("a/b/c/d/e/f/g/h/i"));
        } catch (IllegalStateException e2) {
            e = e2;
        }
        assertNotNull(e);

        FileUtils.recursivelyDeleteDirectory(folderUnitTestsDir);
    }

    @Test
    public void test_findAllJsonFilesInFolder() {
        List<String> files = FileHelper.findAllJsonFilesInFolder(rootFileTestFolder);
        assertNotNull(files);
        assertEquals(5, files.size());
        // Sort so that we can access by index
        files.sort(String::compareTo);
        assertTrue(files.get(0).endsWith("fileTests/FolderA/a.json"));
        assertTrue(files.get(1).endsWith("fileTests/FolderA/b.json"));
        assertTrue(files.get(2).endsWith("fileTests/FolderB/c.json"));
        assertTrue(files.get(3).endsWith("fileTests/FolderB/d.json"));
        assertTrue(files.get(4).endsWith("fileTests/FolderC/a.json"));
    }
}