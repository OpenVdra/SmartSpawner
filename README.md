<div align="center">

<img src="docs/public/banner.png" alt="SmartSpawner banner" width="520">

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png)](https://modrinth.com/plugin/smartspawner)
[![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_64h.png)](https://hangar.papermc.io/Nighter/SmartSpawner)
[![ghpages](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_64h.png)](https://docs.smartspawner.site/)

Spawners that generate drops and experience without spawning mobs, for **Paper** and **Folia**.

</div>

## Overview
* **Virtual Drops:** Drops and experience go straight into the spawner's storage, without spawning a single mob.
* **Spawner Menu:** Open storage, collect experience and manage the stack from one menu. Storage is paged, sortable, filterable and sellable.
* **Stacking:** Right-click a spawner with another of the same type to stack it, or sneak to add a whole stack at once. Drops and experience scale with the stack.
* **Spawner Types:** Mob spawners, item spawners that generate materials such as diamonds, and custom spawners with their own loot and name.
* **Admin Tools:** Browse every spawner by world, teleport to it, edit or remove it, and log actions to files or Discord.

Every feature is explained on the [documentation site](https://docs.smartspawner.site/).

<div align="center">
<table>
  <tr>
    <td align="center"><img src="docs/public/gui-showcase.gif" alt="The spawner menu, its storage pages, sorting and taking all items" width="372"></td>
    <td align="center"><img src="docs/public/screenshots/spawner-hologram.webp" alt="A stack of 129 zombie spawners with a hologram showing its stored experience and items" width="372"></td>
  </tr>
</table>
</div>

## Requirements

| Minecraft | Java | Server software |
| :--- | :---: | :--- |
| 26.1 – 26.2 | 25 | Paper, Folia, Purpur or compatible forks |
| 1.21.6 – 1.21.11 | 21 | Paper, Folia, Purpur or compatible forks |

One jar runs on every version listed. The ExcellentEconomy integration needs Java 25, as ExcellentEconomy itself does.

## Compatibility
All integrations are soft dependencies: nothing is required, and each one turns on only when its plugin is installed.

| Function | Plugins | What SmartSpawner does |
| :--- | :--- | :--- |
| **Shop prices** | <img alt="EconomyShopGUI icon" src="docs/public/icons/integrations/economyshopgui.png" width="20" height="20" align="absmiddle"> [EconomyShopGUI](https://docs.smartspawner.site/docs/integrations/shops/economyshopgui)<br><img alt="ShopGUI+ icon" src="docs/public/icons/integrations/shopguiplus.png" width="20" height="20" align="absmiddle"> [ShopGUI+](https://docs.smartspawner.site/docs/integrations/shops/shopguiplus)<br><img alt="zShop icon" src="docs/public/icons/integrations/zshop.png" width="20" height="20" align="absmiddle"> [zShop](https://docs.smartspawner.site/docs/integrations/shops/zshop) | Prices the items sold from spawner storage |
| **Economy** | <img alt="Vault icon" src="docs/public/icons/integrations/vault.png" width="20" height="20" align="absmiddle"> [Vault](https://docs.smartspawner.site/docs/integrations/shops/vault)<br><img alt="ExcellentEconomy icon" src="https://cdn.modrinth.com/data/r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp" width="20" height="20" align="absmiddle"> [ExcellentEconomy](https://docs.smartspawner.site/docs/integrations/shops/excellenteconomy) (Java 25) | Pays players when they sell |
| **Protection and claims** | <img alt="WorldGuard icon" src="https://cdn.modrinth.com/data/DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp" width="20" height="20" align="absmiddle"> [WorldGuard](https://docs.smartspawner.site/docs/integrations/protections/worldguard)<br><img alt="GriefPrevention icon" src="https://cdn.modrinth.com/data/O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp" width="20" height="20" align="absmiddle"> [GriefPrevention](https://docs.smartspawner.site/docs/integrations/protections/griefprevention)<br><img alt="Lands icon" src="docs/public/icons/integrations/lands.jpg" width="20" height="20" align="absmiddle"> [Lands](https://docs.smartspawner.site/docs/integrations/protections/lands)<br><img alt="Towny icon" src="https://cdn.modrinth.com/data/Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp" width="20" height="20" align="absmiddle"> [Towny](https://docs.smartspawner.site/docs/integrations/protections/towny)<br><img alt="Residence icon" src="docs/public/icons/integrations/residence.jpg" width="20" height="20" align="absmiddle"> [Residence](https://docs.smartspawner.site/docs/integrations/protections/residence)<br><img alt="RedProtect icon" src="https://cdn.modrinth.com/data/loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp" width="20" height="20" align="absmiddle"> [RedProtect](https://docs.smartspawner.site/docs/integrations/protections/redprotect)<br><img alt="SimpleClaimSystem icon" src="https://cdn.modrinth.com/data/80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp" width="20" height="20" align="absmiddle"> [SimpleClaimSystem](https://docs.smartspawner.site/docs/integrations/protections/simpleclaimsystem)<br><img alt="FactionsUUID icon" src="docs/public/icons/integrations/factionsuuid.png" width="20" height="20" align="absmiddle"> [FactionsUUID](https://docs.smartspawner.site/docs/integrations/protections/factionsuuid)<br><img alt="BlockLocker icon" src="https://hangarcdn.papermc.io/avatars/project/3666.webp?v=1" width="20" height="20" align="absmiddle"> [BlockLocker](https://docs.smartspawner.site/docs/integrations/protections/blocklocker) | Checks region and claim permissions before a spawner is opened, stacked or broken |
| **Islands and plots** | <img alt="SuperiorSkyblock2 icon" src="docs/public/icons/integrations/superiorskyblock2.png" width="20" height="20" align="absmiddle"> [SuperiorSkyblock2](https://docs.smartspawner.site/docs/integrations/islands/superiorskyblock2)<br><img alt="BentoBox icon" src="https://cdn.modrinth.com/data/aBVLHiAW/2ad201b237497093c4957095fa9f97bf61708ed1_96.webp" width="20" height="20" align="absmiddle"> [BentoBox](https://docs.smartspawner.site/docs/integrations/islands/bentobox)<br><img alt="IridiumSkyblock icon" src="https://cdn.modrinth.com/data/uVMG0MzO/1db3be2f57ed47d7b0dfd30902c437768c34d966.png" width="20" height="20" align="absmiddle"> [IridiumSkyblock](https://docs.smartspawner.site/docs/integrations/islands/iridiumskyblock)<br><img alt="PlotSquared icon" src="docs/public/icons/integrations/plotsquared.png" width="20" height="20" align="absmiddle"> [PlotSquared](https://docs.smartspawner.site/docs/integrations/islands/plotsquared)<br><img alt="minePlots icon" src="docs/public/icons/integrations/mineplots.svg" width="20" height="20" align="absmiddle"> [minePlots](https://docs.smartspawner.site/docs/integrations/islands/mineplots) | Applies island and plot permissions to spawner actions |
| **Skills** | <img alt="AuraSkills icon" src="https://cdn.modrinth.com/data/uDdZAVls/aadaedbbb7f8c142a625a11081aa0d9bdfafecc2.jpeg" width="20" height="20" align="absmiddle"> [AuraSkills](https://docs.smartspawner.site/docs/integrations/auraskills) | Turns claimed spawner experience into skill XP |
| **Custom mobs** | <img alt="MythicMobs icon" src="https://cdn.modrinth.com/data/NLuc7Jjh/80ddd8e4535a8305eac4edfd5ced1e8e5344e11d_96.webp" width="20" height="20" align="absmiddle"> [MythicMobs](https://docs.smartspawner.site/docs/integrations/mythicmobs) | Drops spawner items from MythicMobs drop tables |

Setup for each plugin is in the [integrations guide](https://docs.smartspawner.site/docs/integrations/).

## Configuration
* **Files:** `plugins/SmartSpawner/`. `config.yml` for the main settings, `spawner_mobs.yml` and `spawner_items.yml` for loot, `sell_integration.yml` for selling, `activity_log.yml` for logging.
* **Menus and text:** Every message and menu text is editable in `language/` and `gui_layouts/`, with MiniMessage colours.
* **Storage:** SQLite by default, MySQL or MariaDB for networks.
* **Reference:** Every key is documented on the [configuration page](https://docs.smartspawner.site/docs/configuration).

## Commands
Aliases: `/ss`, `/spawner`, `/smartspawner`. Each command has its own permission, listed on the [permissions page](https://docs.smartspawner.site/docs/permissions).

| Command | Description |
| :--- | :--- |
| `/ss give <player> <spawner> [amount]` | Give a mob, item or custom spawner |
| `/ss give <player> vanilla <mob> [amount]` | Give a vanilla spawner |
| `/ss list` | Browse, teleport to and manage every spawner |
| `/ss near [radius]` | Highlight nearby spawners through walls |
| `/ss editloot <spawner>` | Edit a spawner's loot in game |
| `/ss set <stack_size\|range\|delay> <value>` | Change the spawner being looked at |
| `/ss prices` | Show the sell price of every spawner drop |
| `/ss hologram` | Turn holograms on or off |
| `/ss language <locale>` / `/ss gui_layout <layout>` | Switch language or menu layout |
| `/ss reload` | Reload every config file |

Placing and opening a spawner need no permission at all. If players cannot place spawners, another plugin is blocking it.

## For Developers

**Java API:** Create spawner items, read spawner data and listen to spawner events. Add it from [JitPack](https://jitpack.io/#OpenVdra/SmartSpawner/):

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.OpenVdra:SmartSpawner:<version>' }
```

```java
// Give a stack of 5 zombie spawners.
SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
player.getInventory().addItem(api.createSpawnerItem(EntityType.ZOMBIE, 5));

// Only VIPs can stack spawners past 1,000.
@EventHandler
public void onStack(SpawnerStackEvent event) {
    if (event.getNewStackSize() > 1000 && !event.getPlayer().hasPermission("myserver.vip")) {
        event.setCancelled(true);
    }
}
```

See the [Developer API guide](https://docs.smartspawner.site/developer-api/).

## Building
Gradle downloads JDK 25 to compile when it is missing. The plugin jar targets Java 21.

```bash
git clone https://github.com/OpenVdra/SmartSpawner.git
cd SmartSpawner
./gradlew build
```

The plugin jar lands in `core/build/libs/SmartSpawner-<version>.jar`.

| Task | Command |
| :--- | :--- |
| Unit tests | `./gradlew :core:test` |
| Dev server | `./gradlew runServer` |
| Dev client | `./gradlew runClient` |
| Server and client | `./gradlew runDev` |

Contributions are welcome, see [CONTRIBUTING.md](CONTRIBUTING.md). Translation PRs are welcome too.

## Languages

| Language | Code | Contributors | Status |
| :--- | :--- | :--- | :--- |
| English | `en_US` | [Nighter](https://github.com/whoisnighter) | Latest |
| Vietnamese | `vi_VN` | [Nighter](https://github.com/whoisnighter) | Latest |
| Chinese (Simplified) | `zh_CN` | [CTfish0101](https://github.com/CTfish0101), [SnowCutieOwO](https://github.com/SnowCutieOwO) | Latest |
| Turkish | `tr_TR` | berkkorkmaz, [onurrrk](https://github.com/onurrrk) | Latest |
| German | `de_DE` | [jannispkz](https://github.com/jannispkz) | 1.6.7 |
| Italian | `it_IT` | [RV_SkeLe](https://github.com/RVSkeLe) | 1.3.5 |

## Support
* **Bugs and requests:** [GitHub Issues](https://github.com/OpenVdra/SmartSpawner/issues)
* **Community:** [Discord](https://discord.com/invite/FJN7hJKPyb)

## Statistics

Live server and player counts from [bStats](https://bstats.org/plugin/bukkit/SmartSpawner/24822).

[![bStats](https://bstats.org/signatures/bukkit/SmartSpawner.svg)](https://bstats.org/plugin/bukkit/SmartSpawner/24822)

## License
Licensed under the [GPL-3.0](LICENSE).
