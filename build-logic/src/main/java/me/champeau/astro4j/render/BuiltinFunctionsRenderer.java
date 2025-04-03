package me.champeau.astro4j.render;

import me.champeau.astro4j.BuiltinFunctionModel;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class BuiltinFunctionsRenderer implements Renderer<List<BuiltinFunctionModel>> {
    @Override
    public String render(List<BuiltinFunctionModel> functions) {
        var template = readTemplate();
        var generatedFunctions = functions.stream()
                .sorted(Comparator.comparing(BuiltinFunctionModel::getName))
                .map(this::renderFunction)
                .map(s -> "    " + s)
                .collect(Collectors.joining(",\n"));
        return template.replace("%FUNCTIONS%", generatedFunctions);
    }

    private String renderFunction(BuiltinFunctionModel fun) {
        var sb = new StringBuilder();
        sb.append(fun.getName()).append("(");
        if (fun.isHasSideEffect()) {
            sb.append("true, List.of(");
        }
        if (fun.isSpreadList()) {
            sb.append("Parameter.SPREAD_LIST");
        } else {
            sb.append(fun.getArguments().stream().map(arg -> (arg.isRequired() ? "req(" : "opt(") + '"' + arg.getName() + "\")").collect(Collectors.joining(", ")));
        }
        sb.append(")");
        if (fun.isHasSideEffect()) {
            sb.append(")");
        }
        return sb.toString();
    }

    private String readTemplate() {
        try (var is = BuiltinFunctionsRenderer.class.getResourceAsStream("BuiltinFunction.java")) {
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
