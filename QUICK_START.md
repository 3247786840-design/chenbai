# Quick Start (Current)

## 1) Compile

```powershell
cd <repo_root>\LovingAI
.\compile.ps1
```

Expected:

`OK: compiled to .\build`

## 2) Run

HTTP server only:

```powershell
java -cp build com.lovingai.LivingAI
```

Or one-click starter:

```powershell
.\start.ps1
```

## 3) Verify

Smoke:

```powershell
.\verify-smoke.ps1
```

Regression:

```powershell
.\verify-regression.ps1
```

Life-feel pack (fast/balanced/deep three latency profiles):

```powershell
.\verify-lifefeel.ps1
```

## 4) Open UI / Check APIs

- Talk page: `http://127.0.0.1:8080/talk`
- Status: `http://127.0.0.1:8080/api/status`
- Observe global: `http://127.0.0.1:8080/api/observe/global`
- Trace replay: `http://127.0.0.1:8080/api/observe/trace?traceId=default&limit=50`

## 5) Optional JVM flags

Examples:

```powershell
$env:LOVINGAI_JAVA_OPTS="-Dlovingai.proactive.selfSolveTimeoutSec=60 -Dlovingai.observe.verifyAlignment=false"
.\start.ps1
```

## 6) Notes

- Runtime package path is `src/com/lovingai/...` and main class is `com.lovingai.LivingAI`.
- Long user pastes (e.g. novel excerpts) plus parallel auxiliary calls can queue LM Studio: set `latencyProfile=deep` in `data/localai.properties`, and if timeouts persist, cap concurrency with e.g. `-Dlovingai.auxLlm.threads=4`.
- Current generate timeout profile defaults are `fast=180s`, `balanced=240s`, `deep=360s` (`defaultGenerateTimeoutSecByProfile`).
- Long-input behavior switches:
  - `-Dlovingai.actionLoop.suppressOnVeryLong=false` restores the full「行动闭环」block on 800+ character turns (default is suppress).
  - `-Dlovingai.dialogue.stripAuxOnVeryLong=true` re-enables stripping auxiliary expressive sections on very long input.
  - `-Dlovingai.dialogue.veryLongLocalCompress=true` re-enables local post-compression for very long high-priority responses.
- Column gate is already wired into circle candidate selection and emits `pillar_gate_applied`.
- Snapshot APIs are available:
  - `POST /api/life/snapshot/export`
  - `GET /api/life/snapshot/verify`
