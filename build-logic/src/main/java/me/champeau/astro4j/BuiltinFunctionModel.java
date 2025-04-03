package me.champeau.astro4j;

import java.util.List;
import java.util.Map;

public class BuiltinFunctionModel implements Describable {
    private String name;
    private DocCategory category;
    private boolean hasSideEffect;
    boolean isSpreadList;
    private List<FunctionArg> arguments = List.of();
    private Map<String, String> description;
    private List<String> examples;
    private Map<String, String> extraDocs;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DocCategory getCategory() {
        return category;
    }

    public void setCategory(DocCategory category) {
        this.category = category;
    }

    public List<FunctionArg> getArguments() {
        return arguments;
    }

    public void setArguments(List<FunctionArg> arguments) {
        this.arguments = arguments;
    }

    public boolean isSpreadList() {
        return isSpreadList;
    }

    public void setSpreadList(boolean spreadList) {
        isSpreadList = spreadList;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

    public List<String> getExamples() {
        return examples;
    }

    public void setExamples(List<String> examples) {
        this.examples = examples;
    }

    public boolean isHasSideEffect() {
        return hasSideEffect;
    }

    public void setHasSideEffect(boolean hasSideEffect) {
        this.hasSideEffect = hasSideEffect;
    }

    public Map<String, String> getExtraDocs() {
        return extraDocs;
    }

    public void setExtraDocs(Map<String, String> extraDocs) {
        this.extraDocs = extraDocs;
    }
}
