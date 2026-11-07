# The Forge (BlastForge)

Paper server plugin for Minecraft 1.21.0–1.21.11 (also Folia, Purpur, Pufferfish), plus its resource pack.
Rare ores (Siftie → Enderite → Wardenite), Blast mining, the Forging Anvil, magic books, and the Legendary Scythe.
GitHub: https://github.com/duckking2091-max/TheForge

## Layout
- `src/main/java/dev/duckmc/blastforge/` – plugin code (`BlastForge.java` is the main class; `expansion/` holds newer features)
- `src/main/resources/` – `paper-plugin.yml` and the YAML configs (config, magic, materials, messages, scythe, villagers, guide)
- `src/test/` – tests; they run on every build
- `resource-pack/` – resource pack **source** (`pack.mcmeta` + `assets/` + version overlays). Supports pack formats 34–75.
- `.tools/` – Python helpers for generating/testing the pack, plus source art in `.tools/art/`
- `docs/` – Modrinth description text
- `release/<version>/` – built jar + pack zip (git-ignored; upload these to GitHub Releases / Modrinth)

## Tools on this PC
- Java 21: `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot`
- Maven: `C:\Users\duckk\Projects\dev-tools\apache-maven-3.9.11\bin\mvn.cmd` (not on PATH)
- Git: `C:\Program Files\Git\cmd\git.exe`

## Build
```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot"
& "C:\Users\duckk\Projects\dev-tools\apache-maven-3.9.11\bin\mvn.cmd" -q -B package
```
Output: `target/TheForge-<version>.jar` (the shaded one; ignore `original-*.jar`).
Test output prints scythe/“save unavailable” log lines — that's normal, not a failure.

## Making a release
1. Bump `<version>` in `pom.xml` (and the README if it mentions versions/SHA-1).
2. Build, then copy the jar into `release/<version>/`.
3. Zip the *contents* of `resource-pack/` (pack.mcmeta at the zip root, forward-slash paths) to `release/<version>/TheForge-pack.zip`.
   PowerShell's `Compress-Archive` writes backslashes — use .NET `ZipFile` with `/` paths instead.
4. Report the pack's SHA-1 (`Get-FileHash -Algorithm SHA1`) — servers need it in `server.properties`.

## Notes
- Last built release: 1.4.0 (pack SHA-1 `01f73ab43a6f2af6d118a479f01710d3ca6d7fd8`). GitHub Releases only has up to 1.0.1 and Modrinth's pack 1.1.0, so newer versions must be uploaded by the user (needs their login).
- The user isn't very technical: explain steps plainly and commit + push after changes so nothing is lost again (their PC was wiped once). Pushing may need the user to sign in to GitHub themselves.
