# Version Compatibility

## Three product jars

| Module | Jar | Minecraft | Compile target |
| --- | --- | --- | --- |
| `mc121` | `account-switcher-1.21.4-*.jar` | **1.21.4 – 1.21.8** | 1.21.4 |
| `mc1219` | `account-switcher-1.21.9-*.jar` | **1.21.9 – 1.21.11** | 1.21.9 |
| `mc26` | `account-switcher-26-*.jar` | **26.1 – 26.2** | 26.2 |

Install **one** jar matching your game version. Do not mix them.

### Why three?

1.21.9 removed the session account-type enum and moved player-skin / GUI list APIs enough that a single 1.21.4→1.21.11 jar became reflection hell. 26.x is a separate unobfuscated toolchain.

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

## Build

```bash
gradlew buildAll
```

Outputs under each module’s `build/libs/`.
