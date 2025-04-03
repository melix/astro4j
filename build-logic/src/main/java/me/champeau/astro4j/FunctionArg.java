package me.champeau.astro4j;

import java.util.Map;

public class FunctionArg implements Describable {
    private String name;
    private boolean required = true;
    private String defaultValue;
    private Map<String, String> description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void setOptional(boolean optional) {
        this.required = !optional;
    }

    public boolean isOptional() {
        return !required;
    }

    public String getDefault() {
        return defaultValue;
    }

    public void setDefault(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

}
