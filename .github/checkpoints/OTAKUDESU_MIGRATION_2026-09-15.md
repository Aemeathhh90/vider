# Otakudesu Migration Checkpoint — 2026-09-15

## Status

YELLOW — concrete Otakudesu provider baseline migrated; validation still pending.

## Migrated

- Canonical `ProviderAnime` / `ProviderEpisode` models.
- `OtakudesuWebSource` with multi-domain discovery and episode HTML parsing.
- `OtakudesuProvider` with primary + legacy API fallback.
- Web episode playback URL discovery is connected to the separated `StreamResolver`.

## Compatibility

The provider contract remains the same as `Test/main`: search, anime detail, episodes, and streams. No fast-first routing or broad refactor was introduced during migration.

## Known limitation

Provider-core currently has only the generic direct extractor. Host-specific extractors and BrowserMediaResolver are still pending. Therefore an embed page may resolve to no playable stream until those extractors are migrated.

## Validation

Android/Gradle Build has not yet been run after this migration. Provider E2E is intentionally not run; it remains a manual special test.

## Next

1. Add the minimum Otakudesu host/embed extractors required by the migrated web flow.
2. Run normal Provider Android Build.
3. Add focused provider-core tests.
4. Then migrate Samehadaku.
