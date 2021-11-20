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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PathsHelperTests {

    private final Path resourceDirectory = Paths.get("src", "test", "resources");
    private final Path rootFileTestFolder = resourceDirectory.resolve("fileTests");
    private final Path folderA = rootFileTestFolder.resolve("FolderA");
    private final Path folderB = rootFileTestFolder.resolve("FolderB");
    private final Path folderUnitTestsDir = resourceDirectory.resolve("folderUnitTests");

    @Before
    public void before() throws IOException {
        PathsHelper.deleteDirectoryRecursively(folderUnitTestsDir);
    }

    @After
    public void after() throws IOException {
        PathsHelper.deleteDirectoryRecursively(folderUnitTestsDir);
    }

    @Test
    public void test_findFileContaining() throws IOException {
        Path folderToSearch = rootFileTestFolder.resolve("FolderA");
        Path file = PathsHelper.findFileContaining(folderToSearch, "a.json");
        assertNotNull(file);
        assertEquals("a.json", file.getFileName().toString());

        file = PathsHelper.findFileContaining(folderToSearch, "FolderA");
        assertNotNull(file);
        assertEquals("FolderA.zip", file.getFileName().toString());

        file = PathsHelper.findFileContaining(folderToSearch, "Decoy");
        assertNull(file); // function does not find directories

        file = PathsHelper.findFileContaining(folderToSearch, "Miss");
        assertNull(file);

        file = PathsHelper.findFileContaining(folderToSearch, "a.jsona");
        assertNull(file);
    }

    @Test
    public void test_createFolderIfNecessary() throws IOException {
        assertFalse(Files.exists(folderUnitTestsDir));
        PathsHelper.createFolderIfNecessary(folderUnitTestsDir);
        assertTrue(Files.exists(folderUnitTestsDir));
        // Check redundant folder make should succeed
        PathsHelper.createFolderIfNecessary(folderUnitTestsDir);
        assertTrue(Files.exists(folderUnitTestsDir));

        // Test exception
        NoSuchFileException e = null;
        try {
            // None of these parent folders exist, so mkdir should fail
            PathsHelper.createFolderIfNecessary(Paths.get("a/b/c/d/e/f/g/h/i"));
        } catch (NoSuchFileException e2) {
            e = e2;
        }
        assertNotNull(e);
    }

    @Test
    public void test_recursiveDeleteDirectory() throws IOException {
        PathsHelper.createFolderIfNecessary(folderUnitTestsDir);
        Path subDirA = folderUnitTestsDir.resolve("A");
        PathsHelper.createFolderIfNecessary(subDirA);
        Path subDirB = folderUnitTestsDir.resolve("B");
        PathsHelper.createFolderIfNecessary(subDirB);
        Path fileB = subDirB.resolve("B.txt");
        Files.createFile(fileB);

        PathsHelper.deleteDirectoryRecursively(folderUnitTestsDir);
        assertFalse(Files.exists(folderUnitTestsDir));
        assertFalse(Files.exists(subDirA));
        assertFalse(Files.exists(subDirB));
        assertFalse(Files.exists(fileB));
    }

    @Test
    public void test_getFilesInDirectory() throws IOException {
        List<Path> paths = PathsHelper.getFilesInDirectory(folderA);
        assertNotNull(paths);
        assertEquals(4, paths.size());
        paths.sort((o1, o2) -> o1.getFileName().toString()
                .compareTo(o2.getFileName().toString()));
        assertEquals("FolderA.zip", paths.get(0).getFileName().toString());
        assertEquals("a.json", paths.get(1).getFileName().toString());
        assertEquals("b.json", paths.get(2).getFileName().toString());
        assertEquals("e.txt", paths.get(3).getFileName().toString());

        paths = PathsHelper.getDirectoriesInDirectory(folderB);
        assertNotNull(paths);
        assertEquals(0, paths.size());
    }

    @Test
    public void test_getDirectoriesInDirectory() throws IOException {
        List<Path> paths = PathsHelper.getDirectoriesInDirectory(rootFileTestFolder);
        assertNotNull(paths);
        assertEquals(3, paths.size());
        paths.sort((o1, o2) -> o1.getFileName().toString()
                .compareTo(o2.getFileName().toString()));
        assertEquals("FolderA", paths.get(0).getFileName().toString());
        assertEquals("FolderB", paths.get(1).getFileName().toString());
        assertEquals("FolderC", paths.get(2).getFileName().toString());
    }

    @Test
    public void test_findAllJsonFilesInFolder() throws IOException {
        List<Path> files = PathsHelper.findAllJsonFilesInDirectory(rootFileTestFolder);
        assertNotNull(files);
        assertEquals(6, files.size());
        // Sort so that we can access by index
        files.sort((o1, o2) -> {
            if (o1.getFileName().toString().equals(o2.getFileName().toString())) {
                return o1.getParent().getFileName().toString().compareTo(
                        o2.getParent().getFileName().toString());
            }
            return o1.getFileName().toString().compareTo(o2.getFileName().toString());
        });
        assertEquals("a.json", files.get(0).getFileName().toString());
        assertEquals("FolderA", files.get(0).getParent().getFileName().toString());
        assertEquals("a.json", files.get(1).getFileName().toString());
        assertEquals("FolderC", files.get(1).getParent().getFileName().toString());
        assertEquals("b.json", files.get(2).getFileName().toString());
        assertEquals("c.json", files.get(3).getFileName().toString());
        assertEquals("d.json", files.get(4).getFileName().toString());
        assertEquals("z.json", files.get(5).getFileName().toString());
    }
}