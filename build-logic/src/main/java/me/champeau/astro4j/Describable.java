package me.champeau.astro4j;

import java.util.LinkedHashMap;
import java.util.Map;

public interface Describable {
    default void setSingleDescription(String description) {
        var descriptions = new LinkedHashMap<String, String>();
        descriptions.put("en", description);
        descriptions.put("fr", description);
        setDescription(descriptions);
    }

    void setDescription(Map<String, String> langToDescription);
}
