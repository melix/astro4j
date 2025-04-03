package me.champeau.astro4j.render;

import me.champeau.astro4j.BuiltinFunctionModel;

import java.util.Arrays;
import java.util.ResourceBundle;

public class FunctionAdocRenderer implements Renderer<BuiltinFunctionModel> {

    private final ResourceBundle translations;
    private final String lang;

    public FunctionAdocRenderer(ResourceBundle translations, String lang) {
        this.translations = translations;
        this.lang = lang;
    }

    @Override
    public String render(BuiltinFunctionModel fun) {
        var sb = new StringBuilder();
        sb.append(fun.getDescription().get(lang)).append("\n");
        sb.append("[cols=\"15%,15%,65%,15%\"]\n");
        sb.append("|===\n");
        sb.append("|").append(translations.getString("arguments")).append("\n");
        sb.append("|").append(translations.getString("required")).append("\n");
        sb.append("|").append(translations.getString("description")).append("\n");
        sb.append("|").append(translations.getString("default")).append("\n");
        for (var arg : fun.getArguments()) {
            sb.append("|`").append(arg.getName()).append("`\n");
            sb.append("^|").append(arg.isRequired()?"icon:check[]":"").append("\n");
            var description = arg.getDescription();
            if (description != null) {
                sb.append("|").append(description.get(lang)).append("\n");
            } else {
                sb.append("|").append("\n");
            }
            sb.append("|").append(arg.getDefault() == null ? "" : arg.getDefault()).append("\n");
        }
        sb.append("4+a|");
        if (fun.getExtraDocs() != null) {
            Arrays.stream(fun.getExtraDocs().get(lang).split("\n"))
                    .map(String::stripIndent)
                    .forEach(s -> sb.append(s).append("\n"));
            sb.append("\n");
        }
        sb.append("\n");
        sb.append("|===\n");
        sb.append("\n");
        sb.append("**").append(translations.getString("examples")).append("**\n");
        for (var ex : fun.getExamples()) {
            sb.append("[listing]\n");
            sb.append("----\n");
            sb.append(ex).append("\n");
            sb.append("----\n");
        }
        return sb.toString();
    }
}
