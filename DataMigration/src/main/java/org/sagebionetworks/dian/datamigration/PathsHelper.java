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

import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public class PathsHelper {

    private static final String MAC_DS_STORE = ".DS_Store";

    /**
     * @param directory in which we want to delete, along with all its contents
     * @throws IOException if something goes wrong deleteing a directory or file
     */
    public static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        // Files.walk - return all files/directories below rootPath including
        try (Stream<Path> walk = Files.walk(directory)) {
            // .sorted - sort the list in reverse order, so the directory
            // itself comes after the including subdirectories and files
            walk.sorted(Comparator.reverseOrder())
                    // .map - map the Path to File
                    .map(Path::toFile)
                    // .peek - is there only to show which entry is processed
                    .peek(System.out::println)
                    // .forEach - calls the .delete() method on every File object
                    .forEach(File::delete);
        }
    }

    /**
     * @param path to file containing text
     * @return the string contents of the file
     * @throws IOException if somethings goes wrong
     */
    public static String readFile(Path path) throws IOException {
        byte[] encoded = Files.readAllBytes(path);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    /**
     * @param data to write to the file
     * @param path where to save the json file
     * @throws IOException if something goes wrong writing the file
     */
    public static void writeToFile(String data, Path path) throws IOException {
        byte[] jsonBytes = data.getBytes(StandardCharsets.UTF_8);
        Files.write(path, jsonBytes);
    }

    /**
     * @param folder to create
     * @throws IllegalStateException if we could not make the folder
     */
    public static void createFolderIfNecessary(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            Files.createDirectory(folder);
        }
    }

    /**
     * @param directory containing the file to find, will NOT find directories
     * @param partOfName to look for in the filename for a match
     * @return the file if there was a match, null otherwise
     */
    public static @Nullable Path findFileContaining(
            Path directory, String partOfName) throws IOException {
        for (Path file: getFilesInDirectory(directory)) {
            if (file.getFileName().toString().contains(partOfName)) {
                return file;
            }
        }
        return null;
    }

    /**
     * On Mac OSX, .DS_Store will be omitted
     * @param directory to search for files
     * @return a list of files within the directory
     * @throws IOException if something goes wrong accessing the directory's files
     */
    public static List<Path> getFilesInDirectory(Path directory) throws IOException {
        List<Path> pathList = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (!Files.isDirectory(path) &&
                    !MAC_DS_STORE.equals(path.getFileName().toString())) {
                    pathList.add(path);
                }
            }
        }
        return pathList;
    }

    /**
     * @param directory to search for files
     * @return a list of files within the directory
     * @throws IOException if something goes wrong accessing the directory's files
     */
    public static List<Path> getDirectoriesInDirectory(Path directory) throws IOException {
        List<Path> dirList = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    dirList.add(path);
                }
            }
        }
        return dirList;
    }

    /**
     * @param directory folder containing json files,
     *                  and/or a list of directories, that also contain JSON files
     * @return a list of the file paths to all the JSON files in the extractedFolder
     */
    public static List<Path> findAllJsonFilesInDirectory(
            @NonNull Path directory) throws IOException {
        List<Path> jsonFileList = new ArrayList<>();

        // Find all JSON files within this directory
        for (Path file : getFilesInDirectory(directory)) {
            // Ignore everything but JSON files
            if (file.getFileName().toString().endsWith(".json")) {
                jsonFileList.add(file);
            }
        }

        // Find all JSON files within nested directories
        for (Path dir : getDirectoriesInDirectory(directory)) {
            for (Path file : getFilesInDirectory(dir)) {
                // Ignore everything but JSON files
                if (file.getFileName().toString().endsWith(".json")) {
                    jsonFileList.add(file);
                }
            }
        }
        return jsonFileList;
    }
}
