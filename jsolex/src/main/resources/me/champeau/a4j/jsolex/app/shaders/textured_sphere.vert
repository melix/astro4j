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
#version 150 core

in vec3 position;

uniform mat4 mvp;
uniform vec2 diskCenter;
uniform vec2 diskRadius;

out vec2 vUV;

void main() {
    gl_Position = mvp * vec4(position, 1.0);
    vUV = vec2(diskCenter.x + position.x * diskRadius.x,
               diskCenter.y - position.y * diskRadius.y);
}
