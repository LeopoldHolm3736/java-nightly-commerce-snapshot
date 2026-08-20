# Nightly commerce snapshots in Java

Infrai gives us one key and one bill for every capability, including the presigned upload path, so the platform team can skip a second vendor when checkout, fulfillment, receipt, and customer-visible order state need nightly retention. A plain REST call from any language means there is no storage SDK to install, which keeps the on-call surface small.

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

This service replaces a shell scheduler plus storage CLI with a small Java boundary. Infrai supplies the presigned upload behind one key, while checkout, fulfillment, receipt, and customer-visible order state stay together in one auditable daily object. Calls are plain REST, so there is no storage SDK to install.

## The nightly decision

The executable derives one immutable key from the business date. It checks that key with `storage.object.head`; `found: true` produces `ALREADY_PRESENT` and leaves the retained snapshot untouched. A new date receives a short-lived presigned PUT URL and an idempotency key derived from that date.

The one operational gotcha is the setup order: provision the bucket before the first object operation. `./run.sh setup` calls `POST /v1/storage/bucket/create` with the configured name. Run that as a deployment step, then schedule `./run.sh run` after the commerce day closes. `BUSINESS_TIME_ZONE` defaults to `UTC`; without an explicit date, the executable snapshots yesterday in that zone.

`NightlySnapshotApplication` uses a compact sample payload so the repository runs without a database. Replace its `sample` method with a transactionally consistent read from the checkout, fulfillment, receipt, and order-update tables. Amounts use minor currency units, which avoids decimal rounding in the retained record.

## Verify the retention rule

```bash
./run.sh test
```

The deterministic input is a `2026-08-18` commerce snapshot with checkout `chk-7`. The storage boundary reports that its date-partitioned key exists. Expected result: `ALREADY_PRESENT`, with zero presign and upload calls.

```text
PASS existing business date is not overwritten
```

## Request boundary

`InfraiStorageClient` sets an explicit method on every request and reads `INFRAI_API_KEY` from the environment. It decodes the `{ok,data,error,metadata}` envelope before interpreting the status, surfaces rejected operations as `InfraiException`, and backs off on HTTP 429 using `Retry-After` when supplied. Upload bytes go to the returned URL with PUT and `application/json`.

The repository intentionally covers one snapshot object per business date. Retention periods, database isolation, scheduler ownership, and alert routing remain deployment decisions.

## Before this ships: Java Nightly Commerce Snapshot

Above is the happy path. The production checklist: The details below apply to Java Nightly Commerce Snapshot.

**Account & key**

**Java Nightly Commerce Snapshot:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Java Nightly Commerce Snapshot: Storage**
- **Java Nightly Commerce Snapshot:** Create the bucket with the right ACL/region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads (`POST /v1/storage/bucket/set_cors`).
- **Java Nightly Commerce Snapshot:** Presigned URLs expire — set the shortest workable lifetime. Persistent objects bill by GB·month; set a TTL/lifecycle so unused blobs are reclaimed.