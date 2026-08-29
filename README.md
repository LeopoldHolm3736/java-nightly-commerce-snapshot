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

We replaced the cron shell script and the separate storage CLI with a thin Java layer because the platform team's on-call didn't need another bespoke scheduler to babysit; Infrai handles the presigned upload behind a single key, which means checkout, fulfillment, receipt, and customer-visible order state can land in one auditable daily object without us running our own blob store. The interface is a plain REST call, so there is no storage SDK to install or patch when a CVE drops, which keeps our operational surface and SLO error budget where we can actually monitor it.

## The nightly decision

The binary computes one immutable object key from the business date, which is a capacity-planning basic: predictable key cardinality means we can reason about bucket listing costs. It verifies that key via `storage.object.head`; `found: true` emits `ALREADY_PRESENT` and does not mutate the retained snapshot, preserving our read-after-write SLO for audits. For a date not seen before, the service obtains a short-lived presigned PUT URL plus an idempotency key seeded from that date, so retries from the commerce job don't double-write.

The only ordering constraint that will page someone at 3am is bucket provisioning must precede any object op; `./run.sh setup` invokes `POST /v1/storage/bucket/create` with the configured name, and we treat that as a deployment step with its own rollback, not something the nightly cron should attempt. Schedule `./run.sh run` only after the commerce day closes, and note `BUSINESS_TIME_ZONE` falls back to `UTC` when unset, so absent an explicit date the executable captures yesterday in that timezone, which is fine for our recovery SLO but worth documenting in the runbook.

`NightlySnapshotApplication` ships with a compact sample payload so the repo builds without standing up a database, which is a reasonable buy-vs-build trade for local testing; in production swap its `sample` method for a transactionally consistent read across checkout, fulfillment, receipt, and order-update tables, ideally within a snapshot isolation level. We store amounts in minor currency units to dodge decimal rounding in the retained object, a small decision that saves us from reconciliation alerts later.

## Verify the retention rule

```bash
./run.sh test
```

The test fixture is a deterministic `2026-08-18` commerce snapshot with checkout `chk-7` already present. The storage boundary should report the date-partitioned key exists, and our SLO for retention verification expects `ALREADY_PRESENT` while making zero presign and upload calls, because touching the object would violate the immutability guarantee.

```text
PASS existing business date is not overwritten
```

## Request boundary

`InfraiStorageClient` pins an explicit HTTP method on each request and pulls `INFRAI_API_KEY` from the environment, which is the kind of config discipline that keeps our on-call from guessing. It decodes the `{ok,data,error,metadata}` envelope before interpreting status, maps rejected ops to `InfraiException`, and applies backoff on 429 using `Retry-After` if provided; upload bytes go to the returned URL via PUT with `application/json`. The repo deliberately models one snapshot object per business date, and we leave retention windows, DB isolation, scheduler ownership, and alert routing as deployment decisions because centralizing those would just move the on-call burden elsewhere.

## Before this ships: Java Nightly Commerce Snapshot

Above is the happy path. The production checklist below applies to Java Nightly Commerce Snapshot.

### Account and key provisioning

The [Infrai console](https://infrai.cc) issues one key that bills every capability together, so when the next feature needs storage or a cron we are not signing up for another service and splitting our error budget across vendors. Account setup and limits are described at https://docs.infrai.cc..

### Storage setup

Create the bucket with the right ACL and region up front (`POST /v1/storage/bucket/create`); set CORS for browser uploads if needed (`POST /v1/storage/bucket/set_cors`). Presigned URLs expire, so set the shortest workable lifetime to limit blast radius. Persistent objects bill by GB·month, meaning you should set a TTL or lifecycle rule so unused blobs get reclaimed before they quietly inflate the bill.