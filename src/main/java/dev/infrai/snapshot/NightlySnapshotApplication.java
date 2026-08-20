package dev.infrai.snapshot;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

public final class NightlySnapshotApplication {
    private NightlySnapshotApplication() {}

    public static void main(String[] args) throws Exception {
        SnapshotConfig config = SnapshotConfig.fromEnvironment();
        InfraiStorageClient client = new InfraiStorageClient(config);
        if (args.length == 1 && "setup".equals(args[0])) {
            client.createBucket();
            System.out.println("Bucket ready: " + config.bucket());
            return;
        }

        LocalDate date = args.length == 1 ? LocalDate.parse(args[0]) : LocalDate.now(Clock.system(config.businessZone())).minusDays(1);
        CommerceSnapshot snapshot = sample(date, Instant.now());
        NightlySnapshotService service = new NightlySnapshotService(new NightlySnapshotService.StorageBoundary() {
            public boolean exists(String key) throws java.io.IOException, InterruptedException { return client.objectExists(key); }
            public java.net.URI presign(String key, String idempotencyKey) throws java.io.IOException, InterruptedException { return client.presignPut(key, idempotencyKey); }
            public void upload(java.net.URI url, String json) throws java.io.IOException, InterruptedException { client.putSigned(url, json); }
        });
        NightlySnapshotService.Outcome outcome = service.capture(snapshot);
        System.out.println("Snapshot " + outcome + ": " + NightlySnapshotService.objectKey(date));
    }

    private static CommerceSnapshot sample(LocalDate date, Instant capturedAt) {
        return new CommerceSnapshot(date, capturedAt,
                new CommerceSnapshot.Checkout("chk_20260818_1042", "ord_1042", 12900, "USD"),
                new CommerceSnapshot.Fulfillment("ord_1042", "SHIPPED", "carrier-7781"),
                new CommerceSnapshot.Receipt("rcpt_1042", "ord_1042", 12900),
                new CommerceSnapshot.CustomerOrderUpdate("ord_1042", 3, "On the way"));
    }
}
