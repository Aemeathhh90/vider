# KakaAnime Provider Migration Manifest

Date: 2026-09-15

## Purpose

Move the provider/streaming subsystem out of the Android app without changing its public behavior first. The existing `Aemeathhh90/Test` repository remains the safety baseline until the separated provider project builds and its proven E2E path is restored.

## Source of truth for migration

Source repo: `Aemeathhh90/Test`

Source package: `app/src/main/java/com/kakaanime/app/provider`

The source provider package currently contains the provider contract, models, registry/factory, routing, stream normalization/selection/deduplication, playback resolution, and concrete provider adapters.

## Phase 1 — migrate without redesign

### Core contract
- `AnimeProvider.kt` — migrate first; public provider contract.
- `ProviderModels.kt` — canonical provider-side models.
- `NormalizedEpisodeStream.kt` — normalized episode result.
- `NormalizedStream.kt` — canonical normalized playback model and `StreamQuality`.

### Registry / orchestration
- `ProviderRegistry.kt`
- `ProviderFactory.kt`
- `ProviderEngine.kt`
- `SmartProviderRouter.kt`
- `ProviderHealth.kt`
- `ProviderResult.kt`

### Resolution / stream pipeline
- `ProviderPlaybackResolver.kt`
- `ProviderStreamDeduplicator.kt`
- `StreamNormalizer.kt`
- `StreamSelector.kt`

### Concrete providers / adapters
- `OtakudesuProvider.kt`
- `OtakudesuWebSource.kt`
- `SamehadakuProvider.kt`
- `RemoteSourceProvider.kt`
- `DemoProvider.kt` — migrate only if still required by tests/development.

### Tests
- `app/src/androidTest/java/com/kakaanime/app/provider/OtakudesuProviderE2ETest.kt`
- `SamehadakuProviderE2ETest.kt`
- `SamehadakuEpisodeDiagnosticsTest.kt`

Provider E2E should be adapted to the separated project after the Android test harness is available. Do not claim provider success from compilation alone.

## Explicitly NOT migrated

- Compose UI/screens.
- `AnimeRepository` implementations that are app-specific unless required as a thin integration adapter later.
- `AnimeData`, `EpisodeData`, `KakaAnimePreferences`, library/favorite/social/profile state.
- `MainActivity` and navigation.
- Billing, ads, diamonds, premium UI.
- Theme/UI assets.
- App-specific Media3 screen implementation.

## Known issues to fix only after the clean migration builds

1. Duplicate stream model exists in app data layer vs provider model layer. Provider repo must have one canonical `ProviderStream` contract.
2. `ProviderAnimeRepository.getLatestUpdates()` calls `searchAnime("")`, while `SmartProviderRouter.search()` rejects blank queries. Latest-updates behavior needs a real provider operation or an explicit integration-level implementation.
3. `RemoteSourceProvider` currently assigns `language = Japanese` and `subtitleLanguage = Indonesian` to discovered streams instead of deriving them from provider metadata. Do not preserve this as a trusted truth in the new contract.
4. `SmartProviderRouter` currently performs provider calls sequentially. Parallel/fast-first discovery is a later optimization, not part of the first migration.
5. `ProviderPlaybackResolver` currently tries candidate anime/providers sequentially. Preserve behavior during migration; redesign only after baseline E2E is green.
6. A standalone `StreamValidator` class was not found at the expected provider package path during audit. The checkpoint says validation is conceptually part of the architecture, so its actual implementation/location must be traced before declaring it migrated.

## Migration gates

### Gate A — structure
- [ ] Provider repo has a clean Android/Kotlin buildable structure.
- [ ] Provider package compiles independently.
- [ ] No UI dependency leaks into provider core.

### Gate B — baseline behavior
- [ ] Search works.
- [ ] Anime/detail lookup works.
- [ ] Episode discovery works.
- [ ] Stream discovery returns normalized candidates.
- [ ] Selection honors quality/premium rules.
- [ ] Headers are preserved.

### Gate C — playback proof
- [ ] E2E reaches stream resolution.
- [ ] E2E reaches Media3.
- [ ] E2E reaches `onRenderedFirstFrame()`.
- [ ] Only then mark the provider path PASS.

## Rule

Do not perform broad refactors while migrating. First make the separated repository reproduce the existing proven behavior. Then fix the known defects one at a time with a checkpoint after each meaningful green build.
