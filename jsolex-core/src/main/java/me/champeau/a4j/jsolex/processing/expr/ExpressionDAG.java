/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.expr.ast.Assignment;
import me.champeau.a4j.jsolex.processing.expr.ExpressionDependencyAnalyzer.DependencyInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExpressionDAG {
    private final Map<String, DependencyInfo> nodes;
    private final Map<String, Set<String>> adjacencyList;
    private final Map<String, Integer> inDegree;

    public ExpressionDAG() {
        this.nodes = new HashMap<>();
        this.adjacencyList = new HashMap<>();
        this.inDegree = new HashMap<>();
    }

    public void addNode(DependencyInfo info) {
        var varName = info.variableName();
        nodes.put(varName, info);
        adjacencyList.putIfAbsent(varName, new HashSet<>());
        inDegree.putIfAbsent(varName, 0);

        for (var dependency : info.dependencies()) {
            if (nodes.containsKey(dependency)) {
                adjacencyList.get(dependency).add(varName);
                inDegree.merge(varName, 1, Integer::sum);
            }
        }
    }

    public List<ExecutionLevel> computeExecutionLevels() {
        var levels = new ArrayList<ExecutionLevel>();
        var remainingInDegree = new HashMap<>(inDegree);
        var processed = new HashSet<String>();

        while (processed.size() < nodes.size()) {
            var currentLevel = new ArrayList<DependencyInfo>();
            var parallelizable = new ArrayList<DependencyInfo>();
            var sequential = new ArrayList<DependencyInfo>();

            for (var entry : nodes.entrySet()) {
                var varName = entry.getKey();
                var info = entry.getValue();

                if (!processed.contains(varName) && remainingInDegree.get(varName) == 0) {
                    currentLevel.add(info);

                    if (info.hasFunctionCall() && !info.hasStatefulFunction() && !info.hasNonConcurrentFunction()) {
                        parallelizable.add(info);
                    } else {
                        sequential.add(info);
                    }
                }
            }

            if (currentLevel.isEmpty()) {
                var remaining = nodes.keySet().stream()
                        .filter(v -> !processed.contains(v))
                        .toList();
                throw new IllegalStateException("Circular dependency detected among variables: " + remaining);
            }

            if (!parallelizable.isEmpty()) {
                levels.add(new ExecutionLevel(parallelizable, true));
            }

            for (var info : sequential) {
                levels.add(new ExecutionLevel(List.of(info), false));
            }

            for (var info : currentLevel) {
                var varName = info.variableName();
                processed.add(varName);

                for (var dependent : adjacencyList.get(varName)) {
                    remainingInDegree.merge(dependent, -1, Integer::sum);
                }
            }
        }

        return levels;
    }

    public record ExecutionLevel(
            List<DependencyInfo> expressions,
            boolean canRunInParallel
    ) {
        public List<Assignment> assignments() {
            return expressions.stream()
                    .map(DependencyInfo::assignment)
                    .toList();
        }
    }

    public static ExpressionDAG build(List<DependencyInfo> dependencyInfos) {
        var dag = new ExpressionDAG();
        for (var info : dependencyInfos) {
            dag.addNode(info);
        }
        return dag;
    }

    public String toDotFormat() {
        var sb = new StringBuilder();
        sb.append("digraph ExpressionDAG {\n");
        sb.append("  rankdir=TB;\n");
        sb.append("  newrank=true;\n");
        sb.append("  node [shape=box, style=rounded];\n\n");

        for (var info : nodes.values()) {
            var varName = info.variableName();
            var color = info.hasStatefulFunction() ? "red" :
                       info.hasNonConcurrentFunction() ? "orange" :
                       info.hasFunctionCall() ? "lightblue" : "lightgray";
            var shape = info.hasFunctionCall() ? "box" : "ellipse";

            var expression = info.assignment() != null ?
                    info.assignment().expression().toString() : "?";

            var escapedExpression = escapeHtml(expression);
            var escapedVarName = escapeHtml(varName);
            var escapedSectionName = escapeHtml(info.sectionName());

            var label = new StringBuilder();
            label.append("<<TABLE BORDER=\"0\" CELLBORDER=\"0\" CELLSPACING=\"0\">");
            label.append("<TR><TD><B>").append(escapedVarName).append("</B></TD></TR>");
            label.append("<TR><TD><FONT POINT-SIZE=\"10\">").append(escapedExpression).append("</FONT></TD></TR>");

            if (!info.sectionName().isEmpty()) {
                label.append("<TR><TD><FONT POINT-SIZE=\"8\" COLOR=\"gray\">[")
                     .append(escapedSectionName).append("]</FONT></TD></TR>");
            }

            if (info.hasParallelFunctionArguments()) {
                label.append("<TR><TD><FONT POINT-SIZE=\"8\" COLOR=\"blue\">⚡ parallel args</FONT></TD></TR>");
            }
            if (info.hasNonConcurrentFunction()) {
                label.append("<TR><TD><FONT POINT-SIZE=\"8\" COLOR=\"red\">🚫 blocking</FONT></TD></TR>");
            }

            label.append("</TABLE>>");

            var style = info.hasParallelFunctionArguments() ?
                    "filled,rounded,bold" : "filled,rounded";
            var peripheries = info.hasParallelFunctionArguments() ? 2 : 1;
            sb.append(String.format("  \"%s\" [label=%s, fillcolor=%s, style=\"%s\", shape=%s, peripheries=%d];\n",
                    varName, label, color, style, shape, peripheries));
        }

        sb.append("\n");

        for (var entry : adjacencyList.entrySet()) {
            var from = entry.getKey();
            for (var to : entry.getValue()) {
                sb.append(String.format("  \"%s\" -> \"%s\";\n", to, from));
            }
        }

        var levels = computeExecutionLevels();
        for (int i = 0; i < levels.size(); i++) {
            var level = levels.get(i);
            var levelVars = level.expressions().stream()
                    .map(DependencyInfo::variableName)
                    .toList();

            if (level.canRunInParallel() && levelVars.size() > 1) {
                sb.append("  { rank=same; ");
                for (var varName : levelVars) {
                    sb.append(String.format("\"%s\"; ", varName));
                }
                sb.append("}\n");
            }
        }

        sb.append("  \"legend_title\" [label=<<B>Legend</B>>, shape=plaintext];\n");
        sb.append("  \"legend_red\" [label=\"Stateful Function\", fillcolor=red, style=\"filled,rounded\", shape=box];\n");
        sb.append("  \"legend_orange\" [label=\"Non-Concurrent (Blocking)\", fillcolor=orange, style=\"filled,rounded\", shape=box];\n");
        sb.append("  \"legend_blue\" [label=\"Function Call\", fillcolor=lightblue, style=\"filled,rounded\", shape=box];\n");
        sb.append("  \"legend_gray\" [label=\"Simple Expression\", fillcolor=lightgray, style=\"filled,rounded\", shape=ellipse];\n");
        sb.append("  \"legend_parallel\" [label=\"Parallel Arguments\", fillcolor=lightblue, style=\"filled,rounded,bold\", shape=box, peripheries=2];\n");
        sb.append("  { rank=max; \"legend_title\"; \"legend_red\"; \"legend_orange\"; \"legend_blue\"; \"legend_gray\"; \"legend_parallel\"; }\n");
        sb.append("  \"legend_title\" -> \"legend_red\" -> \"legend_orange\" -> \"legend_blue\" -> \"legend_gray\" -> \"legend_parallel\" [style=invis];\n");

        if (!levels.isEmpty()) {
            var firstLevel = levels.getFirst();
            var firstLevelNodes = firstLevel.expressions().stream()
                    .map(DependencyInfo::variableName)
                    .toList();
            if (!firstLevelNodes.isEmpty()) {
                for (var node : firstLevelNodes) {
                    sb.append(String.format("  \"%s\" -> \"legend_title\" [style=invis, weight=10];\n", node));
                }
            }
        }

        sb.append("}\n");
        return sb.toString();
    }

    private String escapeHtml(String str) {
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("\n", "<BR/>")
                  .replace("\r", "");
    }

    public String toDebugString() {
        var sb = new StringBuilder();
        sb.append("=== Expression Dependency Graph ===\n\n");

        sb.append("Nodes:\n");
        for (var entry : nodes.entrySet()) {
            var varName = entry.getKey();
            var info = entry.getValue();
            sb.append(String.format("  %s: %s%s%s\n",
                    varName,
                    info.assignment() != null ? info.assignment().expression() : "?",
                    info.hasStatefulFunction() ? " [STATEFUL]" : "",
                    info.hasFunctionCall() ? " [FUNC]" : ""));
        }

        sb.append("\nDependencies:\n");
        for (var entry : nodes.entrySet()) {
            var varName = entry.getKey();
            var deps = entry.getValue().dependencies();
            if (!deps.isEmpty()) {
                sb.append(String.format("  %s depends on: %s\n", varName, deps));
            }
        }

        sb.append("\nExecution Levels:\n");
        var levels = computeExecutionLevels();
        for (int i = 0; i < levels.size(); i++) {
            var level = levels.get(i);
            var levelVars = level.expressions().stream()
                    .map(DependencyInfo::variableName)
                    .toList();
            sb.append(String.format("  Level %d (%s): %s\n",
                    i, level.canRunInParallel() ? "PARALLEL" : "SEQUENTIAL", levelVars));
        }

        return sb.toString();
    }
}
