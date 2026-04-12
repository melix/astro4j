/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.spectrosolhub;

import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.QuotaResponse;

import java.util.function.Consumer;

class Step1AuthenticationHandler implements StepHandler {

    private final SpectroSolHubLoginPane loginPane;

    Step1AuthenticationHandler(Consumer<Boolean> onAuthenticationChanged) {
        this.loginPane = new SpectroSolHubLoginPane(onAuthenticationChanged);
    }

    @Override
    public VBox createContent() {
        return loginPane;
    }

    @Override
    public void load() {
        loginPane.load();
    }

    @Override
    public void cleanup() {
    }

    @Override
    public boolean validate() {
        return loginPane.isAuthenticated();
    }

    boolean isAuthenticated() {
        return loginPane.isAuthenticated();
    }

    QuotaResponse getQuotaResponse() {
        return loginPane.getQuotaResponse();
    }
}
