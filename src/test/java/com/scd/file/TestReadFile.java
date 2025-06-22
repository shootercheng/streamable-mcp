package com.scd.file;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestReadFile {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestReadFile.class);

    @Test
    public void testReadFileByUri() throws IOException {
        String uri = "file://doc/mcp.md";
        String filePath = uri.replace("file://", "");
        String content = new String(Files.readAllBytes(Paths.get(filePath)));
        LOGGER.info("file data {}", content);
    }
}
