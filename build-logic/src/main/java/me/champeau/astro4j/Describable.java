package me.champeau.astro4j;

import java.util.Map;

public interface Describable {
    default void setSingleDescription(String description) {
        setDescription(Map.of("en", description, "fr", description));
    }

    void setDescription(Map<String, String> langToDescription);
}
