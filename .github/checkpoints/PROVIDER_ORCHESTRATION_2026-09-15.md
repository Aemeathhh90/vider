# Provider Orchestration Checkpoint — 2026-09-15

## Status

🟢 Provider core orchestration baseline migrated.

## Migrated in this stage

- `ProviderRegistry`
- `NormalizedEpisodeStream`
- `ProviderEngine`
- `SmartProviderRouter`

The engine is independent of Android `Log`; diagnostics use stdout.

## Compatibility rule

This stage intentionally preserves the baseline sequential routing behavior from `Test/main`. Parallel/fast-first routing is deferred until the separated baseline is proven.

## Current limitation

Concrete provider adapters are not yet fully migrated/registered. Extractor implementations and playback integration still need to be connected.

## Next stage

Migrate concrete provider adapters and their HTTP/HTML dependencies, starting with the proven dedicated providers. Then add provider-core tests before adapting E2E.

## E2E

Not run. Provider E2E remains a manual special test and is not part of routine migration commits.
