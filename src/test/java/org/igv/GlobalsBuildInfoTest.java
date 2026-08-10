package org.igv;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class GlobalsBuildInfoTest {

    @Test
    public void computesSha256ForRuntimeArtifact() throws Exception {
        Path file = Files.createTempFile("igv-build-info", ".bin");
        try {
            Files.writeString(file, "IGV", StandardCharsets.UTF_8);
            assertEquals("849ae6c484a2bc15f875fce3e1617bfe1b300a7d3749638995e070667aae0647",
                    Globals.sha256(file));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
