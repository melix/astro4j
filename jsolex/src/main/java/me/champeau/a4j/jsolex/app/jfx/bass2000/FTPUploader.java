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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import me.champeau.a4j.jsolex.app.Configuration;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.function.Consumer;

class FTPUploader {
    private static final String DUMMY_FTP = "ftp://dummy";

    static void uploadFileToFTP(File file,
                                long totalBytes,
                                long bytesAlreadyTransferred,
                                Consumer<? super Double> progressListener) throws IOException {
        var config = Configuration.getInstance();
        var ftpUrl = config.getBass2000FtpUrl();
        if (DUMMY_FTP.equals(ftpUrl)) {
            return;
        }
        try {
            var uri = new URI(ftpUrl);
            var ftpHost = uri.getHost();
            var ftpPort = uri.getPort() == -1 ? 21 : uri.getPort();
            var ftpPath = uri.getPath();

            var ftpClient = new FTPClient();
            try {
                ftpClient.connect(ftpHost, ftpPort);

                if (!FTPReply.isPositiveCompletion(ftpClient.getReplyCode())) {
                    throw new IOException("FTP server refused connection");
                }

                if (!ftpClient.login("anonymous", "")) {
                    throw new IOException("FTP login failed");
                }

                ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
                ftpClient.enterLocalPassiveMode();

                if (!ftpClient.changeWorkingDirectory(ftpPath)) {
                    if (!ftpClient.makeDirectory(ftpPath) || !ftpClient.changeWorkingDirectory(ftpPath)) {
                        throw new IOException("Failed to create or change to directory: " + ftpPath);
                    }
                }

                try (var inputStream = new BufferedInputStream(new ProgressTrackingInputStream(new FileInputStream(file), totalBytes, bytesAlreadyTransferred, progressListener))) {
                    if (!ftpClient.storeFile(file.getName(), inputStream)) {
                        throw new IOException("Failed to store file: " + file.getName());
                    }
                }

                ftpClient.logout();
            } finally {
                if (ftpClient.isConnected()) {
                    ftpClient.disconnect();
                }
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid FTP URL: " + ftpUrl, e);
        }
    }

    private static class ProgressTrackingInputStream extends FilterInputStream {
        private final long totalBytes;
        private final long bytesAlreadyTransferred;
        private final Consumer<? super Double> progressListener;
        private long bytesRead = 0;

        public ProgressTrackingInputStream(InputStream in,
                                           long totalBytes,
                                           long bytesAlreadyTransferred,
                                           Consumer<? super Double> progressListener) {
            super(in);
            this.totalBytes = totalBytes;
            this.bytesAlreadyTransferred = bytesAlreadyTransferred;
            this.progressListener = progressListener;
        }

        @Override
        public int read() throws IOException {
            var result = super.read();
            if (result != -1) {
                bytesRead++;
                updateProgress();
            }
            return result;
        }

        @Override
        public int read(byte[] b) throws IOException {
            var result = super.read(b);
            if (result != -1) {
                bytesRead += result;
                updateProgress();
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            var result = super.read(b, off, len);
            if (result != -1) {
                bytesRead += result;
                updateProgress();
            }
            return result;
        }

        private void updateProgress() {
            if (totalBytes > 0) {
                var progress = (double) (bytesAlreadyTransferred + bytesRead) / totalBytes;
                progressListener.accept(progress);
            }
        }
    }
}
