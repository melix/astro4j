/*
 * Copyright 2025-2025 the original author or authors.
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
/** Module for the JSol'Ex embedded web server. */
module me.champeau.a4j.jsolex.server {
    requires me.champeau.a4j.jsolex.core;
    requires io.micronaut.micronaut_context;
    requires io.micronaut.micronaut_core;
    requires io.micronaut.micronaut_http;
    requires io.micronaut.views.micronaut_views_core;
    requires io.micronaut.views.micronaut_views_thymeleaf;
    requires io.micronaut.views.micronaut_views_htmx;
    requires io.micronaut.serde.micronaut_serde_api;
    requires io.micronaut.serde.micronaut_serde_bson;
    requires io.micronaut.micronaut_websocket;
    requires io.micronaut.micronaut_http_server_netty;
    requires io.netty.handler;
    requires io.netty.codec.compression;
    requires io.netty.codec.http;
    requires io.netty.codec.http2;
    requires jakarta.annotation;
    requires jakarta.inject;
    requires io.micronaut.micronaut_inject;

    // For Thymeleaf!!!
    requires java.sql;

    exports me.champeau.a4j.jsolex.server;
    opens views;
    opens _static.css;
    opens _static.img;
    opens _static.js;
}
