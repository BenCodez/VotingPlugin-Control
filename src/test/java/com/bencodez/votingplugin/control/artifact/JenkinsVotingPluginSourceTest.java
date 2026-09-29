package com.bencodez.votingplugin.control.artifact;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JenkinsVotingPluginSourceTest {
    @TempDir Path directory;
    private HttpServer server;
    private final List<JenkinsVotingPluginSource> sources = new ArrayList<>();

    @AfterEach void stop() {
        sources.forEach(JenkinsVotingPluginSource::close);
        if (server != null) server.stop(0);
    }

    @Test void readsFixedSuccessfulBuildAndDownloadsExactArtifact() throws Exception {
        byte[] jar = votingPluginJar();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/job/VotingPlugin/lastSuccessfulBuild/api/json", metadata(2393, "VotingPlugin-7.1.2-SNAPSHOT.jar"));
        respond("/job/VotingPlugin/2393/api/json", metadata(2393, "VotingPlugin-7.1.2-SNAPSHOT.jar"));
        respond("/job/VotingPlugin/2393/artifact/VotingPlugin/target/VotingPlugin-7.1.2-SNAPSHOT.jar", jar,
                "application/java-archive");
        server.start();

        JenkinsVotingPluginSource source = source();
        assertEquals(2393, source.latestSuccessful().buildNumber());
        try (JenkinsVotingPluginSource.Download download = source.open(2393)) {
            assertEquals("VotingPlugin-7.1.2-SNAPSHOT.jar", download.build().fileName());
            ArtifactStore.Artifact artifact = new ArtifactStore(directory).upload(download.body(),
                    download.build().fileName(), null);
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jar)),
                    artifact.artifactId());
            assertEquals(jar.length, artifact.size());
        }
    }

    @Test void rejectsFailedMismatchedAndRedirectedBuilds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/job/VotingPlugin/lastSuccessfulBuild/api/json", metadata(2393).replace("SUCCESS", "FAILURE"));
        respond("/job/VotingPlugin/2393/api/json", metadata(2394));
        server.createContext("/job/VotingPlugin/2394/api/json", exchange -> {
            exchange.getResponseHeaders().set("Location", "https://example.test/attacker");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();

        JenkinsVotingPluginSource source = source();
        assertThrows(java.io.IOException.class, source::latestSuccessful);
        assertThrows(java.io.IOException.class, () -> source.open(2393));
        assertThrows(java.io.IOException.class, () -> source.open(2394));
    }

    @Test void rejectsUnsafeOrNonTargetArtifactPaths() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/job/VotingPlugin/lastSuccessfulBuild/api/json",
                metadata(2393, "VotingPlugin-7.1.2.jar", "VotingPlugin/target/../VotingPlugin-7.1.2.jar"));
        server.start();

        assertThrows(java.io.IOException.class, () -> source().latestSuccessful());
    }

    @Test void boundsStalledMetadataBodyReads() throws Exception {
        CountDownLatch headersSent = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/job/VotingPlugin/lastSuccessfulBuild/api/json", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 1024);
            headersSent.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        JenkinsVotingPluginSource source = source(Duration.ofMillis(100));
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                    assertThrows(java.io.IOException.class, source::latestSuccessful));
            assertTrue(headersSent.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test void recordsInterruptedArtifactSourceReads() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        respond("/job/VotingPlugin/2393/api/json", metadata(2393));
        server.createContext("/job/VotingPlugin/2393/artifact/VotingPlugin/target/VotingPlugin-2393.jar", exchange -> {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write(new byte[] {1});
            exchange.close();
        });
        server.start();

        try (JenkinsVotingPluginSource.Download download = source().open(2393)) {
            assertThrows(java.io.IOException.class, () -> download.body().readAllBytes());
            assertTrue(download.sourceReadFailed());
        }
    }

    private JenkinsVotingPluginSource source() {
        return source(Duration.ofSeconds(30));
    }

    private JenkinsVotingPluginSource source(Duration timeout) {
        URI job = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/job/VotingPlugin/");
        JenkinsVotingPluginSource source = new JenkinsVotingPluginSource(job, HttpClient.newHttpClient(), timeout);
        sources.add(source);
        return source;
    }

    private void respond(String path, String body) {
        respond(path, body.getBytes(StandardCharsets.UTF_8), "application/json");
    }

    private void respond(String path, byte[] body, String contentType) {
        server.createContext(path, exchange -> {
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
    }

    private static String metadata(int build) {
        return metadata(build, "VotingPlugin-" + build + ".jar");
    }

    private static String metadata(int build, String fileName) {
        return metadata(build, fileName, "VotingPlugin/target/" + fileName);
    }

    private static String metadata(int build, String fileName, String relativePath) {
        return """
                {"number":%d,"result":"SUCCESS","building":false,"timestamp":1790648737343,
                 "artifacts":[{"fileName":"%s",
                 "relativePath":"%s"}]}
                """.formatted(build, fileName, relativePath);
    }

    private static byte[] votingPluginJar() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("plugin.yml"));
            zip.write("name: VotingPlugin\nmain: example.Main\nversion: test\n".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
