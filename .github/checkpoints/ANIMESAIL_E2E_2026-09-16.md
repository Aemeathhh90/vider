# AnimeSail E2E Checkpoint — 2026-09-16

## Result

🔴 AnimeSail E2E is not proven.

The latest emulator.wtf run failed at the first assertion:

`AnimeSail search returned no results`

The test did not reach detail, episode, stream, extractor, or Media3 first-frame validation.

## Current interpretation

- Native `AnimeSailProvider` is present and registered.
- The failure is specifically in the live search path for the current AnimeSail endpoint/parser.
- Do not mark AnimeSail playback green from this run.
- Keep this result as a failed provider record for the final multi-provider summary.

## Decision

Per project workflow, pause AnimeSail investigation and continue testing the next provider. Revisit AnimeSail only after the other selected providers have been tested, then compare the failure patterns and decide whether a targeted repair is worthwhile.

## E2E scope

Baseline case: One Piece, Episode 1, HTTP(S) stream, Media3 `onRenderedFirstFrame()`.
