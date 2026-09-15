# KakaAnime Provider Repository Audit — 2026-09-15

## Pre-flight evidence

- Repository: `Aemeathhh90/vider`
- Default branch: `main`
- Latest commit before this audit: `8e58755e909261835a71e0306ee1b3a64043ecb7`
- Current repository tree contains only `README.md` and `RULES.md`.
- Therefore this repository is currently a **provider workspace shell**, not yet a migrated provider implementation.
- `README.md` already existed before the rules commit and is preserved.

## Source-of-truth audit target

The actual provider implementation remains in `Aemeathhh90/Test` on `main` at this checkpoint. The provider package currently includes the provider contract, registry/factory, router, engine, playback resolver, provider models, dedicated Otakudesu/Samehadaku adapters, and a multi-gateway `RemoteSourceProvider`. Provider E2E tests currently exist under `app/src/androidTest/.../provider/`.

## Current architecture observed

```text
UI / Repository
    ↓
ProviderPlaybackResolver / ProviderAnimeRepository
    ↓
ProviderEngine
    ↓
SmartProviderRouter
    ↓
ProviderRegistry / ProviderFactory
    ↓
AnimeProvider implementations
    ↓
ProviderStream
    ↓
StreamDeduplicator / StreamNormalizer / StreamSelector
    ↓
Media3 handoff
```

## Findings

### P0 — SPLIT NOT MIGRATED YET

**Classification:** BLOCKED / STRUCTURAL

`vider` contains no Kotlin/Gradle implementation yet. Do not invent a new provider project structure before extracting the exact dependency graph from `Test`.

**Action:** build the provider repository from the proven current source tree, preserving package contracts and test behavior first.

### P0 — DUPLICATE STREAM MODEL

**Classification:** BUG / ARCHITECTURE RISK

`Test` currently contains both:

- `data/ProviderStream.kt` with a `ProviderStream` model using `isM3u8`.
- `provider/ProviderModels.kt` with another `ProviderStream` model using `StreamType`.

The active provider engine imports the provider-package model, while `ProviderAnimeRepository` uses normalized provider models. The duplicate legacy data model must be traced before migration; it must not be blindly copied into `vider`.

**Action:** identify all references and retain one authoritative provider stream contract during migration.

### P1 — LATEST UPDATES PATH IS CURRENTLY INVALID

**Classification:** BUG

`ProviderAnimeRepository.getLatestUpdates()` calls `searchAnime("")`. `SmartProviderRouter.search()` explicitly returns `emptyList()` for a blank query. Therefore this repository method cannot currently produce latest updates through the provider router.

**Action:** audit whether latest updates should be a provider capability (`latestUpdates`) or belong to the catalog/backend layer. Do not patch it with another search query without confirming the intended contract.

### P1 — LANGUAGE METADATA IS HARDCODED IN REMOTE SOURCE

**Classification:** BUG / DATA-CONTRACT RISK

`RemoteSourceProvider.stream()` currently assigns `language = "Japanese"` and `subtitleLanguage = "Indonesian"` to every discovered stream. This is not proof of actual stream language/subtitle availability and conflicts with the project requirement that language availability be based on real provider data.

**Action:** audit provider payloads and stream metadata propagation before changing the model. Unknown must remain unknown when evidence is unavailable.

### P1 — ROUTING IS SEQUENTIAL, NOT FAST/PARALLEL

**Classification:** ENHANCEMENT / PERFORMANCE ARCHITECTURE

`SmartProviderRouter.search()` and `getStreams()` iterate providers with sequential `flatMap` calls. `ProviderPlaybackResolver` also tries candidate anime sequentially. This differs from the desired fast/parallel discovery architecture and can make provider fallback slow.

**Action:** benchmark/trace actual latency first, then design bounded parallel discovery/racing without breaking provider priority or health cooldown semantics.

### P1 — PROVIDER E2E IS INFRASTRUCTURE-COUPLED TO THE APP

**Classification:** STRUCTURAL

Current E2E tests live inside the Android app test source set and exercise provider code through the app module. Moving provider work to `vider` requires deciding whether the new repository remains an Android library/module, a JVM-compatible core, or another testable form. This decision must be made from the current provider dependencies rather than assumed.

## Proven baseline

The existing extractor checkpoint records the Otakudesu streaming path as a proven baseline through Media3 `onRenderedFirstFrame()`. That baseline must be preserved during the split. The current provider E2E infrastructure has also been exercised, but recent failures were instrumentation/emulator failures rather than proof of `NO_STREAM`.

## Migration rule

Do **not** start by rewriting providers.

First extract and preserve:

1. provider contracts/models,
2. registry/factory,
3. routing/health logic,
4. stream normalization/deduplication/selection,
5. dedicated provider adapters,
6. reusable extractors/resolvers,
7. provider tests and diagnostics,
8. only then integration adapters for the main KakaAnime app.

## Next diagnostic step

Audit the complete `Test/app/src/main/java/com/kakaanime/app/provider/` dependency tree and the provider-related Gradle/test dependencies. Produce a migration manifest before copying or refactoring implementation code.

## Status

**Audit result: PARTIAL — repository split foundation is ready, implementation migration is not started.**
