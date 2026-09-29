package com.stremio.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

public abstract class VerifyArm64ApkPackaging extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getApkDirectory();

    @TaskAction
    public void verify() {
        File directory = getApkDirectory().get().getAsFile();
        File[] matchingApks = directory.listFiles(file ->
                file.isFile() && file.getName().contains("arm64-v8a") && file.getName().endsWith("debug.apk"));
        if (matchingApks == null || matchingApks.length != 1) {
            throw new GradleException("Expected exactly one arm64-v8a debug APK in " + directory.getAbsolutePath());
        }

        File apk = matchingApks[0];
        try (ZipFile zip = new ZipFile(apk)) {
            for (String entry : new String[]{
                    "lib/arm64-v8a/libstream_server.so",
                    "lib/arm64-v8a/libc++_shared.so"
            }) {
                if (zip.getEntry(entry) == null) {
                    throw new GradleException(apk.getName() + " is missing required native entry: " + entry);
                }
            }
        } catch (IOException error) {
            throw new GradleException("Could not inspect the arm64 debug APK ZIP: " + apk.getName(), error);
        }
        getLogger().lifecycle("Verified {} contains stream_server and libc++ for arm64-v8a", apk.getAbsolutePath());
    }
}
