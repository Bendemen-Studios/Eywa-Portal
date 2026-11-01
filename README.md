# EywaPortal

**EywaPortal** is a free, open-source portal-transition plugin for EywaMC. It is designed to work alongside EssentialsX and Paper 26.3, with a later separate client-side overlay for Java and Bedrock players.

## Current status

This is the initial foundation, not yet the finished cinematic transition.

- Intercepts player-issued `/warp <name>` commands and runs the original command after a configurable countdown.
- Shows an EywaMC-style title/countdown.
- Plays a spiral of native Minecraft particles around the player and an arrival burst.
- Optional Darkness effect to approximate a screen transition.
- Configurable messages, particle choices, countdown and sounds.
- EssentialsX still resolves the warp destination and checks its normal permissions.

### Important limitation

A true full-screen animated portal image is **not included yet**. It requires client assets: Java resource-pack/font or UI techniques and a separate Bedrock resource-pack/UI implementation for Geyser. Titles and Darkness are only a temporary fallback, not a true fullscreen image overlay.

VEngine is an optional planned integration. This first version uses native Paper particles so it does not depend on an unverified VEngine API. Direct VEngine effect playback will be added after confirming the installed VEngine command/API.

## Requirements

- Paper 26.3
- Java 25 (confirm the Java runtime required by your exact Paper build)
- EssentialsX for `/warp` functionality
- Geyser is optional; client-specific full-screen overlays are not implemented yet

## Build

```bash
gradle build
```

The plugin JAR will be in `build/libs/`.

## Install

1. Build the project with Gradle.
2. Put `EywaPortal-0.1.0-SNAPSHOT.jar` in the server's `plugins/` directory.
3. Restart the server.
4. Adjust `plugins/EywaPortal/config.yml`.
5. Test with `/eywaportal test` (operator only).

## Commands

- `/eywaportal test` — preview the current particle/title effect.
- `/eywaportal reload` — reload the config.

## Notes

- `/warp <name>` is intercepted for player commands only. Console-issued commands are unaffected.
- `/warp` without a destination remains unchanged.
- This initial implementation uses the native particle API and does not require VEngine to be installed.
- Test on a staging server before enabling it on a live server. Teleport commands and other plugins may have their own delays or restrictions.

## License

MIT. See [LICENSE](LICENSE).
