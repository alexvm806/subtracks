# Changelog

## Development • playback session restoration

- While the playback controller is connected, Android media-session pauses and same-track seeks request a position checkpoint. Late checkpoints are discarded if the queue changed or the cursor moved to another entry. Repeated checkpoints at the same offset avoid a Room write.
- Reopening a saved session restores it paused. Abrupt termination between checkpoints may lose recent progress.

See [development progress](DEVELOPMENT_PROGRESS.md) for test and build evidence and [earlier milestones](docs/personal-player-roadmap.md).
- Explicitly reshuffling the same album uses its source and library identity to avoid restarting on the current track when alternatives exist.
