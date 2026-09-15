# Provider Orchestration Checkpoint — 2026-09-15

## Status

GREEN — Provider registry/orchestration baseline migrated.

## Migrated

- `ProviderRegistry`
- `ProviderHealth`
- `SmartProviderRouter`
- `ProviderEngine`
- `ProviderFactory`

## Compatibility rule

This stage intentionally preserves the baseline sequential routing behavior from `Test/main`. Parallel/fast-first routing is deferred until the separated baseline is proven.

## Current limitation

No concrete provider adapters are registered yet in the separated repository. The factory accepts providers explicitly so the core remains independent from Android UI and app-specific dependencies.

## Next stage

Migrate concrete provider adapters and their HTTP/HTML dependencies, starting with the proven dedicated providers. Then add provider-core tests before adapting E2E.

## E2E

Not run. Provider E2E remains a manual special test and is not part of routine migration commits.
