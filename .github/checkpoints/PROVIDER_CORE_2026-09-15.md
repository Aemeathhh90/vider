# Provider Core Migration Checkpoint — 2026-09-15

## Status

🟢 Phase 1 core contract migration started and committed on `main`.

## Migrated

- `AnimeProvider` provider contract.
- Canonical `ProviderAnime`, `ProviderEpisode`, `ProviderStream`, `StreamType`, and `EpisodeAvailability` models.
- `NormalizedStream`, `StreamQuality`, and metadata cache.
- `NormalizedEpisodeStream`.
- `ProviderResult` and `ProviderHealth`.
- `StreamSelector`.
- `ProviderStreamDeduplicator`.
- `StreamNormalizer`.

## Project structure

- Android/Kotlin provider library module: `provider-core`.
- Java/Kotlin target: 17.
- compileSdk: 35.
- Dependencies prepared for coroutines, OkHttp, and Jsoup.

## Deliberately not migrated yet

- Provider registry/factory/engine/router.
- Playback resolver.
- Concrete provider adapters.
- Provider E2E tests.
- App integration and Media3.

## Validation state

This checkpoint records source migration only. A green build has **not** been claimed yet because the repository has no Gradle wrapper committed at this stage.

## Next step

Migrate registry/orchestration next, then validate the project structure/build before moving concrete providers. Do not run Provider E2E automatically.
