# Get the jar (pick one)

## A. On your PC (needs JDK 21 only; Gradle downloads itself)
    Windows:   gradlew.bat build
    Mac/Linux: ./gradlew build
The mod jar appears in `build/libs/godzilla_mod-1.0.0.jar`. First build takes a few minutes (downloads Minecraft + NeoForge + GeckoLib).
Test it in-game without installing: `gradlew runClient`, then `/summon godzilla_mod:godzilla_minus_one`.

## B. No setup at all: let GitHub build it
1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the repo's **Actions** tab -> the "Build" run -> download the **godzilla_mod-jar** artifact.

## Installing the jar
Minecraft 1.21.1 + NeoForge 21.1.x, and **GeckoLib 4.8.x for NeoForge 1.21.1** (CurseForge/Modrinth) in the same `mods` folder, on both client and server.

See MOD_README.md for how it works, how to swap in your own model, and the few spots that may need a small fix if the first compile complains (this code has not been compiled yet).
