package dev.infrai.snapshot;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

public final class NightlySnapshotServiceTest {
    public static void main(String[] args) throws Exception {
        ExistingStorage storage = new ExistingStorage();
        NightlySnapshotService service = new NightlySnapshotService(storage);
        CommerceSnapshot snapshot = new CommerceSnapshot(LocalDate.parse("2026-08-18"), Instant.parse("2026-08-19T01:00:00Z"),
                new CommerceSnapshot.Checkout("chk-7", "ord-7", 4200, "USD"),
                new CommerceSnapshot.Fulfillment("ord-7", "SHIPPED", "track-7"),
                new CommerceSnapshot.Receipt("receipt-7", "ord-7", 4200),
                new CommerceSnapshot.CustomerOrderUpdate("ord-7", 2, "On the way"));

        NightlySnapshotService.Outcome outcome = service.capture(snapshot);
        require(outcome == NightlySnapshotService.Outcome.ALREADY_PRESENT, "existing snapshot must be retained");
        require(storage.presignCalls == 0 && storage.uploadCalls == 0, "duplicate snapshot must not be uploaded");
        require(storage.lastKey.equals("commerce/year=2026/month=08/snapshot-2026-08-18.json"), "date partition must be stable");
        System.out.println("PASS existing business date is not overwritten");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class ExistingStorage implements NightlySnapshotService.StorageBoundary {
        int presignCalls;
        int uploadCalls;
        String lastKey;
        public boolean exists(String key) { lastKey = key; return true; }
        public URI presign(String key, String idempotencyKey) { presignCalls++; return URI.create("https://example.test/upload"); }
        public void upload(URI url, String json) { uploadCalls++; }
    }
}
