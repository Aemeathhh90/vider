# KakaAnime Provider — Work Rules

## Mandatory pre-flight
Before **any audit, investigation, implementation, refactor, dependency change, or workflow change**:

1. Check the current repository first.
2. Check the current branch/ref and latest commit.
3. Inspect the relevant files, architecture, existing checkpoints, and current tests/workflows.
4. Compare the current state with the last known checkpoint before deciding what to change.
5. Do not assume the repository matches an older conversation, screenshot, or memory.

## Audit → Reference → Fix → Validate

1. Audit the actual current code/logs first.
2. Identify the failing layer and separate proven facts from hypotheses.
3. Search external references only when they can reduce technical uncertainty.
4. Check compatibility with KakaAnime's current architecture before using a reference.
5. Fix the proven root cause; avoid speculative patch stacking.
6. Validate with the smallest relevant test first.
7. Record the result in a checkpoint.

## Provider scope
This repository owns provider/source resolver, discovery, extraction, stream validation, subtitle/language resolution, playback handoff, and provider-specific E2E diagnostics.

## Testing
- Android Build/routine build checks are preferred for normal changes.
- Provider E2E is manual-only and should be used when provider → stream → playback evidence is required.
- Do not dispatch GitHub Actions from the assistant; the user manually starts E2E when requested.
- A provider E2E failure must not block unrelated UI/UX work.
- Provider is green only when the playback contract is actually proven, not merely because compilation succeeds.

## Checkpoints
- Every meaningful milestone gets a clear commit/checkpoint.
- Never overwrite historical diagnosis just to make the latest status look cleaner.
- Record error, evidence, root cause/hypothesis, affected layer, fix, validation, result, and next step when applicable.
- Prefer root-cause fixes over workarounds.

## Integration
Provider changes are integrated into the main KakaAnime repository only at deliberate checkpoints after the provider contract and playback path are verified.
