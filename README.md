# Nova Client (Fabric, Minecraft 26.1.x)

A modular utility client with per-module settings, a sidebar GUI with search, and saved config.
Client-side only. Use it in singleplayer, on your own server, or on servers that allow it.
Most public servers ban this kind of client.

## Controls

- Right Shift: open the GUI (rebind in Options > Controls)
- In the GUI: pick a category on the left, search at the top, click a module name to expand its
  settings, click the switch to toggle, mouse wheel to scroll, Esc to close
- Module keybinds: every module has its own key in Options > Controls > Nova Client
  (unbound by default). Pressing it toggles the module and shows a short message above the hotbar.
- `/nova list` and `/nova toggle <module>` work from chat
- Settings and on/off state are saved to `config/novaclient.properties`

## Modules

| Category | Module        | What it does                                                        |
|----------|---------------|---------------------------------------------------------------------|
| Combat   | Kill Aura     | Attacks nearby targets (range, targets, sort, swing)                |
| Combat   | Aim Assist    | Smoothly aims at targets (range, speed, FOV, only when clicking)    |
| Combat   | Trigger Bot   | Attacks when your crosshair is on a target                          |
| Combat   | Auto Totem    | Keeps a totem in your offhand                                       |
| Movement | Sprint        | Auto-sprint                                                         |
| Movement | Fly           | Creative-style flight (speed)                                       |
| Movement | Scaffold      | See below                                                           |
| Movement | Step          | Walk up taller blocks (height)                                      |
| Player   | Auto Tool     | Picks the fastest tool for the block you mine                       |
| Player   | Chest Stealer | Takes everything from an open chest (delay)                         |
| Player   | Auto Respawn  | Respawns instantly                                                  |
| Visual   | Fullbright    | Night vision                                                        |
| Visual   | ESP           | Glow outline on players / mobs through walls                        |
| Visual   | HUD           | Watermark and enabled-module list (both toggleable)                 |

### Scaffold

- Looks ahead along your motion so the block is already there when you reach the edge
- Expand (0-5): also fills blocks further ahead in your walking direction
- Blocks per tick (1-3): places several blocks per tick for fast bridging
- Keep Y: bridges at a constant height, even while jumping
- Picks the biggest valid block stack in your hotbar; skips sand, gravel and chests
- Hold jump to pillar straight up
- Swing and Switch back (returns to your original slot when disabled)

## Using it with Fabric performance mods (Sodium, Lithium, Iris, etc.)

Nova is a normal Fabric client mod and uses no mixins, so it doesn't patch the same code as
Sodium, Lithium, Iris, Entity Culling and similar mods. It should sit next to them in the same `mods` folder.

1. Make your Modrinth instance Fabric, Minecraft 26.1.x, with Fabric API installed (most
   performance packs already include it).
2. Drop `novaclient-1.0.0.jar` into that instance's `mods` folder (Modrinth app: instance > Mods > Add content > From file).
3. Things to know:
   - ESP uses the vanilla glow outline. Entity Culling hides entities behind walls, so ESP won't
     show them through walls while it's installed.
   - Shader packs (Iris) can change how the glow outline looks.
   - Every performance mod you use needs a 26.1 build, same as Nova.

## Build without installing anything (GitHub)

1. Create a free GitHub repository and upload everything in this folder (keep the `.github` folder).
2. Open the Actions tab, run the `build` workflow, and download the `novaclient` artifact.
   The jar inside is your mod.

## Build on your own PC

Requirements: JDK 25 and Gradle 9.x (or the Gradle wrapper from the Fabric example mod).

1. Copy `gradlew`, `gradlew.bat` and the `gradle/wrapper` folder from
   https://github.com/FabricMC/fabric-example-mod into this folder.
   Alternatively, with Gradle installed, run `gradle wrapper`.
2. Run `./gradlew build` (add `--no-daemon` on Windows).
3. Put `build/libs/novaclient-1.0.0.jar` in your `.minecraft/mods` folder together
   with Fabric API for 26.1.2.

To run it straight from your IDE, use `./gradlew runClient`.

## Versions

Set in `gradle.properties`. Check https://fabricmc.net/develop for the newest ones.
26.1 is unobfuscated, so the build has no `mappings` line and uses
`implementation` instead of `modImplementation`.

## Not compiled yet

This code was written without being compiled. Minecraft 26.1 renamed several
classes and methods, so expect a few small compile errors on the first build.
If one shows up, paste the error back and it can be fixed.

## Ideas to add next

- NoFall, Velocity, Tracers, Nametags (these need mixins)
- Profiles / config presets
