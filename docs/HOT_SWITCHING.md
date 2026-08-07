# Hot Account Switching

## What works without restart

| Action | Result |
| --- | --- |
| Replace `Minecraft.user` (username, UUID, access token, xuid, client id) | Immediate |
| Recreate `UserApiService` | Best-effort |
| Recreate `ProfileKeyPairManager` | Best-effort (chat reporting keys for the new account) |
| Join a multiplayer server after switch | Uses new session |
| Open Realms browser after switch | Uses new session when not already connected |

## What requires leaving the world / reconnect

- Active integrated server / singleplayer world identity
- Active multiplayer connection
- Active Realms session
- Some skins/capes already loaded in the current world

**Recommended flow:** return to the title screen (or at least disconnect), switch accounts, then join again.

## Why a full restart is not required for most cases

Online join validates the access token at connection time via authlib. Updating the client `User` before connecting is sufficient for Mojang/Microsoft session handshake on a fresh connection.
