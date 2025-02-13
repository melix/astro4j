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

import io.micronaut.context.ApplicationContext;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

import static me.champeau.a4j.jsolex.server.AbstractController.localized;

public class JSolexServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(JSolexServer.class);

    public static void main(String[] args) {
        Micronaut.run(JSolexServer.class);
    }

    public static List<String> getServerUrls(EmbeddedServer server) {
        var port = server.getPort();
        try {
            var localHost = InetAddress.getLocalHost();
            return Arrays.stream(InetAddress.getAllByName(localHost.getHostName()))
                .map(address -> "http://" + address.getHostAddress() + ":" + port)
                .toList();
        } catch (UnknownHostException e) {
        }
        return List.of();
    }

    public static ApplicationContext start(int port, ProcessParams processParams) {
        var server = ApplicationContext.builder()
            .mainClass(JSolexServer.class)
            .args("-Dmicronaut.server.port=" + port)
            .banner(false)
            .start();
        var embeddedServer = server.getBean(EmbeddedServer.class);
        embeddedServer.start();
        server.getBean(SharedContext.class).set(ProcessParams.class, processParams);
        var urls = getServerUrls(embeddedServer);
        if (!urls.isEmpty()) {
            LOGGER.info(String.format(localized("server.started.at"), urls.getFirst()));
        }
        return server;
    }
}
