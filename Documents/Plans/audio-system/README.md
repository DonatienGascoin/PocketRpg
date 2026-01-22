# Audio System Design

This folder contains the design documentation for the PocketRpg audio system.

## Document Index

| Document | Description |
|----------|-------------|
| [1. Overview](./01-overview.md) | Goals, architecture, package structure |
| [2. Core API](./02-core-api.md) | Audio facade, contexts, handles, playback settings |
| [3. Components](./03-components.md) | AudioSource, AudioListener, AmbientZone |
| [4. Mixing](./04-mixing.md) | AudioMixer, channels, volume configuration |
| [5. Music System](./05-music-system.md) | MusicPlayer, crossfading, streaming |
| [6. Editor Integration](./06-editor-integration.md) | EditorAudio, inspector preview, asset browser |
| [7. Implementation Roadmap](./07-implementation-roadmap.md) | Phased delivery plan |
| [8. Testing Strategy](./08-testing-strategy.md) | Mock backends, test categories |

## Quick Start

For a high-level understanding, read documents 1-3. For editor-specific features (testing audio from inspector), see document 6.

## Technology

- **Backend**: OpenAL via LWJGL (already a project dependency)
- **Formats**: WAV, OGG Vorbis, MP3
- **Pattern**: Static facade + DI context (matches Input, Time, PostProcessing)
