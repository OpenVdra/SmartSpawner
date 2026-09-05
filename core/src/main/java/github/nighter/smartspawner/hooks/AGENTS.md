# hooks/

All third-party plugin support. Four families, one rule: **every plugin here is optional at runtime**
and the plugin must boot and work with none of them installed.

| Package | What it integrates |
|---|---|
| `protections/` | Claim, region and land plugins. 15 of them |
| `economy/` | Currencies, shop plugins, item prices |
| `bedrock/` | Floodgate, gates the FormUI menus |
| `drops/` | MythicMobs loot |
| `rpg/` | AuraSkills |

## IntegrationManager is the gate

`IntegrationManager` runs **first** in `onEnable`, before anything else, and produces a `hasX` boolean
per plugin (Lombok makes them `isHasX()`). Everything downstream branches on those booleans, never on
`Bukkit.getPluginManager().getPlugin(...)`.

Detection goes through `checkPlugin(name, check, logSuccess)`, which swallows exceptions into a
warning. That is deliberate: a hook that throws must degrade to "not present" instead of aborting
startup. Keep new detections inside that wrapper.

Some detections do more than return a flag: `Lands`, `IridiumSkyblock`, `SuperiorSkyblock2`,
`PlotSquared` and `MythicMobs` construct or register their integration during the check, so the
detection lambda has real side effects. `SimpleClaimSystem` and `SimpleClaimSystem2` both probe the
same plugin name and disambiguate on version prefix (`1.` vs `2.`), so exactly one flag ends up true.

`reload()` only reloads AuraSkills. Protection flags are **not** re-evaluated on `/ss reload`; a
protection plugin installed after startup needs a server restart.

## Protection checks

Every plugin in `protections/api/` implements the `ProtectionHook` interface: `canBreak`, `canStack`,
`canOpenMenu`, each returning `true` to **allow** and defaulting to allow. `IntegrationManager` builds
one hook instance per detected plugin during startup into `getProtectionHooks()`.

Three static entry points, and callers must use them rather than reaching into `protections/api/`:

| Check | Called before | Interface method it iterates |
|---|---|---|
| `CheckBreakBlock.CanPlayerBreakBlock` | breaking a spawner | `canBreak` |
| `CheckStackBlock.CanPlayerPlaceBlock` | stacking or placing | `canStack` |
| `CheckOpenMenu.CanPlayerOpenMenu` | opening any spawner GUI | `canOpenMenu` |

Each returns `true` early for `isOp()` or `hasPermission("*")`, then loops over `getProtectionHooks()`
and fails closed on the first denial. Any new player action on a spawner needs the matching check;
omitting it is a protection bypass.

**Convention: `true` = allow, `false` = deny, for every hook and every entry point.** Do not write a
hook whose method returns true on denial; SuperiorSkyblock2 used to and it was a standing trap.

**Coverage is expressed by which methods a hook overrides.** A plugin that does not police an action
simply leaves that method at the interface default (allow). SuperiorSkyblock2, BentoBox,
IridiumSkyblock and RedProtect do not override `canBreak`, which is why break protection effectively
covers 11 of the 15 plugins. That asymmetry is intentional and now lives in the hooks themselves, not
in a hand-maintained `if` chain.

### Adding a protection plugin

1. Add the dependency to `core/build.gradle.kts` as `compileOnly` (or `implementation` if it must be shaded), plus a repository in the root `build.gradle.kts` if needed.
2. Add a `hasX` field and a `checkPlugin` block in `IntegrationManager`; inside it, `protectionHooks.add(new X(...))` when the plugin is confirmed present.
3. Add `hooks/protections/api/X.java` implementing `ProtectionHook`. Override only the actions the plugin guards (`canBreak` / `canStack` / `canOpenMenu`), returning `true` for allow. Catch the plugin's exceptions locally. No change to the `Check*` classes is needed.
4. Add the plugin to `dependencies.server` in **both** `plugin.yml` and `paper-plugin.yml` with `required: false` and `join-classpath: true`.
5. Add it to the `protection_plugins` bStats chart in `SmartSpawner.setupBtatsMetrics()` if it is a claim plugin.
6. Document it on the docs site and in `CHANGELOG.md` (use the `write-docs` skill).

Never import a third-party class into a class that loads unconditionally. Keep the import confined to
`protections/api/X.java`, whose instance is only constructed when the flag is true, so a missing
plugin cannot throw `NoClassDefFoundError`.

## Economy

Every sell setting lives in **`sell_integration.yml`**, not in `config.yml`. In 1.8.0 the
`sell_integration` section of `config.yml` and the whole of `item_prices.yml` were merged into it, so
paths lost their `sell_integration.` prefix and the prices became its `custom_prices.prices` section.
`SellIntegrationConfigUpdater` owns the file and replays both moves for upgrading servers; it must run
before `ItemPriceManager` is constructed, which is why it sits in `initializeServices()`.

Do not read these keys off `plugin.getConfig()`. `ItemPriceManager` loads the file and exposes it as
`getSellConfig()`; `CurrencyManager` and `ShopIntegrationManager` both go through that.

`ItemPriceManager` is the single entry point for "what is this item worth". It initializes **before**
spawner settings, because loot config reads prices.

Everything is gated on `enabled`. When false, `currencyManager` and `shopIntegrationManager` are never
constructed and stay **null**. `SmartSpawner.hasSellIntegration()` is the safe question to ask.

Prices come from two sources combined by `price_source_mode`:

| Mode | Meaning |
|---|---|
| `CUSTOM_ONLY` | `custom_prices.prices` only |
| `SHOP_ONLY` | the shop plugin only |
| `CUSTOM_PRIORITY` | custom price wins, shop as fallback |
| `SHOP_PRIORITY` | shop wins, custom as fallback (default) |

`validatePriceSourceMode()` downgrades a mode that cannot be satisfied, so the effective mode may
differ from the configured one.

`custom_prices.prices` is a curated section (`ConfigMigrations.SELL_INTEGRATION_PRICES`), so a
material the owner deletes is not added back on the next start. It is also the one section the plugin
writes to at runtime, through `setPrice` / `removePrice`.

`ShopIntegrationManager` picks one `activeProvider` from `providers/`: EconomyShopGUI, ShopGUI+,
zShop. Either honours `shop_integration.preferred_plugin` or auto-detects the first available. A new
shop plugin means implementing `ShopProvider` (`getPluginName`, `isAvailable`, `getSellPrice`) and
adding a case to that switch.

`CurrencyManager` handles the payout side (Vault, ExcellentEconomy, and the `currency` key). Note the
historical rename: `COINSENGINE` migrates to `EXCELLENTECONOMY`, handled in
`updates/ConfigMigrations.CONFIG_VALUES` while that key was still in `config.yml`.

`providers/shopguiplus/SpawnerProvider` runs the other direction: ShopGUI+ asks *us* for spawner
items. It is exposed via `SmartSpawner.getSpawnerProvider()` and constructs a fresh instance per call.

## Bedrock

`FloodgateHook` decides whether a player is on Bedrock. The FormUI classes
(`SpawnerMenuFormUI`) are only constructed when Floodgate is present **and**
`bedrock_support.enable_formui` is true, so treat them as nullable everywhere. That key defaults to
**false** and is only read in `onEnable`, so turning it on needs a restart, not `/ss reload`.
Java-edition behaviour must never depend on Floodgate being loaded.
