# Testing checklist

- [ ] Fresh install — config + empty account store created
- [ ] Add first account via Microsoft login (browser + device code)
- [ ] Add second account; both appear with cached heads
- [ ] Switch between accounts on title screen
- [ ] Join multiplayer after switch (disconnect first)
- [ ] Restart Minecraft — last account restored when enabled
- [ ] Expire/refresh — Refresh button reauthenticates
- [ ] Remove account — confirmation required
- [ ] Corrupt `accounts.json` — mod starts with empty/partial store, no crash
- [ ] Update config — new keys added, existing values kept
- [ ] Change skin on minecraft.net — head updates after cache expiry / hash change
- [ ] Offline / no network — cached heads still show; auth errors are surfaced
