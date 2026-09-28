/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.server.ui;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Read-only HTTP API giving access to the user interface of JSol'Ex.
 */
@OpenAPIDefinition(info = @Info(
    title = "JSol'Ex UI API",
    version = "1.0",
    description = """
        Read-only inspection API of the JSol'Ex desktop user interface, meant for agents which drive the \
        application with an external tool (e.g. xdotool), run UI tests or automate documentation screenshots.

        The API is only started when the JSOLEX_UI_API environment variable is set to a port number when the \
        application starts. It only listens on 127.0.0.1 and has no authentication. It never performs actions: \
        clicks and keyboard input must be sent by the caller.

        Bounds are screen coordinates expressed in JavaFX logical pixels: multiply them by the outputScale \
        returned by /ui/state to obtain physical pixels, e.g. for xdotool.

        A node reference (ref) is the slash-separated list of child indices from the window root, e.g. 0/2/1. \
        The root of a window is the empty string. References change when the scene graph changes, so look them \
        up again after an action.

        Errors are returned as JSON objects with an error field."""))
@Tag(name = "UI", description = "Inspection of the JavaFX user interface")
@Controller("/ui")
@Requires(env = UiApiServer.ENVIRONMENT)
@ExecuteOn(TaskExecutors.BLOCKING)
public class UiController {
    private static final long POLL_INTERVAL_MILLIS = 100;
    private static final long MAX_TIMEOUT_MILLIS = 600_000;

    private final UiInspector inspector;

    /**
     * Creates the controller.
     *
     * @param inspector the user interface inspector
     */
    public UiController(UiInspector inspector) {
        this.inspector = inspector;
    }

