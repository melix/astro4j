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
package me.champeau.a4j.jsolex.server;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This bean is used to instantiate shared context between the JSol'Ex main application and the server.
 */
@Singleton
public class SharedContext {
    private final Map<Class<?>, Object> context = new ConcurrentHashMap<>();

    public <T> T get(Class<T> type) {
        return type.cast(context.get(type));
    }

    public <T> void set(Class<T> type, T value) {
        context.put(type, value);
    }

}
