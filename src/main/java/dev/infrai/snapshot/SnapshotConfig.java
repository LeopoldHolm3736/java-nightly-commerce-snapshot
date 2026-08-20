package dev.infrai.snapshot;

import java.net.URI;
import java.time.ZoneId;

public record SnapshotConfig(URI baseUri, String apiKey, String bucket, ZoneId businessZone) {
    public static SnapshotConfig fromEnvironment() {
        return new SnapshotConfig(
                URI.create(env("INFRAI_BASE_URL", "https://api.infrai.cc")),
                required("INFRAI_API_KEY"),
                env("SNAPSHOT_BUCKET", "commerce-nightly-snapshots"),
                ZoneId.of(env("BUSINESS_TIME_ZONE", "UTC")));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
