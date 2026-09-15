# Provider Extractor Parity Checkpoint — 2026-09-15

## Status

🟢 Extractor parity cluster is green on Android Build.

Latest commit: `0bf662276951f3164248b9277a4bbb81f1f6203a`

Latest Android Build run: `34949335076` — completed successfully.

## Parity completed

The following extractor behavior was audited against `Test/main` and restored where a meaningful behavior difference was found:

- OtakudesuServer — mirror AJAX flow / nonce / nested mirrorstream / base64 JSON / external host handling restored.
- GenericEmbed — request headers, protocol-relative media detection, escaped HTML/URI normalization, depth handling restored.
- GenericDirect — parity implementation restored.
- SamehadakuEpisode — embed request headers aligned while preserving browser resolver injection.
- KrakenFiles — request `Accept` header and HTML entity decoding restored.
- JavascriptMedia — audited; no functional change required.
- OtakudesuHost — audited; no functional change required.
- PixelDrain — audited; no functional change required.

## Browser fallback boundary

Browser fallback is kept outside provider core through `BrowserStreamResolver` injection.

Android `WebViewBrowserStreamResolver` lives under the Android-specific provider package and is wired through:

`ProviderPlaybackResolver → ProviderFactory → OtakudesuProvider / SamehadakuProvider → StreamResolver`.

This avoids putting app/UI dependencies into provider core.

## Integration audit findings

`vider` is intentionally a standalone Android library module, while `Test` is the Android application module.

Safe compatibility points:

- Provider models are semantically identical.
- `AnimeProvider` contract is semantically identical.
- `ProviderPlaybackResolver` public behavior remains compatible; browser resolver is an optional extension.
- Java/Kotlin target is 17 in both projects.
- Both use compileSdk 35.
- OkHttp 4.12.0 and Jsoup 1.18.3 are already used by the app and provider.

Required adaptation before integration:

- Package namespace changes from `com.kakaanime.app.provider` in `Test` to `com.kakaanime.provider` in `vider`.
- `vider` must be consumed as a library; it must not become the app module.
- Android WebView resolver remains an Android boundary and should not leak into UI code.
- App-facing imports/call sites must be migrated deliberately rather than mass-replaced blindly.
- Provider E2E remains manual-only and is not a routine validation gate.

## Integration strategy

Do not copy provider files into `Test` as a second provider implementation.

First integrate `vider` as the single provider library source, then adapt the app-facing boundary/imports. Keep `Test/main` as the integration/release safety net until Android Build passes and manual Provider E2E proves real playback.

## Explicit non-goals

- No SmartProviderRouter parallelization yet.
- No broad resolver refactor.
- No speculative provider rewrite.
- No claim that playback is proven until manual E2E reaches actual first-frame playback.
