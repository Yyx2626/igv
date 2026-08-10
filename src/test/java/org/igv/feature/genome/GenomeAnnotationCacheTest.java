package org.igv.feature.genome;

import com.sun.net.httpserver.HttpServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenomeAnnotationCacheTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void downloadPublishesOnlyCompleteCacheFile() throws Exception {
        byte[] content = "cached-refseq".getBytes(StandardCharsets.UTF_8);
        HttpServer server = startServer(content, 0);
        try {
            File cacheFile = temporaryFolder.newFolder("annotations").toPath().resolve("refGene.txt.gz").toFile();

            boolean downloaded = GenomeManager.downloadAnnotationToCache(
                    url(server), cacheFile, 5_000);

            assertTrue(downloaded);
            assertArrayEquals(content, Files.readAllBytes(cacheFile.toPath()));
            assertFalse(hasTemporaryDownload(cacheFile.getParentFile()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void timeoutLeavesNoPartialCacheFile() throws Exception {
        HttpServer server = startServer("late".getBytes(StandardCharsets.UTF_8), 1_000);
        try {
            File cacheFile = temporaryFolder.newFolder("timeout").toPath().resolve("refGene.txt.gz").toFile();

            boolean downloaded = GenomeManager.downloadAnnotationToCache(
                    url(server), cacheFile, 100);

            assertFalse(downloaded);
            assertFalse(cacheFile.exists());
            assertFalse(hasTemporaryDownload(cacheFile.getParentFile()));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startServer(byte[] content, long delayMillis) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/refGene.txt.gz", exchange -> {
            try {
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "annotation-cache-test-server");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
        return server;
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/refGene.txt.gz";
    }

    private static boolean hasTemporaryDownload(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".download"));
        return files != null && files.length > 0;
    }
}
