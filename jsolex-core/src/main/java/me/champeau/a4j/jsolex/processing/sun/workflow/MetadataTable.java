package me.champeau.a4j.jsolex.processing.sun.workflow;

import java.util.Map;
import java.util.Optional;

public record MetadataTable(
    Map<String, String> properties
) {
    public static final String FILE_NAME = "fileName";

    public Optional<String> get(String key) {
        return Optional.ofNullable(properties.get(key));
    }
}
