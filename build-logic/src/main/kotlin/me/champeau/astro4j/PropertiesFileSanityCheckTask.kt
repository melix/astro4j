/*
 * Copyright 2023-2024 the original author or authors.
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
package me.champeau.astro4j

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.*

@CacheableTask
abstract class PropertiesFileSanityCheckTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val propertiesFiles: ConfigurableFileCollection

    @TaskAction
    fun checkPropertiesFiles() {
        val violations = mutableListOf<String>()

        propertiesFiles.forEach { file ->
            if (file.name.endsWith(".properties")) {
                checkEncoding(file, violations)
                if (file.name.contains("_fr.")) {
                    checkFrenchTranslationCompleteness(file, violations)
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw RuntimeException("Properties file sanity check failed:\n${violations.joinToString("\n")}")
        }
    }

    private fun checkEncoding(file: File, violations: MutableList<String>) {
        val content = file.readText(StandardCharsets.ISO_8859_1)
        val properties = Properties().apply {
            file.inputStream().use { load(it) }
        }

        val problematicChars = listOf(
            "ï¿½",
            "\u00C3\u00A9", "\u00C3\u00A8", "\u00C3\u0020", "\u00C3\u00B4", "\u00C3\u00BB", "\u00C3\u00A7", "\u00C3\u00AB", "\u00C3\u00AE", "\u00C3\u00AF", "\u00C3\u00A2", "\u00C3\u00BC",
            "\u00E2\u0080\u0099", "\u00E2\u0080\u009C", "\u00E2\u0080\u009D", "\u00E2\u0080\u00A6", "\u00C2\u00B0", "\u00E2\u0080\u0093", "\u00E2\u0080\u0094", "\u00E2\u0080\u00B2", "\u00E2\u0080\u00B3"
        )

        problematicChars.forEach { char ->
            if (content.contains(char)) {
                val affectedProperties = properties.keys.filter { key ->
                    val value = properties.getProperty(key.toString())
                    value?.contains(char) == true
                }

                val propertyInfo = if (affectedProperties.isNotEmpty()) {
                    " (affects properties: ${affectedProperties.joinToString(", ")})"
                } else {
                    ""
                }

                violations.add("File ${file.relativeTo(project.rootDir)} contains improperly encoded character '$char' - likely UTF-8 bytes in ISO-8859-1 file$propertyInfo")
            }
        }

        val nonIso88591Chars = content.toCharArray().filter { it.code > 255 }
        if (nonIso88591Chars.isNotEmpty()) {
            violations.add("File ${file.relativeTo(project.rootDir)} contains characters outside ISO-8859-1 range: ${nonIso88591Chars.distinct()}")
        }
    }

    private fun checkFrenchTranslationCompleteness(frenchFile: File, violations: MutableList<String>) {
        val mainFileName = frenchFile.name.replace("_fr.properties", ".properties")
        val mainFile = File(frenchFile.parentFile, mainFileName)

        if (!mainFile.exists()) {
            violations.add("French translation file ${frenchFile.relativeTo(project.rootDir)} has no corresponding main file $mainFileName")
            return
        }

        val mainProperties = Properties().apply {
            mainFile.inputStream().use { load(it) }
        }

        val frenchProperties = Properties().apply {
            frenchFile.inputStream().use { load(it) }
        }

        val missingKeys = mainProperties.keys.filter { !frenchProperties.containsKey(it) }

        if (missingKeys.isNotEmpty()) {
            violations.add("French translation file ${frenchFile.relativeTo(project.rootDir)} is missing keys: ${missingKeys.joinToString(", ")}")
        }
    }
}