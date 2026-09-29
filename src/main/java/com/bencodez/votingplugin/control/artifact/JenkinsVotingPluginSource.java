package com.bencodez.votingplugin.control.artifact;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Fixed, read-only source for public VotingPlugin development artifacts. */
public final class JenkinsVotingPluginSource implements AutoCloseable {
    static final URI DEFAULT_JOB = URI.create("https://bencodez.com/job/VotingPlugin/");
    private static final int MAX_METADATA_BYTES = 256 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern ARTIFACT_NAME = Pattern.compile("VotingPlugin-[0-9]+\\.jar");
    private static final Pattern ARTIFACT_PATH = Pattern.compile("VotingPlugin/target/VotingPlugin-[0-9]+\\.jar");

    private final URI job;
    private final HttpClient client;
    private final ObjectMapper json;
    private final ScheduledExecutorService metadataTimeouts;
    private final Duration metadataTimeout;

    public JenkinsVotingPluginSource() {
        this(DEFAULT_JOB, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    JenkinsVotingPluginSource(URI job, HttpClient client) {
        this(job, client, REQUEST_TIMEOUT);
    }

    JenkinsVotingPluginSource(URI job, HttpClient client, Duration metadataTimeout) {
        this.job = requireJob(job);
        this.client = client;
        if (metadataTimeout == null || metadataTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("Invalid Jenkins metadata timeout");
        }
        this.metadataTimeout = metadataTimeout;
        metadataTimeouts = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "votingplugin-control-jenkins-timeout");
            thread.setDaemon(true);
            return thread;
        });
        json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public Build latestSuccessful() throws IOException {
        return readBuild(job.resolve("lastSuccessfulBuild/api/json"), null);
    }

    public Download open(int buildNumber) throws IOException {
        if (buildNumber < 1) throw unavailable();
        Build build = readBuild(job.resolve(buildNumber + "/api/json"), buildNumber);
        URI artifact = job.resolve(buildNumber + "/artifact/" + build.relativePath());
        HttpRequest request = request(artifact).build();
        HttpResponse<InputStream> response = send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200 || response.headers().firstValueAsLong("Content-Length")
                .orElse(-1L) > ArtifactStore.MAX_UPLOAD_BYTES) {
            response.body().close();
            throw unavailable();
        }
        return new Download(build, response.body());
    }

    private Build readBuild(URI uri, Integer expectedNumber) throws IOException {
        HttpResponse<InputStream> response = send(request(uri).header("Accept", "application/json").build(),
                HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) throw unavailable();
            ScheduledFuture<?> timeout = metadataTimeouts.schedule(() -> closeQuietly(body),
                    metadataTimeout.toMillis(), TimeUnit.MILLISECONDS);
            byte[] content;
            try {
                content = body.readNBytes(MAX_METADATA_BYTES + 1);
            } finally {
                timeout.cancel(false);
            }
            if (content.length > MAX_METADATA_BYTES) throw unavailable();
            JsonNode root = json.readTree(content);
            int number = root.path("number").asInt(-1);
            String result = root.path("result").asText("");
            boolean building = root.path("building").asBoolean(true);
            long timestamp = root.path("timestamp").asLong(-1);
            if (number < 1 || expectedNumber != null && number != expectedNumber
                    || building || !"SUCCESS".equals(result) || timestamp < 0) throw unavailable();
            JsonNode artifactList = root.path("artifacts");
            if (!artifactList.isArray() || artifactList.isEmpty() || artifactList.size() > 32) throw unavailable();
            JsonNode artifact = null;
            for (JsonNode candidate : artifactList) {
                String candidateName = candidate.path("fileName").asText("");
                String candidatePath = candidate.path("relativePath").asText("");
                if (candidateName.equals("VotingPlugin-" + number + ".jar")
                        && candidatePath.equals("VotingPlugin/target/" + candidateName)) {
                    if (artifact != null) throw unavailable();
                    artifact = candidate;
                }
            }
            if (artifact == null) throw unavailable();
            String fileName = artifact.path("fileName").asText("");
            String relativePath = artifact.path("relativePath").asText("");
            if (!ARTIFACT_NAME.matcher(fileName).matches() || !ARTIFACT_PATH.matcher(relativePath).matches()
                    || !fileName.equals("VotingPlugin-" + number + ".jar")
                    || !relativePath.equals("VotingPlugin/target/" + fileName)) throw unavailable();
            return new Build(number, timestamp, fileName, relativePath);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private HttpRequest.Builder request(URI uri) {
        if (!sameOrigin(uri)) throw new IllegalStateException("Jenkins source escaped its fixed origin");
        return HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT)
                .header("User-Agent", "VotingPlugin-Control/1.0");
    }

    private boolean sameOrigin(URI uri) {
        return job.getScheme().equalsIgnoreCase(uri.getScheme()) && job.getHost().equalsIgnoreCase(uri.getHost())
                && effectivePort(job) == effectivePort(uri) && uri.getRawUserInfo() == null;
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static URI requireJob(URI job) {
        boolean testLoopback = job != null && "http".equalsIgnoreCase(job.getScheme())
                && ("127.0.0.1".equals(job.getHost()) || "::1".equals(job.getHost()));
        if (job == null || !("https".equalsIgnoreCase(job.getScheme()) || testLoopback) || job.getHost() == null
                || job.getRawUserInfo() != null || job.getRawQuery() != null || job.getRawFragment() != null
                || !job.getPath().endsWith("/")) throw new IllegalArgumentException("Invalid Jenkins job origin");
        return job;
    }

    private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return client.send(request, handler);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private static IOException unavailable() {
        return new IOException("VotingPlugin development build is unavailable");
    }

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // Closing is the deadline signal; the blocked reader reports the failure.
        }
    }

    @Override public void close() {
        metadataTimeouts.shutdownNow();
    }

    public record Build(int buildNumber, long timestamp, String fileName, String relativePath) { }

    public record Download(Build build, InputStream body) implements AutoCloseable {
        @Override public void close() throws IOException { body.close(); }
    }
}
