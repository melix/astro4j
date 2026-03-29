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
package me.champeau.a4j.jsolex.processing.sun.workflow;

/**
 * Metadata stored in an ImageWrapper to carry the SpectroSolHub image kind
 * for IMAGE_MATH outputs. This allows differentiating script outputs that
 * would otherwise all share the same IMAGE_MATH kind.
 *
 * @param value the SpectroSolHub image kind string, e.g. "IMAGE_MATH_HA"
 */
public record SpectroSolHubImageKind(String value) {
}
