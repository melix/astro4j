/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.astro4j;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

@CacheableTask
public abstract class CongoCCTask extends DefaultTask {

    @InputDirectory
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getGrammarDirectory();

    @Input
    public abstract Property<String> getGrammarFile();

    @Input
    public abstract ListProperty<String> getOptions();

    @Input
    public abstract Property<Integer> getJdkVersion();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getCongoCCJar();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void generateSources() throws IOException {
        getFileSystemOperations().delete(spec -> spec.delete(getOutputDirectory()));
        Files.createDirectories(getOutputDirectory().get().getAsFile().toPath());
        getExecOperations().javaexec(spec -> {
            spec.classpath(getCongoCCJar());
            spec.workingDir(getGrammarDirectory().get().getAsFile());
            spec.getMainClass().set("org.congocc.app.Main");
            spec.args(getOptions().get());
            spec.args("-jdk" + getJdkVersion().get(), "-d", getOutputDirectory().get().getAsFile().getAbsolutePath(), getGrammarFile().get());
        });
    }
}
