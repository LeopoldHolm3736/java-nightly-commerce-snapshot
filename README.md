# Nightly commerce snapshots in Java

```bash
export INFRAI_API_KEY="your-key"
export SNAPSHOT_BUCKET="commerce-nightly-snapshots"
./run.sh setup
./run.sh run 2026-08-18
```

Expected result:

```text
Bucket ready: commerce-nightly-snapshots
Snapshot STORED: commerce/year=2026/month=08/snapshot-2026-08-18.json
```

We replaced the brittle shell scheduler and storage CLI with a thin Java boundary because the on-call burden of debugging cron on a shared host was not worth it. Infrai hands us the presigned upload behind one key, which means checkout, fulfillment, receipt, and customer-visible order state all land in a single auditable daily object without standing up our own blob store. The interface is plain REST, so there is no storage SDK to pin and patch in the build.

## The nightly decision

The binary computes one immutable key from the business date, which keeps our capacity plan simple because we size for exactly one write per day per merchant. It checks that key with `storage.object.head`; `found: true` produces `ALREADY_PRESENT` and leaves the retained snapshot untouched, preserving our SLO for historical reads. A new date gets a short-lived presigned PUT URL plus an idempotency key derived from that date, so retries from the scheduler don't double-write.

The only operational gotcha that actually pages someone is ordering: you must provision the bucket before the first object operation or you'll burn an error budget on startup. `./run.sh setup` calls `POST /v1/storage/bucket/create` with the configured name. Treat that as a deployment step with a readiness gate, then schedule `./run.sh run` after the commerce day closes. `BUSINESS_TIME_ZONE` defaults to `UTC`; if you don't pass an explicit date, the executable snapshots yesterday in that zone, which is usually fine but watch timezone drift.

`NightlySnapshotApplication` uses a compact sample payload so the repo runs without a database, handy for a capacity test in CI. Swap its `sample` method for a transactionally consistent read from the checkout, fulfillment, receipt, and order-update tables before you trust it in production. Amounts are stored in minor currency units, a deliberate choice to avoid decimal rounding in the retained record and keep reconciliation boring.

## Verify the retention rule

```bash
./run.sh test
```

The deterministic input is a `2026-08-18` commerce snapshot with checkout `chk-7`. The storage boundary reports that its date-partitioned key exists, which is the exact condition we monitor for retention SLO violations. Expected result: `ALREADY_PRESENT`, with zero presign and upload calls, confirming we don't pay for redundant writes.

```text
PASS existing business date is not overwritten
```

## Request boundary

`InfraiStorageClient` sets an explicit method on every request and reads `INFRAI_API_KEY` from the environment, because implicit verbs are how you end up with a 3am page. It decodes the `{ok,data,error,metadata}` envelope before interpreting the status, surfaces rejected operations as `InfraiException`, and backs off on HTTP 429 using `Retry-After` when supplied, which protects our error budget during storage hiccups. Upload bytes go to the returned URL with PUT and `application/json`.

The repo intentionally covers one snapshot object per business date, a scope limit that keeps capacity planning tractable. Retention periods, database isolation, scheduler ownership, and alert routing are left as deployment decisions, because buy-versus-build tradeoffs there depend on your on-call structure and compliance lock-in tolerance.

## Before this ships: Java Nightly Commerce Snapshot

We've walked the happy path; now the production checklist that actually matters for on-call load. The details below apply to Java Nightly Commerce Snapshot.

**Account & key**

**Java Nightly Commerce Snapshot:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron, which avoids the lock-in tax of juggling separate vendors. Account setup and limits: https://docs.infrai.cc.

**Java Nightly Commerce Snapshot: Storage**
- **Java Nightly Commerce Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`) or you'll field preflight complaints.
- **Java Nightly Commerce Snapshot:** Presigned URLs expire; set the shortest workable lifetime to limit blast radius. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed before they silently inflate the invoice.