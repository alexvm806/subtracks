# Personal music player roadmap

The Android app uses Compose, Room 3, Paging 3, Coil and a Media3 session backed by ExoPlayer. Sources, queues and downloads retain their existing identifiers. The development APK uses the existing nightly signing configuration. Main is the upstream baseline.

| Phase | Implemented foundation | Remaining implementation and acceptance |
| --- | --- | --- |
| 1 • Android interface | Home, Search, Your Library and Settings in the shell. Mini-player above navigation with horizontal skip and interactive vertical expansion from measured artwork, metadata and play/pause bounds. Greeting, local discovery feed, favourite heart and remaining time. Decoder-input format and sample rate when reported. Seven sleep choices in the Now Playing song menu: 5, 10, 15, 30, 45, 60 minutes or end of track. | Device validation of artwork, metadata and control transitions, reduced-motion preference, configurable Home sections, source-versus-output quality detail, sleep fade. Measure frame times on a device. |
| 2 • Playback | Bounded Media3 queue window, background media session, gapless preparation, network quality settings and ReplayGain. Optional three-second Media3 next-track preloading on unmetered connections. Optional sequential fade-out and fade-in (0–12 seconds per side) on automatic transitions between different albums. No secondary decoder. | Verify the fade envelope and audible transitions on continuous albums and profile buffering on local and remote connections. Test interruptions, unplugging and focus recovery. Prototype true overlapping crossfade and device audio profiles behind opt-in controls. |
| 3 • Lyrics and library | Synced local mirror, library search, artwork and offline downloads. | Capability-gated lyrics and karaoke, indexed fuzzy search, local-only files, import previews, safe playlist editing, export and file cleanup. |
| 4 • Personalisation | Existing recently played, most played, starred and rediscovery sections use actual library data. | Add deduplicated listening events, 30-day On Repeat, Repeat Rewind, mixes, Smart Shuffle and statistics. |
| 5 • Additional experiences | Android media notification and existing queue persistence. | Home widgets, visible-only visualisers, MediaLibraryService browse tree for Android Auto, explicit queue resume and optional authenticated handoff. |
| 6 • Desktop | Platform boundaries documented. | Extract portable Kotlin models, matching and sync logic once Android stabilises. Compose Desktop with native playback engines and installable Fedora x86_64 and Windows x64 packages. |

## Verification

- [x] Previous Android milestone compiled, passed CI and played from the owner's Navidrome server.
- [ ] Verify new navigation with a populated queue, empty queue, on-screen keyboard, TalkBack, and offline mode.
- [ ] Compare startup, memory, scrolling and expansion frame timing on the same device and library as the baseline.
- [ ] Exercise Bluetooth and headphone removal, interrupted streams, lock-screen playback and long queues.
- [ ] When implementing crossfade and timers, verify seek, scrobble deduplication, lyrics alignment and end-of-track stop on a physical device.
