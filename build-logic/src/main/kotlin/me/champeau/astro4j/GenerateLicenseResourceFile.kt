package me.champeau.astro4j

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

@CacheableTask
abstract class GenerateLicenseResourceFile : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licensesDir: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val sb = StringBuilder()
        sb.append("This software uses other open source libraries which licenses are listed below:\n")
        val texts = File(licensesDir.asFile.get(), "texts")
        texts.listFiles().forEach { groupId ->
            groupId.listFiles().forEach { artifactId ->
                sb.append("${groupId.name}:${artifactId.name}\n")
                Files.walk(artifactId.toPath())
                    .filter(Path::isRegularFile)
                    .forEach {
                        sb.append(it.fileName).append("\n")
                        sb.append(it.readText())
                    }
                sb.append("---\n")
            }
        }
        val outputFile = this.outputFile.get().asFile
        val outputDir = outputFile.parentFile
        outputDir.mkdirs()
        outputFile.writeText(sb.toString())
    }
}
