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
package me.champeau.a4j.jsolex.processing.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class Bass2000UploadHistoryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(Bass2000UploadHistoryService.class);
    private static final String DB_FILE = "bass2000-uploads.db";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Bass2000UploadHistoryService INSTANCE = new Bass2000UploadHistoryService();

    private final Path dbPath;

    private Bass2000UploadHistoryService() {
        this.dbPath = VersionUtil.getJsolexDir().resolve(DB_FILE);
        initializeDatabase();
    }

    public static Bass2000UploadHistoryService getInstance() {
        return INSTANCE;
    }

    private void initializeDatabase() {
        var createTableSQL = """
            CREATE TABLE IF NOT EXISTS uploads (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                observation_date DATE NOT NULL,
                wavelength_angstroms REAL NOT NULL,
                source_filename TEXT NOT NULL,
                uploaded_filename TEXT NOT NULL,
                upload_timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        """;

        var createIndexSQL = """
            CREATE INDEX IF NOT EXISTS idx_uploads_observation_date_wavelength
            ON uploads (observation_date, wavelength_angstroms)
        """;

        try (var conn = getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
            stmt.execute(createIndexSQL);
            LOGGER.debug("BASS2000 upload history database initialized at {}", dbPath);
        } catch (SQLException e) {
            LOGGER.error("Failed to initialize BASS2000 upload history database", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    public void recordUpload(LocalDate observationDate, double wavelengthAngstroms, String sourceFilename, String uploadedFilename) {
        var insertSQL = """
            INSERT INTO uploads (observation_date, wavelength_angstroms, source_filename, uploaded_filename)
            VALUES (?, ?, ?, ?)
        """;

        try (var conn = getConnection();
             var pstmt = conn.prepareStatement(insertSQL)) {

            pstmt.setString(1, observationDate.format(DATE_FORMATTER));
            pstmt.setDouble(2, wavelengthAngstroms);
            pstmt.setString(3, sourceFilename);
            pstmt.setString(4, uploadedFilename);

            pstmt.executeUpdate();
            LOGGER.debug("Recorded BASS2000 upload: {} Å on {}, source: {}, uploaded: {}",
                wavelengthAngstroms, observationDate, sourceFilename, uploadedFilename);
        } catch (SQLException e) {
            LOGGER.warn("Failed to record BASS2000 upload history", e);
        }
    }

    public Optional<UploadRecord> checkForDuplicateUpload(LocalDate observationDate, double wavelengthAngstroms) {
        var querySQL = """
            SELECT source_filename, uploaded_filename, upload_timestamp
            FROM uploads
            WHERE observation_date = ? AND ABS(wavelength_angstroms - ?) < 0.1
            ORDER BY upload_timestamp DESC
            LIMIT 1
        """;

        var formattedDate = observationDate.format(DATE_FORMATTER);

        try (var conn = getConnection();
             var pstmt = conn.prepareStatement(querySQL)) {

            pstmt.setString(1, formattedDate);
            pstmt.setDouble(2, wavelengthAngstroms);

            try (var rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    var record = new UploadRecord(
                        rs.getString("source_filename"),
                        rs.getString("uploaded_filename"),
                        rs.getString("upload_timestamp")
                    );
                    return Optional.of(record);
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Failed to check for duplicate BASS2000 upload", e);
        }

        return Optional.empty();
    }

    public record UploadRecord(
        String sourceFilename,
        String uploadedFilename,
        String uploadTimestamp
    ) {}
}