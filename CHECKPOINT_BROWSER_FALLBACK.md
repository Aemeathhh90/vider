# Checkpoint — Browser Fallback Boundary

Date: 2026-09-15

## Green state

- `BrowserStreamResolver` interface exists in provider core.
- `StreamResolver` accepts an optional browser resolver and falls back to it only after extractor/direct validation paths fail.
- `WebViewBrowserStreamResolver` exists in the Android-specific package and is outside provider-core orchestration.
- `ExtractorRegistry` now accepts and forwards the optional browser resolver to `SamehadakuEpisodeExtractor`.
- `SamehadakuEpisodeExtractor` forwards the resolver to its nested host resolver.

## Important boundary

The browser resolver is **not yet wired into the default provider composition** (`OtakudesuProvider` / `SamehadakuProvider` / `ProviderFactory`). The next implementation step must inject the optional resolver from the Android composition root without adding Android/WebView dependencies to provider core.

## Validation rule

Use the normal Android Build for routine validation. Do not run Provider E2E automatically; Provider E2E remains manual-only and is reserved for proving provider -> stream -> playback.

## Next step

Audit and wire the optional browser resolver through the provider constructors/factory, then run Android Build and create a new checkpoint only after green.
