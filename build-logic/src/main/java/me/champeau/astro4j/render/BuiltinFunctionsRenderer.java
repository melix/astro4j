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
        var documentationMethods = generateDocumentationMethods(functions);
        return template.replace("%FUNCTIONS%", generatedFunctions)
                      .replace("%DOCUMENTATION_METHODS%", documentationMethods);
    }

    private String renderFunction(BuiltinFunctionModel fun) {
        var sb = new StringBuilder();
        sb.append(fun.getName()).append("(");
        if (fun.isHasSideEffect() || !fun.isConcurrent()) {
            sb.append(fun.isHasSideEffect()).append(", ");
            sb.append(fun.isConcurrent()).append(", ");
            sb.append("List.of(");
        }
        if (fun.isSpreadList()) {
            sb.append("Parameter.SPREAD_LIST");
        } else {
            sb.append(fun.getArguments().stream().map(arg -> (arg.isRequired() ? "req(" : "opt(") + '"' + arg.getName() + "\")").collect(Collectors.joining(", ")));
        }
        sb.append(")");
        if (fun.isHasSideEffect() || !fun.isConcurrent()) {
            sb.append(")");
        }
        return sb.toString();
    }

    private String generateDocumentationMethods(List<BuiltinFunctionModel> functions) {
        var sb = new StringBuilder();
        sb.append("\n    public String getDocumentation(String locale) {\n");
        sb.append("        return switch (this) {\n");
        
        for (var fun : functions.stream().sorted(Comparator.comparing(BuiltinFunctionModel::getName)).toList()) {
            sb.append("            case ").append(fun.getName()).append(" -> ");
            sb.append("getDocumentation_").append(fun.getName().toLowerCase()).append("(locale);\n");
        }
        
        sb.append("        };\n");
        sb.append("    }\n\n");
        
        sb.append("    public String getDocumentation() {\n");
        sb.append("        return getDocumentation(\"en\");\n");
        sb.append("    }\n\n");
        
        sb.append("    public java.util.List<String> getExamples(String locale) {\n");
        sb.append("        return switch (this) {\n");
        
        for (var fun : functions.stream().sorted(Comparator.comparing(BuiltinFunctionModel::getName)).toList()) {
            sb.append("            case ").append(fun.getName()).append(" -> ");
            sb.append("getExamples_").append(fun.getName().toLowerCase()).append("(locale);\n");
        }
        
        sb.append("        };\n");
        sb.append("    }\n\n");
        
        sb.append("    public java.util.List<String> getExamples() {\n");
        sb.append("        return getExamples(\"en\");\n");
        sb.append("    }\n\n");
        
        sb.append("    public java.util.List<FunctionParameter> getParameterInfo() {\n");
        sb.append("        return switch (this) {\n");
        
        for (var fun : functions.stream().sorted(Comparator.comparing(BuiltinFunctionModel::getName)).toList()) {
            sb.append("            case ").append(fun.getName()).append(" -> ");
            sb.append("getParameterInfo_").append(fun.getName().toLowerCase()).append("();\n");
        }
        
        sb.append("        };\n");
        sb.append("    }\n");
        
        // Generate individual methods for each function
        for (var fun : functions.stream().sorted(Comparator.comparing(BuiltinFunctionModel::getName)).toList()) {
            // Documentation method
            sb.append("\n    private static String getDocumentation_").append(fun.getName().toLowerCase()).append("(String locale) {\n");
            sb.append("        return switch (locale) {\n");
            
            if (fun.getDescription() != null) {
                fun.getDescription().forEach((lang, desc) -> {
                    sb.append("            case \"").append(lang).append("\" -> \"");
                    sb.append(escapeJavaString(desc));
                    sb.append("\";\n");
                });
            }
            
            sb.append("            default -> getDocumentation_").append(fun.getName().toLowerCase()).append("(\"en\");\n");
            sb.append("        };\n");
            sb.append("    }\n");
            
            // Examples method
            sb.append("\n    private static java.util.List<String> getExamples_").append(fun.getName().toLowerCase()).append("(String locale) {\n");
            if (fun.getExamples() != null && !fun.getExamples().isEmpty()) {
                sb.append("        return java.util.List.of(");
                sb.append(fun.getExamples().stream()
                    .map(example -> "\"" + escapeJavaString(example) + "\"")
                    .collect(Collectors.joining(", ")));
                sb.append(");\n");
            } else {
                sb.append("        return java.util.List.of();\n");
            }
            sb.append("    }\n");
            
            // Parameter info method
            sb.append("\n    private static java.util.List<FunctionParameter> getParameterInfo_").append(fun.getName().toLowerCase()).append("() {\n");
            if (fun.getArguments() != null && !fun.getArguments().isEmpty()) {
                sb.append("        return java.util.List.of(");
                sb.append(fun.getArguments().stream()
                    .map(arg -> {
                        var paramBuilder = new StringBuilder();
                        paramBuilder.append("new FunctionParameter(\"").append(escapeJavaString(arg.getName())).append("\", ");
                        paramBuilder.append(!arg.isRequired()).append(", ");
                        if (arg.getDescription() != null) {
                            paramBuilder.append("java.util.Map.of(");
                            paramBuilder.append(arg.getDescription().entrySet().stream()
                                .map(entry -> "\"" + entry.getKey() + "\", \"" + escapeJavaString(entry.getValue()) + "\"")
                                .collect(Collectors.joining(", ")));
                            paramBuilder.append(")");
                        } else {
                            paramBuilder.append("java.util.Map.of()");
                        }
                        paramBuilder.append(")");
                        return paramBuilder.toString();
                    })
                    .collect(Collectors.joining(",\n            ")));
                sb.append(");\n");
            } else {
                sb.append("        return java.util.List.of();\n");
            }
            sb.append("    }\n");
        }
        
        return sb.toString();
    }

    private String escapeJavaString(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    private String readTemplate() {
        try (var is = BuiltinFunctionsRenderer.class.getResourceAsStream("BuiltinFunction.java")) {
            return new String(is.readAllBytes(), "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
