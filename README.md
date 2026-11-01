# EywaPortal

**EywaPortal** is a free, open-source portal transition plugin for EywaMC, designed for Paper 26.3 alongside EssentialsX.

## Current features

- Intercepts player-issued /warp <name> commands and runs the original EssentialsX command after a configurable countdown.
- EywaMC-style countdown titles, spiral particles, sounds and arrival burst.
- A Java fullscreen overlay animation using a custom-font glyph resource pack.
- Darkness/title fallback when the Java overlay pack is not loaded.
- Bedrock players use the compatible title, particle, sound and darkness fallback by default; when Unison is installed, EywaPortal also attempts the custom-font overlay for Bedrock.
- Configurable colors/messages, countdown, particle choices and sounds.

## Requirements

- Paper 26.3
- Java 25
- EssentialsX for /warp
- VEngine is optional but recommended for the animated organic particle portal. Copy vengine/effects/eywa_portal.yml into plugins/VEngine/effects/ and run /ve reload.
- For the fullscreen Java overlay: merge the generated resource pack into the Java pack you already distribute (for example through RSPM).
- Geyser/Floodgate are optional. Unison is recommended for Bedrock players if you want EywaPortal to attempt the custom-font overlay; without Unison, Bedrock uses the fallback effects.

## Build

Run:

    gradle build

The plugin JAR is created in build/libs/.

To build the Java overlay resource pack:

    python -m pip install cairosvg
    python tools/build-resource-pack.py

The ZIP is created at build/eywaportal-java-resourcepack.zip. GitHub Actions builds both outputs and publishes them as the eywaportal-build artifact on each workflow run.

## Install

1. Download the EywaPortal JAR from the GitHub Actions artifact and put it in plugins/.
2. Merge the contents of eywaportal-java-resourcepack.zip into the existing Java resource pack. Keep your current pack.mcmeta compatible with your client version; merge the assets/ directory rather than replacing your whole pack.
3. Ensure your merged pack is distributed by your existing resource-pack setup (such as RSPM) and that Java players accept it.
4. Restart the server and edit plugins/EywaPortal/config.yml as desired.
5. Test with /eywaportal test (operator only).

## Commands

- /eywaportal test — preview portal particles and transition.
- /eywaportal reload — reload configuration.

## Important limitations

- The fullscreen image overlay uses a Java custom font and only activates after the player reports the server resource pack loaded. The custom font assets must be merged into the actual pack the client receives.
- Bedrock's UI/resource-pack format differs from Java's custom fonts. When Unison is detected, EywaPortal allows the same overlay animation to run for Floodgate players, assuming the merged pack is reported as loaded and Unison can render the font image. Without Unison, Bedrock stays on the compatible fallback. This integration must be verified in-game on your exact Unison/RSPM versions.
- If VEngine is installed and the eywa_portal effect file has been copied in, EywaPortal triggers it through VEngine's /ve play command. Native Paper particles remain as a fallback.
- Test on a staging server before using this on a live network. This plugin intercepts player-issued warp commands; console-issued commands are unaffected.

## License

MIT.
