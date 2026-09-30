package com.stremio.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public abstract class PrepareStreamServerJni extends DefaultTask {
    @Input
    public abstract org.gradle.api.provider.Property<String> getAbi();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getNativeLibrary();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void prepare() {
        var source = getNativeLibrary().get().getAsFile().toPath();
        var abi = getAbi().get();
        var destinationDirectory = getOutputDirectory().get().dir(abi).getAsFile().toPath();
        var destination = destinationDirectory.resolve("libstream_server.so");
        try {
            Files.createDirectories(destinationDirectory);
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException error) {
            throw new GradleException("Could not stage the generated stream-server " + abi + " JNI library.", error);
        }
    }
}
