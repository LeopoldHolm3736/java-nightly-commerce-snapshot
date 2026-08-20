package dev.infrai.snapshot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class InfraiStorageClient {
    private final HttpClient http;
    private final SnapshotConfig config;

    public InfraiStorageClient(SnapshotConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiStorageClient(SnapshotConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public void createBucket() throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", "{\"name\":" + JsonValue.quote(config.bucket()) + "}");
    }

    public boolean objectExists(String key) throws IOException, InterruptedException {
        Map<String, Object> data = call("GET", "/v1/storage/object/head/" + segment(config.bucket()) + "/" + segment(key), null);
        return Boolean.TRUE.equals(data.get("found"));
    }

    public URI presignPut(String key, String idempotencyKey) throws IOException, InterruptedException {
        String body = "{\"op\":\"put\",\"expires_seconds\":600,\"content_type\":\"application/json\",\"idempotency_key\":"
                + JsonValue.quote(idempotencyKey) + "}";
        Map<String, Object> data = call("POST", "/v1/storage/object/presign/" + segment(config.bucket()) + "/" + segment(key), body);
        return URI.create(requiredString(data, "url"));
    }

    public void putSigned(URI url, String json) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(url)
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .method("PUT", HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Signed upload returned HTTP " + response.statusCode());
        }
    }

    private Map<String, Object> call(String method, String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json");
            HttpRequest request = builder.method(method, body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope;
            try {
                envelope = JsonValue.object(response.body());
            } catch (IllegalArgumentException parseError) {
                throw new IOException("Invalid Infrai response envelope", parseError);
            }
            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Object error = envelope.get("error");
                throw new InfraiException(response.statusCode(), error instanceof Map<?, ?> map ? map : Map.of("message", String.valueOf(error)));
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            Object data = envelope.get("data");
            if (!(data instanceof Map<?, ?> map)) throw new IOException("Infrai envelope data must be an object");
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        throw new IOException("Retry budget exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> { try { return Long.parseLong(value) * 1000L; } catch (NumberFormatException ignored) { return 250L << attempt; } })
                .orElse(250L << attempt);
    }

    private static String requiredString(Map<String, Object> data, String field) throws IOException {
        Object value = data.get(field);
        if (!(value instanceof String text) || text.isBlank()) throw new IOException("Missing response field: " + field);
        return text;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static final class InfraiException extends IOException {
        private final int status;
        private final Map<?, ?> error;
        InfraiException(int status, Map<?, ?> error) {
            super("Infrai request rejected: " + error);
            this.status = status;
            this.error = error;
        }
        public int status() { return status; }
        public Map<?, ?> error() { return error; }
    }
}
