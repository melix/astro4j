# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Astro4j is a collection of Java libraries and applications for astronomy image processing, specializing in spectroheliographic video file processing. The project consists of multiple modules:

- **jsolex**: Main JavaFX application for processing Sol'Ex video files
- **jsolex-cli**: Command-line version of JSol'Ex processor
- **jsolex-core**: Core processing library with image analysis and spectroheliographic algorithms
- **jsolex-server**: Embedded web server for JSol'Ex
- **jsolex-scripting**: Scripting support with image math language
- **jserfile**: Library for reading SER video files
- **ser-player**: Application for playing SER files
- **math**: Mathematical utilities (linear regression, ellipse fitting, convolution)
- **docs**: Documentation module

## Build Commands

The project uses Gradle with Java 23 and requires GraalVM. All commands should be run from the project root.

### Core Build Commands
- `./gradlew build` - Full build with tests
- `./gradlew assemble` - Build without running tests
- `./gradlew test` - Run all tests
- `./gradlew check` - Run tests and quality checks (includes spotless formatting)
- `./gradlew clean` - Clean build artifacts

### Running Applications
- `./gradlew :jsolex:run` - Run the main JSol'Ex JavaFX application
- `./gradlew :ser-player:run` - Run the SER Player application
- `./gradlew :jsolex-cli:run` - Run the command-line version

### Distribution Building
- `./gradlew distZip` - Create ZIP distributions
- `./gradlew :jsolex:jlinkZipArchive` - Create native JLink archive for JSol'Ex
- `./gradlew allDistributions` - Build all distribution formats

### Code Quality
- `./gradlew spotlessCheck` - Check code formatting
- `./gradlew spotlessApply` - Fix code formatting issues

## Key Architecture Points

### Module System
The project uses Java modules (module-info.java). When compiling/running, the build sets up module paths correctly. Be aware of modular compilation constraints when adding dependencies.

### Custom Gradle Plugins
The project uses custom Gradle plugins in the `build-logic` directory:
- `me.champeau.astro4j.base` - Base configuration for all modules
- `me.champeau.astro4j.jfxapp` - JavaFX application setup
- `me.champeau.astro4j.library` - Library module configuration

### Code Generation
- **jsolex-core** generates Java code from YAML function definitions (ImageMath functions)
- CongoCC parser generator is used for the ImageMath language
- Spectrum data conversion from BASS2000 format

### Native Dependencies
The project includes JavaFX and uses jlink for creating native distributions. Vector API is enabled where supported.

### Testing
- Uses Spock framework for testing (Groovy-based)
- Test fixtures are available in jserfile module
- Large test files are stored in `test-files/uber-suite/`

## Development Notes

### ImageMath Language
The core module includes a custom DSL (Domain Specific Language) for image processing operations defined in YAML files under `jsolex-core/src/main/functions/`. These are compiled to Java code during build.

### Vector API Usage
The project leverages Java's Vector API for performance in mathematical operations. This is configured via the `astro4j.withVectorApi()` extension.

### Encoding

Files are encoded in UTF-8, at the exception of `.properties` files, which, as Java mandates, are encoded in ISO-8859-1. Editions of properties files MUST be done with tools which do not alter encoding. You are forbidden to alter lines that are not directly related to the change you are making, as this would alter the file's encoding.

### Comments

Do not add explanatory comments, unless an algorithm is particularly challenging to understand.

### New files

Don't forget to call `git add` for new files (or `git rm` for deleted files).

### Licensing
All Java files must include Apache 2.0 license headers. Use `./gradlew spotlessApply` to add missing headers.
