# POS tab (`:feature:tabs:pos`)

Offline-first point of sale: scan, check out, save locally as **PENDING**, and let
WorkManager upload the sale once the device is online.

> Replaces the old TVMaze Search epic. The 3rd tab is now `AppDestinations.POS`.

## Flow

```
Scanner ──scan──▶ cart ──Checkout──▶ Pay
                                      │
                    Room: INSERT header + lines (one @Transaction), status = PENDING
                                      │
                    WorkManager: unique "pos_sync", APPEND_OR_REPLACE, NetworkType.CONNECTED
                                      │  (waits here while offline, survives kill/reboot)
                                      ▼
                    PosSyncWorker ─POST v1/transactions─▶ MockPosInterceptor
                                      │
       201/200 ─▶ SYNCED      409 ─▶ CONFLICT      other 4xx ─▶ FAILED      IO/5xx ─▶ retry (exp. backoff, max 5)
                                      │
                    Receipt screen observes the row through a Room Flow, so it updates live
```

## Fake endpoint (served by `MockPosInterceptor`)

Base URL is `https://pos.mock.local/`. Requests never leave the device: the interceptor
answers them itself. WorkManager's CONNECTED constraint still checks the device's real
network state, so turning on airplane mode is a real offline test.

### `POST v1/transactions`

Headers:

| Header | Required | Notes |
|---|---|---|
| `Idempotency-Key` | yes | v4 UUID created once per sale at checkout and stored in Room. Every retry sends the same key. |
| `Content-Type` | yes | `application/json` |

Body:

```json
{
  "clientTransactionId": "8c1f…",
  "createdAtMillis": 1761748000000,
  "currency": "USD",
  "totalCents": 1798,
  "items": [
    { "barcode": "012345678905", "name": "Popcorn (L)", "unitPriceCents": 899, "quantity": 2 }
  ]
}
```

Responses:

| Code | When | Body |
|---|---|---|
| 201 | New sale accepted, stock decremented | `{"reference":"TXN-1A2B3C4D","status":"ACCEPTED","receivedAtMillis":…}` |
| 200 | Same `Idempotency-Key` seen before | Same reference, `"status":"DUPLICATE"` |
| 409 | Central stock can't cover an item | `{"error":"INVENTORY_EXHAUSTED","conflictingBarcode":"490000000016","clientTransactionId":"…"}` |
| 400 | Missing key or unreadable body | `{"error":"MISSING_IDEMPOTENCY_KEY"}` / `{"error":"BAD_REQUEST"}` |
| 503 | Random, about 20% of calls | `{"error":"SERVICE_UNAVAILABLE"}` |
| 404 | Any other path | `{"error":"NOT_FOUND"}` |

Mock stock is kept in memory and resets when the process restarts. Every item starts at
99, except **Hot Dog (`490000000016`), which starts at 1**.

## Catalog (`ProductCatalog`)

| Barcode | Item | Price |
|---|---|---|
| 012345678905 | Popcorn (L) | $8.99 |
| 036000291452 | Nachos | $7.49 |
| 042100005264 | Soda (M) | $4.99 |
| 070847811169 | Candy Bar | $3.25 |
| 490000000016 | Hot Dog | $5.99 |
| 885909950805 | Movie Ticket | $14.99 |

`SimulatedBarcodeScanner` picks a random item after about 700ms. About 10% of scans
return `000000000000`, which isn't in the catalog, so the unknown-item path gets used too.

## Edge cases handled

### 1. Sold elsewhere while this terminal was offline

Terminal A sells the last hot dog while offline. Terminal B (or the website) sells the
same hot dog while online. When A reconnects, central stock is 0.

- The shelf is the source of truth, and the customer has already left with the item. We
  don't lock anything or block the sale at checkout time. This is a saga-style system:
  accept the sale locally, then let the backend decide.
- The backend returns a specific **409 `INVENTORY_EXHAUSTED`**, not a generic 500.
- `PosSyncWorker` marks the row **CONFLICT** (with `statusReason` and `conflictBarcode`)
  and **does not retry**. Retrying can't fix a business-rule rejection, and backoff would
  loop on it forever.
- The UI shows a "need attention" chip on the scanner and **ACTION REQUIRED** on the
  receipt.
- Resolution is the **compensating transaction**: "Void & refund" sets the row to
  **VOIDED**. The row is never deleted, so it stays for audit. Two other real-world
  options are only described here, not built: ship-to-home from the distribution center,
  and a manager override when stock is physically found.
- Idempotency is checked **before** stock. If a sale the backend already accepted gets
  retried after its response was lost, it gets 200 DUPLICATE, not a false 409.

To demo it: turn on airplane mode, sell one hot dog, sell another hot dog, then turn
airplane mode off. The first sale syncs and the second goes to CONFLICT.

### 2. Idempotency key

- `UUID.randomUUID()` (v4) is generated **once per sale** in
  `OfflineFirstPosRepository.checkout`. This is the scheme Stripe, Square and Adyen
  document for their APIs.
- The key is **stored in Room** (`idempotencyKey` column), not generated per request.
  A retry after a timeout, process death or reboot resends the same key, so the backend
  can dedupe instead of recording the sale twice.
- It has its own column, separate from the primary key, so the key scheme can change
  later without a migration on `id`.

### Other details

- Header and line items are written in one Room `@Transaction`, so the worker never sees
  a sale with no items.
- Line items save the name and price at the moment of sale. A later catalog price change
  doesn't rewrite past sales.
- Money is stored as `Long` cents everywhere. No `Double`.
- This database is separate from core's `AppDatabase`. That one is a disposable cache
  that allows destructive migration. These are unsynced sales, so a bad migration should
  crash rather than wipe them.
- `APPEND_OR_REPLACE` instead of `KEEP`: with KEEP, a sale made while the worker is
  already running would be missed, because the worker has already read its PENDING list.
- Opening the tab calls `syncPending()`. This picks up rows that were left PENDING
  because the process died between the insert and the enqueue, or because the worker
  gave up after 5 attempts.
- The POS module has its own OkHttp/Retrofit client, so the mock can't intercept TMDB
  calls.

## Not built yet (follow-ups)

- A real scanner: CameraX + ML Kit barcode scanning, or a vendor SDK, behind `BarcodeScanner`.
- A real payment step. Pay currently just records the sale. On a real terminal the card
  is authorized at Pay, which is why a later stock conflict needs a refund, not just a
  cancel.
- A list of all CONFLICT/FAILED sales. Today the chip counts them, but only the
  just-completed sale's receipt shows one.
- Ship-to-home and manager-override flows.
- Pruning old SYNCED rows on a schedule.
- `work-testing` + Robolectric tests for `PosSyncWorker`, and Room DAO tests.

## Talking points

- **Why Room before network?** The sale has to survive an app kill. Room makes it durable
  immediately, and WorkManager makes the retry durable across reboots.
- **Retry vs. reject:** IO errors and 5xx get retried with backoff. 4xx doesn't. 409 is a
  business problem that needs a person, not a technical one.
- **Why idempotency?** At-least-once delivery plus a server-side dedupe key gives you
  effectively-once.
- **Conflict resolution:** the backend is the source of truth. The terminal saves the
  conflict and shows it, and a person resolves it with a compensating action.
