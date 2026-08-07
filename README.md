# Account Switcher (Fabric)

Vanilla-style Microsoft account switching — **two separate jars** for the two Minecraft toolchains.

| Jar | Minecraft | Java | Loom |
|---|---|---|---|
| `account-switcher-1.21-*.jar` | **1.21.4 – 1.21.11** | 21+ | Remap (obfuscated) |
| `account-switcher-26-*.jar` | **26.1 – 26.2** | 25+ | No remap (unobfuscated) |

## Build

```bat
gradlew.bat buildAll
```

Outputs:

- `mc121/build/libs/account-switcher-1.21-1.0.0+1.21.jar`
- `mc26/build/libs/account-switcher-26-1.0.0+26.jar`

Or build one line:

```bat
gradlew.bat :mc121:build
gradlew.bat :mc26:build
```

## Install

Put **only one** jar in your mods folder, matching your Minecraft version. Do not install both at once.

## Features

- Microsoft device-code login (browser opens; no token pasting)
- Hot account switch without restarting
- AES-256-GCM encrypted local token storage
- Skin/head cache with hash deduplication
- Favorites, search, sort, remove with confirmation
- Auto token refresh + remember last account
- Title screen + multiplayer entry points

## Storage

Shared across both mod jars (same paths):

```
.minecraft/config/account-switcher.json
.minecraft/config/account-switcher/
  accounts.json
  encryption.dat
  cache/skins/
  cache/heads/
```

## Project layout

```
mc121/   → 1.21.4–1.21.11 (compiled against 1.21.4)
mc26/    → 26.1–26.2 (compiled against 26.1)
docs/    → versioning, hot-switching, testing
```

See [docs/VERSIONING.md](docs/VERSIONING.md) and [docs/HOT_SWITCHING.md](docs/HOT_SWITCHING.md).
