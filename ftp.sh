#!/bin/bash

mkdir -p /tmp/ftp/ftpusers/test/
touch /tmp/ftp/ftpusers/test/this_working_oh_hai.txt
docker run -d --name ftpd_server -p 21:21 -p 30000-30009:30000-30009 -v /tmp/ftp:/hostmount onekilo79/ftpd_test

