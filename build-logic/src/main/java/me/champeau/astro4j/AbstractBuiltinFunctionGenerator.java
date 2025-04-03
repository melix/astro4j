package me.champeau.astro4j;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.Property;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractBuiltinFunctionGenerator extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    public abstract DirectoryProperty getYamlDirectory();


    protected void withModels(Consumer<? super List<BuiltinFunctionModel>> modelsConsumer) throws IOException {
        var yamlDir = getYamlDirectory().getAsFile().get().toPath();
        try (var files = Files.list(yamlDir)) {
            var models = files.filter(file -> file.getFileName().toString().endsWith(".yml"))
                    .sorted(Path::compareTo)
                    .map(AbstractBuiltinFunctionGenerator::parse)
                    .toList();
            modelsConsumer.accept(models);
        }
    }

    private static BuiltinFunctionModel parse(Path file) {
        var yaml = new Yaml();
        yaml.addTypeDescription(new TypeDescription(BuiltinFunctionModel.class) {
            @Override
            public Property getProperty(String name) {
                if ("description".equals(name)) {
                    return new DescriptionProperty(name, Object.class);
                }
                return super.getProperty(name);
            }
        });
        yaml.addTypeDescription(new TypeDescription(FunctionArg.class) {
            @Override
            public Property getProperty(String name) {
                if ("description".equals(name)) {
                    return new DescriptionProperty(name, Object.class);
                }
                return super.getProperty(name);
            }
        });

        try (var reader = Files.newBufferedReader(file)) {
            return yaml.loadAs(reader, BuiltinFunctionModel.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse YAML file: " + file, e);
        }
    }

    private static class DescriptionProperty extends Property {

        public DescriptionProperty(String name, Class<?> type) {
            super(name, type);
        }

        @Override
        public Class<?>[] getActualTypeArguments() {
            return new Class[0];
        }

        @Override
        public void set(Object target, Object value) throws Exception {
            if (target instanceof Describable describable) {
                if (value instanceof String string) {
                    describable.setSingleDescription(string);
                } else if (value instanceof Map map) {
                    describable.setDescription(map);
                }
            }
        }

        @Override
        public Object get(Object o) {
            return null;
        }

        @Override
        public List<Annotation> getAnnotations() {
            return List.of();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> aClass) {
            return null;
        }
    }
}
