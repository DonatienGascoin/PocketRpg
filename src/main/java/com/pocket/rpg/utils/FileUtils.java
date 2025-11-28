package com.pocket.rpg.utils;

import com.google.gson.Gson;
import com.pocket.rpg.serialization.Serializer;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.text.StringEscapeUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class FileUtils {

    private static final String OS = System.getProperty("os.name");
    private static final String WINDOWS = "Windows";

    // ====================================
    // File Reading
    // ====================================

    public static String readFile(File file) {
        String inFile = "";
        try {
            file = new File(getFileNameOsIndependent(file.getAbsolutePath()));
            inFile = new String(Files.readAllBytes(file.toPath()));
        } catch (IOException e) {
            throw new RuntimeException("Impossible to read file '" + file.getAbsolutePath() + "'", e);
        }
        return inFile;
    }

    public static <T> T readFileAndDeserialize(File file, Class<T> clazz) {
        Gson gson = Serializer.getPrettyPrintGson();
        return gson.fromJson(readFile(file), clazz);
    }
    // ====================================
    // File Writing
    // ====================================

    public static void writeToFile(File file, String content) {
        try {
            Files.createDirectories(Paths.get(file.getAbsolutePath()).getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (var fileWriter = new FileWriter(file.getAbsolutePath())) {
            fileWriter.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeToFile(String fileName, String content) {
        writeToFile(new File(fileName), content);
    }

    public static void serializeAndWriteToFile(File file, Object content) {
        Gson gson = Serializer.getPrettyPrintGson();
        writeToFile(file, gson.toJson(content));
    }

    public static void serializeAndWriteToFile(String fileName, Object content) {
        Gson gson = Serializer.getPrettyPrintGson();
        writeToFile(fileName, gson.toJson(content));
    }

    // ====================================
    // Tools
    // ====================================

    private static String getFileNameOsIndependent(String path) {
        return StringEscapeUtils.escapeJava(convertPathToCurrentOs(path));
    }

    private static String convertPathToCurrentOs(String filePath) {
        if (OS.contains(WINDOWS)) {
            return FilenameUtils.separatorsToWindows(filePath);
        }
        return FilenameUtils.separatorsToUnix(filePath);
    }
}
