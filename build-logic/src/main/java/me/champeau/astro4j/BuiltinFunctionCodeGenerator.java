package me.champeau.astro4j;

import me.champeau.astro4j.render.BuiltinFunctionsRenderer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A task which parses the built-in functions YAML files and generates
 * Java code.
 */
@CacheableTask
public abstract class BuiltinFunctionCodeGenerator extends AbstractBuiltinFunctionGenerator {

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedSourcesDirectory();

    @TaskAction
    public void generate() throws IOException {
        var generatedSourcesDir = getGeneratedSourcesDirectory().getAsFile().get().toPath();
        var pkgDir = generatedSourcesDir.resolve("me/champeau/a4j/jsolex/expr");
        Files.createDirectories(pkgDir);
        withModels(models -> {
            try (var writer = Files.newBufferedWriter(pkgDir.resolve("BuiltinFunction.java"), StandardCharsets.UTF_8)) {
                writer.write(new BuiltinFunctionsRenderer().render(models));

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}