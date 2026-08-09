# github-key-leaked-scanner

Research tool (Java / Spring Boot) that measures how often EVM/BNB-style raw private
keys (32-byte hex) show up accidentally in public GitHub pushes, and how much of that
exposure is real: it can resolve the address for a detected key and check its live
on-chain balance.

- Matched secrets are never stored in full — only a redacted snippet
  (`abcdef...1234`) plus metadata (repo, file path, confidence tier).
- Balance checks only ever read public on-chain state for an address derived from an
  already-detected key. No private key is ever logged, persisted, or used to sign or
  move funds — see `crypto/` (`AbstractChainResolver`, `Eth`, `RpcManagement`,
  `Web3ConnectionPool`).

## How it works

1. Downloads an hour of [GH Archive](https://www.gharchive.org/) events
   (`https://data.gharchive.org/YYYY-MM-DD-H.json.gz`) and filters `PushEvent`s
   (`GhArchiveClient`).
2. GH Archive's `PushEvent` payload only contains `{ref, before, head}` (GitHub
   dropped the per-commit list some time ago) — so each sampled push is diffed
   in one request via GitHub's unauthenticated compare-patch endpoint:
   `github.com/{repo}/compare/{before}...{head}.patch` (`PatchFetcher`). Pushes to
   a brand-new ref (`before` = all-zero SHA) fall back to
   `github.com/{repo}/commit/{head}.patch`.
3. Added lines (`+...`) in the diff are scanned for 64-hex-char strings
   (`PrivateKeyDetector`), tagged with a confidence tier:
   - **high** — `0x`-prefixed hex *and* a nearby keyword (`private_key`, `wallet`,
     `mnemonic`, `bnb`, `bsc`, `metamask`, ...)
   - **medium** — has the `0x` prefix *or* the keyword, not both
   - **low** — bare 64-hex with no context (almost always a SHA-256/commit hash,
     kept only for tuning false-positive rate)
4. Stats + redacted matches are written to a local text file, one per scanned hour,
   at `data/scans/YYYY-MM-DD-HH.txt` (zero-padded hour) — `ScanResultStore`.

## Balance checking

For a detected key, `IChain` implementations (`Eth`, `Btc`) derive the corresponding
address entirely offline and can look up its live balance:

1. `RpcManagement` sources candidate JSON-RPC endpoints per chain from
   [chainlist.org](https://chainlist.org/rpcs.json), filters to `https://` URLs, and
   health-checks candidates with a live `eth_blockNumber` call before handing one out.
   A failed endpoint gets a short cooldown rather than a permanent blacklist. Repeated
   lookups round-robin across the known candidates instead of always probing the same
   one first.
2. `Web3ConnectionPool` caches one Web3j client per chain (`Chain.ETH_MAINNET`, ...),
   reused until a request through it fails, at which point it's dropped and the next
   lookup rotates to a different endpoint.
3. `Eth.retrieve(privateKey)` resolves the EIP-55 address and calls `eth_getBalance`
   through the pooled connection, returning a `BalanceResultDto` (chain, address,
   balance) — the private key itself is never attached to that result.

BTC balance-checking isn't implemented yet — `Btc.chain()` reports the ecosystem, but
`retrieve()` is still the unimplemented base-class stub.

## Running

Requires a JDK 21 and Maven (or use the same JDK via `JAVA_HOME`).

```bash
mvn spring-boot:run
# or
mvn -DskipTests package && java -jar target/github-key-leaked-scanner-0.1.0.jar
```

The app listens on `:8080`.

### Trigger a scan

```bash
# scan one UTC hour (sample is a fraction 0-1 of push events to actually diff)
curl -X POST "http://localhost:8080/api/scans?date=2026-08-07&hour=3&sample=0.02"

# scan a range of hours on one UTC date
curl -X POST "http://localhost:8080/api/scans/range?date=2026-08-07&fromHour=0&toHour=23&sample=0.01"
```

Start with a small `sample` (e.g. `0.01`-`0.05`) — a full hour has 100k+ push events,
and each sampled one costs an HTTP request to `github.com`.

### Read results

```bash
# aggregate leak-rate stats across everything scanned so far
curl "http://localhost:8080/api/report"

# list stored (redacted) matches
curl "http://localhost:8080/api/matches?minConfidence=medium"
curl "http://localhost:8080/api/matches?hour=2026-08-07-3&minConfidence=low"
```

## Configuration (`src/main/resources/application.yml`)

| property | default | meaning |
|---|---|---|
| `scanner.data-dir` | `./data` | where the archive cache and per-hour result files live |
| `scanner.sample-rate` | `0.02` | default fraction of push events to diff when not overridden per-request |
| `scanner.concurrency` | `6` | parallel patch fetches |
| `scanner.request-delay-ms` | `150` | delay before each fetch, per concurrency slot |
| `scanner.max-retries` | `3` | retry attempts on transient/429/403 responses |
| `scanner.scheduled.enabled` | `false` | turn on the recurring scan job |
| `scanner.scheduled.cron` | `0 5 * * * *` | when enabled, scans the UTC hour that just completed |

## Notes / limitations

- Diffing is unauthenticated (no GitHub token needed) via `github.com/.../*.patch`,
  which isn't subject to `api.github.com` rate limits but can still be throttled by
  GitHub under high request volume — lower `concurrency` / raise `requestDelayMs` if
  you see a spike in fetch errors.
- Only the tip-vs-before diff is inspected; force-pushes or pushes that rewrite history
  in unusual ways may under- or over-count.
- `0x`-prefixed 64-hex strings without key-related context (the "medium" tier) also
  catch ordinary EVM transaction/block hashes — expect false positives there; "high"
  tier requires both the prefix and a keyword nearby.
- BIP39 mnemonic phrases aren't detected yet (would need the wordlist to avoid noise
  from ordinary prose) — only raw hex private keys.
- `data/` (archive cache + per-hour scan text files) is gitignored; delete it freely
  to start fresh.
- Balance checking depends on chainlist.org for RPC candidates and third-party public
  nodes for the actual `eth_getBalance` calls — if both are unreachable, `Eth.retrieve()`
  throws rather than silently returning a stale/zero balance.
