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
package me.champeau.a4j.jsolex.processing.expr.impl;

import com.google.gson.JsonParser;
import me.champeau.a4j.jsolex.expr.ExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteScriptGen extends AbstractFunctionImpl {
    private final AbstractImageExpressionEvaluator evaluator;

    public RemoteScriptGen(AbstractImageExpressionEvaluator evaluator, Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
        this.evaluator = evaluator;
    }

    public Object callRemoteScriptGen(List<Object> arguments) {
        if (arguments.size() != 1) {
            throw new IllegalArgumentException("remote_scriptgen() accept a single argument (remote url)");
        }
        var scriptUrl = arguments.getFirst().toString();
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(scriptUrl))
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(evaluator.exportAsJson()))
                    .build();
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                var body = response.body();
                var json = JsonParser.parseString(body);
                if (json.isJsonObject()) {
                    var obj = json.getAsJsonObject();
                    if (obj.has("error")) {
                        throw new IllegalStateException(obj.get("error").getAsString());
                    }
                    var script = json.getAsJsonObject().get("script");
                    if (script != null) {
                        var userFunctions = evaluator.getUserFunctions();
                        var executor = new DefaultImageScriptExecutor(
                                evaluator::findImage,
                                new HashMap<>(context),
                                broadcaster
                        ) {
                            @Override
                            protected void populateContext(AbstractImageExpressionEvaluator evaluator) {
                                super.populateContext(evaluator);
                                userFunctions.forEach(evaluator::putFunction);
                            }
                        };
                        executor.disableOutputLogging();
                        var result = executor.execute(script.getAsString(), ImageMathScriptExecutor.SectionKind.SINGLE);
                        var keys = Stream.concat(
                                result.imagesByLabel().keySet().stream(),
                                result.filesByLabel().keySet().stream()
                        ).collect(Collectors.toSet());
                        Map<String, Object> collectedVariables = executor.getVariables()
                                .entrySet()
                                .stream()
                                .filter(e -> keys.contains(e.getKey()))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        return new ExpressionEvaluator.NestedInvocationResult(collectedVariables);
                    }
                }
                return null;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
