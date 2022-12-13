package org.sagebionetworks.dian.datamigration;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class FileLogger {

    private BufferedWriter writer = null;
    private String fileName = "schedule_migration_logs.txt";
    private File outFile = new File(fileName);

    public void openFile() throws IOException {
        writer = new BufferedWriter(new FileWriter(fileName, false));
    }

    public void write(String log) throws IOException {
        System.out.println(log);
        writer.write(log + "\n");
    }

    public File closeFile() throws IOException {
        writer.close();
        return outFile;
    }
}
