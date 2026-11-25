# Development progress

Previous Android phases and discovery milestones continue in [the personal player roadmap](docs/personal-player-roadmap.md). See [the architecture](docs/architecture.md) for the Room, Paging, Compose and Media3 boundaries.

## Quality-of-Life Update

Source audit against optimised development build 29 (`fc82b82`). Status describes the requested behaviour as a whole. For every row, the audit alone changed documentation only, ran no feature-specific tests, produced no new binary and has no runtime cost. Build, test, file and performance evidence for implementation increments is recorded below. Hardware acceptance remains open.

| Spec | Feature | Status | Existing components to reuse | Limitations and outstanding acceptance |
| --- | --- | --- | --- | --- |
| 3 | Session restoration | In progress | Room cursor and queue, paused restore, 15 s position checkpoints | Capture system pause and seek; validate speed, reboot and reattachment. |
| 4 | Undo actions | In progress | Controller undo for removal and reorder | Undo skips with position, clear, playlist and favourite edits; bound history. |
| 5 | Persistent shuffle | In progress | Queue seed, original order and cursor | Test process recreation, edits and a full cycle on large playlists; explicit reshuffle. |
| 6 | Automatic recovery | In progress | Playback service and stall watchdog | Classify transient failures; test bounded retry and cancellation on pause. |
| 7 | Offline switching | In progress | Download repository, offline queue window | Verify complete matching file, mid-stream fallback and position retention. |
| 8 | Lock-screen favourite | Not started | Media3 session and server favourites | Connect session action, offline journal and replay; verify system placement. |
| 9 | Song row swipes | Not started | Song actions and queue operations | Configurable gestures, thresholds and vertical-scroll cancellation. |
| 10 | Artwork swipes | Not started | Existing next/previous controller | Gesture cancellation, artwork motion and rapid-input handling. |
| 11 | Mini-player expansion | In progress | Measured artwork bounds and interactive expansion | Validate cancellation, reduced motion and frame timing on device. |
| 12 | Browse restoration | In progress | Navigation, paging and preferences | Check each route's scroll, filters, sort and layout after back and recreation. |
| 13 | Long-press actions | In progress | Existing library and playback actions | Audit action availability per type, add contextual shortcuts. |
| 14 | Custom player controls | Not started | Existing player controls and overflow | Persist user choices and order; restore defaults. |
| 15 | Haptics | Not started | Compose interactions | System-aware toggle and discrete gesture feedback. |
| 16 | Edge-to-edge | In progress | Compose shell and mini-player | Validate cutouts, IME, rotation and three-button navigation. |
| 17 | Local/Tailscale selection | Not started | Source identity and network mode | Verify alternate endpoints before sharing credentials; test switching. |
| 18 | Headphone profiles | In progress | ReplayGain and quality preferences | Recognise output or select manually; apply settings without volume jumps. |
| 19 | Smart downloads | Not started | Existing download repository | Ownership, bounded scheduling, deduplication and protected cleanup. |
| 20 | Battery/data modes | In progress | Network-specific quality and preload policy | Independent opt-in controls, background limits and overrides. |
| 21 | Offline startup | In progress | Room mirror, cached art and local sources | Measure launch with unreachable server and verify background resync. |
| 22 | Cache cleanup | In progress | Separate downloads and artwork storage | Inventory cache ownership, size limits and eviction protections. |
| 23 | Track multi-select | Not started | Paged library and playlist actions | Efficient selection and cancellable batch operations. |
| 24 | Alphabetical scrollbar | Not started | Paged library and ordering | Sort-aware sections and fast scroll across scripts. |
| 25 | Home pins | Not started | Home discovery and source identifiers | Persist shortcuts, artwork, reorder and missing-item handling. |
| 26 | Recommendation exclusions | Not started | Local discovery queries | Store exclusions, apply to generated results and provide reversal. |
| 27 | Find in playlists | Not started | Room playlist membership | Indexed reverse lookup and navigation with source identity. |
| 28 | Playlist folders | Not started | Existing playlist identities | Local folder persistence, ordering and backup. |
| 29 | Automatic playlist artwork | In progress | Coil and existing artwork refs | Membership-aware cover cache and user overrides. |
| 30 | Duplicate warnings | In progress | Source IDs and import deduplication | Recording-aware classification and explicit batch policy. |
| 31 | Audio signal path | In progress | Media3 encoding and ReplayGain | Separate source, decoder, processing and known output properties. |
| 32 | GitHub update checker | Not started | Nightly and stable packaging | Opt-in release check, identity, signing and checksum validation. |
| 33 | In-app changelog | Not started | Release metadata | Bundle offline notes and add version history UI. |
| 34 | Settings backup | Not started | DataStore and existing models | Versioned SAF export, preview, validation and secret exclusion. |

### Performance baseline and test conditions

Build 29's GitHub artifact ZIP was 9,004,075 bytes. Compare the same optimised variant, signing and archive type. Installed size, startup, RAM, CPU, frame timing, search latency, battery, network bytes and server request counts still need a matched Android device, library, playback route and network. Compare offline and remote playback separately. APK archive size alone cannot establish a runtime regression or improvement.

### Phase A • playback position checkpoints

Changed `PlaybackController.kt`, `PlayerHandle.kt`, `Media3PlayerHandle.kt`, `QueueRepository.kt` and `PlaybackControllerTest.kt`. The existing Media3 listener handles pause and same-track seek events while connected. The controller captures queue version, index and track ID; Room checks the durable cursor before changing the offset. Identical offsets skip the write. There is no new dependency, polling loop, schema or background service. Existing 15-second playback checkpoints and paused restoration remain in use.

Added regression coverage for a stale checkpoint after the cursor moves, and a paused-state assertion on restoration. CI and APK measurements pending. Service-only checkpointing without a UI controller, playback speed, prior active-state metadata and physical-device process death, reboot and network acceptance remain outstanding. No runtime performance measurements yet.