    /**
     * Lists the windows.
     *
     * @return the windows
     */
    @Get("/windows")
    @Operation(
        summary = "List the windows",
        description = "Lists the windows currently showing (main window first), including dialogs, popups and menus. "
            + "The returned identifiers are used as the window parameter of the other endpoints.")
    @ApiResponse(responseCode = "200", description = "The windows",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = UiWindow.class))))
    public List<UiWindow> windows() {
        return inspector.windows();
    }

    /**
     * Returns the node tree of a window.
     *
     * @param window the window identifier
     * @param depth the maximum depth
     * @return the root node
     */
    @Get("/tree")
    @Operation(
        summary = "Get the node tree of a window",
        description = "Returns the visible nodes of a window as a tree, with their JavaFX id, type, displayed text, "
            + "state and bounds on screen. Use a small depth to explore large windows incrementally, then /ui/find "
            + "or a deeper tree to locate a node.")
    @ApiResponse(responseCode = "200", description = "The root node of the window",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiNode.class)))
    @ApiResponse(responseCode = "400", description = "Missing or invalid parameter",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    @ApiResponse(responseCode = "404", description = "Unknown window",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    public UiNode tree(@Parameter(description = "Window identifier, as returned by /ui/windows", required = true) @QueryValue String window,
                       @Parameter(description = "Maximum depth to descend from the root (0 returns the root only), or -1 for no limit") @QueryValue(defaultValue = "-1") int depth) {
        return inspector.tree(window, depth);
    }

    /**
     * Finds nodes.
     *
     * @param window the window identifier, or null for all windows
     * @param id the JavaFX id
     * @param text a substring of the displayed text
     * @param type the simple class name
     * @return the matching nodes
     */
    @Get("/find")
    @Operation(
        summary = "Find nodes",
        description = "Finds the visible nodes matching all the given criteria, in one window or in all windows. "
            + "At least one of id, text or type is required. The matches do not include children; "
            + "use the returned ref with /ui/tree or /ui/snapshot.")
    @ApiResponse(responseCode = "200", description = "The matching nodes, possibly empty",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, array = @ArraySchema(schema = @Schema(implementation = UiMatch.class))))
    @ApiResponse(responseCode = "400", description = "No criterion given",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    @ApiResponse(responseCode = "404", description = "Unknown window",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    public List<UiMatch> find(@Parameter(description = "Window identifier, as returned by /ui/windows. All windows are searched if absent.") @Nullable @QueryValue String window,
                              @Parameter(description = "Exact JavaFX id of the node") @Nullable @QueryValue String id,
                              @Parameter(description = "Case-insensitive substring of the text displayed by the node") @Nullable @QueryValue String text,
                              @Parameter(description = "Exact simple class name of the node, e.g. Button, Label, TextField") @Nullable @QueryValue String type) {
        return inspector.find(query(window, id, text, type));
    }

    /**
     * Renders a window or a node as PNG.
     *
     * @param window the window identifier
     * @param node the node reference, or null for the whole window
     * @return the PNG bytes
     */
    @Get("/snapshot")
    @Produces(MediaType.IMAGE_PNG)
    @Operation(
        summary = "Take a snapshot",
        description = "Renders a whole window, or one of its nodes, as a PNG image. The image is rendered by JavaFX "
            + "at the output scale of the screen, so it does not include window decorations nor overlapping windows.")
    @ApiResponse(responseCode = "200", description = "The PNG image",
        content = @Content(mediaType = MediaType.IMAGE_PNG, schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Missing parameter or malformed node reference",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    @ApiResponse(responseCode = "404", description = "Unknown window or node",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    public byte[] snapshot(@Parameter(description = "Window identifier, as returned by /ui/windows", required = true) @QueryValue String window,
                           @Parameter(description = "Reference of the node to render (slash-separated child indices from the window root). The whole window is rendered if absent.") @Nullable @QueryValue String node) {
        return inspector.snapshot(window, node);
    }

    /**
     * Returns the state of the application.
     *
     * @return the state
     */
    @Get("/state")
    @Operation(
        summary = "Get the application state",
        description = "Returns whether the application is busy (processing, running a script...), the current task "
            + "and its progress, and the output scale of the screen, used to convert bounds to physical pixels.")
    @ApiResponse(responseCode = "200", description = "The state",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiState.class)))
    public UiState state() {
        return inspector.state();
    }

    /**
     * Waits for a condition.
     *
     * @param until the condition
     * @param timeout the timeout in milliseconds
     * @param window the window identifier
     * @param id the JavaFX id
     * @param text a substring of the displayed text
     * @param type the simple class name
     * @return the state or the matches
     */
    @Get("/wait")
    @Operation(
        summary = "Wait for a condition",
        description = "Blocks until a condition is met, polling every 100 ms. With until=idle, waits until the "
            + "application is not busy on two consecutive polls and returns the state. With until=node, waits until "
            + "at least one node matches the criteria (same rules as /ui/find) and returns the matches. "
            + "Use it after triggering an action, e.g. to wait for processing to finish or for a dialog to open.")
    @ApiResponse(responseCode = "200", description = "The condition is met: a UiState for until=idle, an array of UiMatch for until=node",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(oneOf = {UiState.class, UiMatch[].class})))
    @ApiResponse(responseCode = "400", description = "Unknown condition, invalid timeout or no criterion given for until=node",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    @ApiResponse(responseCode = "404", description = "Unknown window for until=node",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    @ApiResponse(responseCode = "408", description = "The condition was not met before the timeout",
        content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = UiError.class)))
    public Object await(@Parameter(description = "Condition to wait for", required = true, schema = @Schema(allowableValues = {"idle", "node"})) @QueryValue String until,
                        @Parameter(description = "Timeout in milliseconds, between 0 and 600000") @QueryValue(defaultValue = "30000") long timeout,
                        @Parameter(description = "For until=node: window identifier. All windows are searched if absent.") @Nullable @QueryValue String window,
                        @Parameter(description = "For until=node: exact JavaFX id of the node") @Nullable @QueryValue String id,
                        @Parameter(description = "For until=node: case-insensitive substring of the text displayed by the node") @Nullable @QueryValue String text,
                        @Parameter(description = "For until=node: exact simple class name of the node") @Nullable @QueryValue String type) {
        if (timeout < 0 || timeout > MAX_TIMEOUT_MILLIS) {
            throw new IllegalArgumentException("timeout must be between 0 and " + MAX_TIMEOUT_MILLIS + " ms");
        }
        return switch (until.toLowerCase(Locale.ROOT)) {
            case "idle" -> awaitIdle(timeout);
            case "node" -> {
                var query = query(window, id, text, type);
                yield poll(timeout, () -> {
                    var matches = inspector.find(query);
                    return matches.isEmpty() ? null : matches;
                });
            }
            default -> throw new IllegalArgumentException("Unknown condition '" + until + "', expected idle or node");
        };
    }

    private UiState awaitIdle(long timeout) {
        var idlePolls = new AtomicInteger();
        return poll(timeout, () -> {
            var state = inspector.state();
            if (state.busy()) {
                idlePolls.set(0);
                return null;
            }
            return idlePolls.incrementAndGet() >= 2 ? state : null;
        });
    }

    private static <T> T poll(long timeout, Supplier<T> condition) {
        var deadline = System.nanoTime() + timeout * 1_000_000L;
        while (true) {
            var result = condition.get();
            if (result != null) {
                return result;
            }
            if (System.nanoTime() >= deadline) {
                throw new UiApiException(HttpStatus.REQUEST_TIMEOUT.getCode(), "Condition not met after " + timeout + " ms");
            }
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UiApiException(HttpStatus.SERVICE_UNAVAILABLE.getCode(), "Interrupted while waiting");
            }
        }
    }

    private static UiQuery query(String window, String id, String text, String type) {
        if (id == null && text == null && type == null) {
            throw new IllegalArgumentException("At least one of id, text or type is required");
        }
        return new UiQuery(window, id, text, type);
    }

    @Error(exception = UiApiException.class)
    HttpResponse<UiError> onUiApiException(UiApiException e) {
        return HttpResponse.<UiError>status(HttpStatus.valueOf(e.status())).body(new UiError(e.getMessage()));
    }

    @Error(exception = IllegalArgumentException.class)
    HttpResponse<UiError> onIllegalArgument(IllegalArgumentException e) {
        return badRequest(e.getMessage());
    }

    @Error(exception = UnsatisfiedRouteException.class)
    HttpResponse<UiError> onUnsatisfiedRoute(UnsatisfiedRouteException e) {
        return badRequest(e.getMessage());
    }

    @Error(exception = ConversionErrorException.class)
    HttpResponse<UiError> onConversionError(ConversionErrorException e) {
        return badRequest(e.getMessage());
    }

    private static HttpResponse<UiError> badRequest(String message) {
        return HttpResponse.<UiError>badRequest().body(new UiError(message));
    }
}
