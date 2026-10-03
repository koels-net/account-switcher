# Version Compatibility

## Four product jars

| Module | Jar | Minecraft | Compile target |
| --- | --- | --- | --- |
| `mc121` | `account-switcher-1.21.4-*.jar` | **1.21.4 – 1.21.8** | 1.21.4 |
| `mc1219` | `account-switcher-1.21.9-*.jar` | **1.21.9 – 1.21.11** | 1.21.9 |
| `mc26` | `account-switcher-26-*.jar` | **26.1 – 26.2** | 26.2 |
| `mc263` | `account-switcher-26.3-*.jar` | **26.3** | 26.3 |

Install **one** jar matching your game version. Do not mix them.

### Why four?

1.21.9 removed the session account-type enum and moved player-skin / GUI list APIs enough that a single 1.21.4→1.21.11 jar became reflection hell. 26.x is a separate unobfuscated toolchain, and 26.3 replaced authlib's auth classes (see below), so it can't share a jar with 26.1–26.2.

### `mc121` notes (1.21.4–1.21.8)

- Still uses a small `compat` layer for mid-range changes (DynamicTexture label, `drawString` shadow, GUI render pipelines on 1.21.6+, click sounds).
- Session uses 6-arg `User` + account type.

### `mc1219` notes (1.21.9–1.21.11)

- Direct 1.21.9 Mojmap APIs: 5-arg `User`, `SkinManager#createLookup`, `PlayerFaceRenderer`, `RenderPipelines.GUI_TEXTURED`, list `renderContent` / `MouseButtonEvent`.
- Mixin accessors on `user` / `userApiService` / `profileKeyPairManager`.

### `mc26` notes (26.1–26.2)

- `ScreenHelper` bridges `Minecraft#setScreen` vs `Gui#setScreen`.
- Heads via `PlayerFaceExtractor` + `ResolvableProfile`.
- Opaque text colors (`UiColors`).

### `mc263` notes (26.3)

- Same code as `mc26`, ported to 26.3's authlib: the `com.mojang.authlib.yggdrasil` package is gone.
  - `YggdrasilAuthenticationService` → `MinecraftServicesDiscoveryService.create(Proxy)`
  - `MinecraftSessionService` → `SessionService`
  - `ProfileResult` moved to `com.mojang.authlib.services`
- Fix bugs in both `mc26` and `mc263` until 26.1–26.2 support is dropped.

## Build

Needs JDK 25.

```bash
./gradlew buildAll      # Linux/macOS
gradlew.bat buildAll    # Windows
```

Outputs under each module’s `build/libs/`.
