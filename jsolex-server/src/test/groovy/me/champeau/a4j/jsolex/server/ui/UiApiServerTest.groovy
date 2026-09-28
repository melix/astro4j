/*
 * Copyright 2026-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.server.ui

import groovy.json.JsonSlurper
import io.micronaut.context.ApplicationContext
import me.champeau.a4j.jsolex.server.MainController
import me.champeau.a4j.jsolex.server.MainWebSocket
import me.champeau.a4j.jsolex.server.SpectrumController
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

class UiApiServerTest extends Specification {
    private static final UiBounds BOUNDS = new UiBounds(10, 20, 300, 200)
    private static final UiNode ROOT = new UiNode("", "root", "BorderPane", null, ["root"], false, false, null, BOUNDS, BOUNDS, [
        new UiNode("0", "processButton", "Button", "Process", ["button"], false, true, null, BOUNDS, BOUNDS, [])
    ])

    @Shared
    FakeInspector inspector = new FakeInspector()

    @Shared
    @AutoCleanup("stop")
    UiApiServer server = UiApiServer.start(0, inspector)

    @Shared
    HttpClient client = HttpClient.newHttpClient()

    def setup() {
        inspector.reset()
    }

    def "lists windows"() {
        when:
        def response = get("/ui/windows")

        then:
        response.statusCode() == 200
        response.headers().firstValue("Content-Type").get().startsWith("application/json")
        def json = parse(response)
        json.size() == 1
        json[0].id == "w1"
        json[0].title == "JSol'Ex"
        json[0].bounds.width == 300
    }

    def "returns the tree of a window"() {
        when:
        def response = get("/ui/tree?window=w1&depth=2")

        then:
        response.statusCode() == 200
        inspector.lastWindow == "w1"
        inspector.lastDepth == 2
        def json = parse(response)
        json.ref == ""
        json.text == null
        json.children[0].ref == "0"
        json.children[0].id == "processButton"
        json.children[0].children == []
    }

    def "tree depth defaults to no limit"() {
        when:
        def response = get("/ui/tree?window=w1")

        then:
        response.statusCode() == 200
        inspector.lastDepth == -1
    }

    def "tree requires a window"() {
        when:
        def response = get("/ui/tree")

        then:
        response.statusCode() == 400
        parse(response).error
    }

    def "find requires a criterion"() {
        when:
        def response = get("/ui/find?window=w1")

        then:
        response.statusCode() == 400
        parse(response).error.contains("id, text or type")
        inspector.lastQuery == null
    }

    def "finds nodes"() {
        when:
        def response = get("/ui/find?window=w1&id=processButton&type=Button")

        then:
        response.statusCode() == 200
        inspector.lastQuery == new UiQuery("w1", "processButton", null, "Button")
        def json = parse(response)
        json.size() == 1
        json[0].window == "w1"
        json[0].node.ref == "0"
    }

    def "decodes UTF-8 query parameters"() {
        when:
        def response = get("/ui/find?text=" + URLEncoder.encode("Démarrer l'été", StandardCharsets.UTF_8))

        then:
        response.statusCode() == 200
        inspector.lastQuery == new UiQuery(null, null, "Démarrer l'été", null)
    }

    def "takes snapshots"() {
        when:
        def response = client.send(request("/ui/snapshot?window=w1&node=0"), HttpResponse.BodyHandlers.ofByteArray())

        then:
        response.statusCode() == 200
        response.headers().firstValue("Content-Type").get() == "image/png"
        response.body() == FakeInspector.PNG
        inspector.lastWindow == "w1"
        inspector.lastRef == "0"
    }

    def "snapshot of a whole window has no node reference"() {
        when:
        def response = client.send(request("/ui/snapshot?window=w1"), HttpResponse.BodyHandlers.ofByteArray())

        then:
        response.statusCode() == 200
        inspector.lastRef == null
    }

    def "returns the state"() {
        when:
        def response = get("/ui/state")

        then:
        response.statusCode() == 200
        def json = parse(response)
        json.busy == false
        json.taskCount == 0
        json.currentTask == ""
        json.progress == -1
        json.outputScale == 1.5
    }

    def "waits until idle"() {
        given:
        inspector.busyPolls = 3

        when:
        def response = get("/ui/wait?until=idle&timeout=5000")

        then:
        response.statusCode() == 200
        parse(response).busy == false
        inspector.statePolls == 5
    }

    def "waits for a node"() {
        given:
        inspector.missingPolls = 2

        when:
        def response = get("/ui/wait?until=node&id=processButton&timeout=5000")

        then:
        response.statusCode() == 200
        parse(response)[0].node.id == "processButton"
        inspector.findPolls == 3
    }

    def "waiting for a node requires a criterion"() {
        when:
        def response = get("/ui/wait?until=node")

        then:
        response.statusCode() == 400
        parse(response).error
    }

    def "times out"() {
        given:
        inspector.busyPolls = Integer.MAX_VALUE

        when:
        def response = get("/ui/wait?until=idle&timeout=300")

        then:
        response.statusCode() == 408
        parse(response).error.contains("300")
    }

    def "rejects invalid wait parameters (#query)"() {
        when:
        def response = get("/ui/wait?" + query)

        then:
        response.statusCode() == 400
        parse(response).error

        where:
        query << ["until=never", "until=idle&timeout=-1", "until=idle&timeout=600001", "until=idle&timeout=abc", "timeout=10"]
    }

    def "propagates inspector errors"() {
        when:
        def response = get("/ui/tree?window=unknown")

        then:
        response.statusCode() == 404
        response.headers().firstValue("Content-Type").get().startsWith("application/json")
        parse(response).error == "Unknown window: unknown"
    }

    def "returns 404 for unknown routes"() {
        expect:
        get("/ui/unknown").statusCode() == 404
    }

    def "does not serve the web interface"() {
        expect:
        get(path).statusCode() == 404

        where:
        path << ["/", "/css/app.css", "/js/app.js", "/views/menu"]
    }

    def "serves the OpenAPI document"() {
        when:
        def response = get(UiApiServer.OPENAPI_PATH)

        then:
        response.statusCode() == 200
        response.body().contains("title: JSol'Ex UI API")
        response.body().contains("/ui/windows:")
        response.body().contains("/ui/wait:")
        !response.body().contains("/api/spectrum")
    }

    def "only listens on the loopback interface"() {
        expect:
        server.port() > 0
        get("/ui/state").uri().host == "127.0.0.1"
    }

    def "the web server does not expose the UI API"() {
        given:
        def context = ApplicationContext.run()

        expect:
        !context.containsBean(UiController)
        context.containsBean(MainController)
        context.containsBean(SpectrumController)
        context.containsBean(MainWebSocket)

        cleanup:
        context.close()
    }

    private HttpRequest request(String path) {
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:${server.port()}$path")).GET().build()
    }

    private HttpResponse<String> get(String path) {
        client.send(request(path), HttpResponse.BodyHandlers.ofString())
    }

    private static Object parse(HttpResponse<String> response) {
        new JsonSlurper().parseText(response.body())
    }

    static class FakeInspector implements UiInspector {
        static final byte[] PNG = [0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3] as byte[]

        String lastWindow
        Integer lastDepth
        String lastRef
        UiQuery lastQuery
        int busyPolls
        int missingPolls
        int statePolls
        int findPolls

        synchronized void reset() {
            lastWindow = null
            lastDepth = null
            lastRef = null
            lastQuery = null
            busyPolls = 0
            missingPolls = 0
            statePolls = 0
            findPolls = 0
        }

        @Override
        List<UiWindow> windows() {
            [new UiWindow("w1", "JSol'Ex", "Stage", true, BOUNDS)]
        }

        @Override
        synchronized UiNode tree(String windowId, int maxDepth) {
            lastWindow = windowId
            lastDepth = maxDepth
            if (windowId != "w1") {
                throw new UiApiException(404, "Unknown window: " + windowId)
            }
            ROOT
        }

        @Override
        synchronized List<UiMatch> find(UiQuery query) {
            lastQuery = query
            findPolls++
            if (findPolls <= missingPolls) {
                return []
            }
            [new UiMatch("w1", ROOT.children().getFirst())]
        }

        @Override
        synchronized byte[] snapshot(String windowId, String nodeRef) {
            lastWindow = windowId
            lastRef = nodeRef
            PNG
        }

        @Override
        synchronized UiState state() {
            statePolls++
            new UiState(statePolls <= busyPolls, 0, "", -1, 1.5)
        }
    }
}
