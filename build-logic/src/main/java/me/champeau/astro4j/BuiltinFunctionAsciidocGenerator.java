package me.champeau.astro4j;

import me.champeau.astro4j.render.FunctionAdocRenderer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * A task which parses the built-in functions YAML files and generates
 * Java code.
 */
@CacheableTask
public abstract class BuiltinFunctionAsciidocGenerator extends AbstractBuiltinFunctionGenerator {

    private static final Map<String, ResourceBundle> BUNDLES = Map.of(
            "en", ResourceBundle.getBundle("asciidoc", Locale.ENGLISH),
            "fr", ResourceBundle.getBundle("asciidoc", Locale.FRENCH)
    );

    @OutputDirectory
    public abstract DirectoryProperty getGeneratedDocsDirectory();

    @TaskAction
    public void generate() throws IOException {
        var docsDir = getGeneratedDocsDirectory().getAsFile().get().toPath();
        withModels(models -> {
            try {
                for (var lang : List.of("fr", "en")) {
                    var langDir = docsDir.resolve(lang);
                    Files.createDirectories(langDir);
                    for (var model : models) {
                        try (var writer = Files.newBufferedWriter(langDir.resolve(model.getName().toLowerCase(Locale.US) + ".adoc"), StandardCharsets.UTF_8)) {
                            writer.write(new FunctionAdocRenderer(BUNDLES.get(lang), lang).render(model));
                        }
                    }

                    try (var writer = new PrintWriter(Files.newBufferedWriter(langDir.resolve("functions.adoc"), StandardCharsets.UTF_8))) {
                        // generate aggregated file
                        for (var category : DocCategory.values()) {
                            writer.println("== " + BUNDLES.get(lang).getString("DocCategory." + category));
                            writer.println();
                            for (var model : models) {
                                if (model.getCategory() == category) {
                                    writer.println("=== " + model.getName());
                                    writer.println("include::" + model.getName().toLowerCase(Locale.US) + ".adoc[]");
                                    writer.println();
                                }
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}