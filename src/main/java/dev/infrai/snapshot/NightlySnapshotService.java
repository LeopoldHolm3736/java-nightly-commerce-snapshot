package dev.infrai.snapshot;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;

public final class NightlySnapshotService {
    public interface StorageBoundary {
        boolean exists(String key) throws IOException, InterruptedException;
        URI presign(String key, String idempotencyKey) throws IOException, InterruptedException;
        void upload(URI url, String json) throws IOException, InterruptedException;
    }

    public enum Outcome { STORED, ALREADY_PRESENT }

    private final StorageBoundary storage;

    public NightlySnapshotService(StorageBoundary storage) {
        this.storage = storage;
    }

    public Outcome capture(CommerceSnapshot snapshot) throws IOException, InterruptedException {
        String key = objectKey(snapshot.businessDate());
        if (storage.exists(key)) return Outcome.ALREADY_PRESENT;
        URI signedUrl = storage.presign(key, "nightly-commerce-" + snapshot.businessDate());
        storage.upload(signedUrl, snapshot.toJson());
        return Outcome.STORED;
    }

    public static String objectKey(LocalDate businessDate) {
        return "commerce/year=" + businessDate.getYear() + "/month=" + String.format("%02d", businessDate.getMonthValue())
                + "/snapshot-" + businessDate + ".json";
    }
}
