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
- Local tokens encrypted at rest (AES-256-GCM), key protected by the Windows OS keystore (DPAPI) — protects against accidental leaks of the accounts file, not against malware on your own machine (same as the vanilla launcher)
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

## Security

Access/refresh tokens are encrypted at rest with AES-256-GCM. The random data key lives in
`encryption.dat`, itself wrapped so the file is useless when copied on its own:

- **Windows:** the key is protected with the OS keystore (DPAPI / `CryptProtectData`), which ties
  decryption to your Windows login account. Copying `encryption.dat` (and `accounts.json`) to
  another user or machine leaves it undecryptable.
- **Other platforms / DPAPI unavailable:** a machine-derived key is used as a fallback. Weaker, but
  still keeps a copied `accounts.json` alone unusable.

This protects against accidental or casual leaks of the accounts file. It does **not** protect
against malware or anyone running as your own user account — such an attacker can obtain the key
alongside the data, exactly as they could read the vanilla launcher's session. Treat your
`.minecraft/config/account-switcher/` folder as sensitive.

> If you move installs between Windows accounts/PCs, delete `encryption.dat` on the new machine and
> log in again — the old file cannot be decrypted there by design.

## Project layout

```
mc121/   → 1.21.4–1.21.11 (compiled against 1.21.4)
mc26/    → 26.1–26.2 (compiled against 26.1)
docs/    → versioning, hot-switching, testing
```

See [docs/VERSIONING.md](docs/VERSIONING.md) and [docs/HOT_SWITCHING.md](docs/HOT_SWITCHING.md).
