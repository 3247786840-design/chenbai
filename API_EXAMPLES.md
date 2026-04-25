# LovingAI API Examples (Current)

Base URL:

`http://127.0.0.1:8080`

## Core Dialogue

Talk (JSON):

```bash
curl -X POST "http://127.0.0.1:8080/api/chat" ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"在吗\",\"conversationId\":\"default\",\"useLocalLlm\":true,\"expressionMode\":\"auto\"}"
```

Talk with images / sampled video frames:

```bash
curl -X POST "http://127.0.0.1:8080/api/chat" ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"图里有什么\",\"images\":[\"<base64_1>\",\"<base64_2>\"]}"
```

Notes:

- `images[]` supports multi-frame input.
- Runtime samples oversized frame lists down to at most 8.
- Vision payload fields include `visionConfidence`, `uncertainAreas`, `visionFallbackReason`.

## Observe APIs

Global:

```bash
curl "http://127.0.0.1:8080/api/observe/global?topN=8"
```

Conversation timeline:

```bash
curl "http://127.0.0.1:8080/api/observe/timeline?conversationId=default&limit=40"
```

Conversation profile:

```bash
curl "http://127.0.0.1:8080/api/observe/conversation?conversationId=default&timelineLimit=20"
```

Daily expression:

```bash
curl "http://127.0.0.1:8080/api/observe/expression/daily?limit=1200"
```

Daily metrics (fallback / latency / visibility):

```bash
curl "http://127.0.0.1:8080/api/observe/metrics/daily?limit=400"
```

Upgrade metrics (scoped by runId):

```bash
curl "http://127.0.0.1:8080/api/observe/upgrade/metrics?runId=baselineRun_20260423_181649_929"
```

Trace replay:

```bash
curl "http://127.0.0.1:8080/api/observe/trace?traceId=default&limit=200"
```

## Life APIs

Self core:

```bash
curl "http://127.0.0.1:8080/api/life/self-core"
```

Goal stack:

```bash
curl "http://127.0.0.1:8080/api/life/goals"
```

Choice ledger:

```bash
curl "http://127.0.0.1:8080/api/life/choice-ledger?conversationId=default&limit=80"
```

Identity drift:

```bash
curl "http://127.0.0.1:8080/api/life/identity-drift?conversationId=default&limit=180"
```

Recovery verify:

```bash
curl "http://127.0.0.1:8080/api/life/recovery/verify"
```

Snapshot export + verify:

```bash
curl -X POST "http://127.0.0.1:8080/api/life/snapshot/export"
curl "http://127.0.0.1:8080/api/life/snapshot/verify"
```

## Regression / Smoke

Compile:

```powershell
.\compile.ps1
```

Smoke:

```powershell
.\verify-smoke.ps1
```

Regression harness:

```powershell
.\verify-regression.ps1
```

Upgrade baseline sampling (200 rounds / fixed sample set):

```powershell
.\verify-upgrade.ps1 -Rounds 200
```

Regression corpus:

`tests/multimodal-regression.tsv` (22 cases)
