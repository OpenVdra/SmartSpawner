<div align="center">

<br>

[![modrinth](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_64h.png)](https://modrinth.com/plugin/smartspawner)
[![hangar](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/hangar_64h.png)](https://hangar.papermc.io/Nighter/SmartSpawner)
[![ghpages](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/documentation/ghpages_64h.png)](https://docs.smartspawner.site/)
[![discord-plural](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/social/discord-plural_64h.png)](https://discord.com/invite/FJN7hJKPyb)

</div>

## How It Works
* **Virtual Drops:** Spawners generate drops and experience straight into their own storage, without spawning a single mob.
* **Player Range:** A spawner only works while a player is nearby, like vanilla.
* **Inspired by DonutSMP:** The spawner style of [DonutSMP](https://www.youtube.com/@DonutSMPServer), on any Paper or Folia server.

## Spawner Menu
* **One Menu:** Right-click a spawner to open its storage, collect its experience and manage its stack.
* **Paged Storage:** Take single stacks or everything at once, sort by item, and filter out drops a player does not want.
* **Selling:** Sell the whole storage in one click through a [supported shop or economy plugin](https://docs.smartspawner.site/docs/integrations/shops/).
* **Layouts:** Every slot and text can be changed, and presets such as DonutSMP are included.

<div align="center">
<table>
  <tr>
    <th align="center">Spawner menu</th>
    <th align="center">Storage</th>
  </tr>
  <tr>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/screenshots/gui-main.png" alt="The menu of a stack of 129 zombie spawners, with storage, stack and experience buttons" width="352"></td>
    <td align="center"><img src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/screenshots/gui-storage.png" alt="Page 1 of 2 of a zombie spawner's storage, full of rotten flesh, with sort, filter, sell and take-all buttons" width="352"></td>
  </tr>
</table>
</div>

<p align="center">
  <img src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/gui-showcase.gif" alt="Opening a skeleton spawner, browsing its storage, sorting it and taking every item" width="600">
</p>

## Stacking
* **By Hand:** Right-click a spawner with another of the same type to stack it. Sneak to add the whole stack in your hand.
* **Stack Menu:** Add or remove spawners in exact amounts from the spawner menu.
* **Scaling:** Drops, experience and storage grow with the stack. 100 spawners give 100 times the drops.

<p align="center">
  <img src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/spawner-stacking.gif" alt="Stacking skeleton spawners by hand, then in the stack menu" width="600">
</p>

## Holograms
* **At a Glance:** Floating text above each spawner shows its stack size, stored experience and items.
* **Toggle:** Turn them on or off with `/ss hologram`. Position, alignment and text are configurable.

<p align="center">
  <img src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/screenshots/spawner-hologram.webp" alt="A stack of 129 zombie spawners with a hologram showing 19.4K of 129K experience and 42 of 5.8K items" width="600">
</p>

## Spawner Types
* **Mob Spawners:** Every vanilla mob, each with its own loot and experience in `spawner_mobs.yml`.
* **Item Spawners:** Generate materials such as diamonds or iron, set up in `spawner_items.yml`.
* **Custom Spawners:** Any number of extra spawners for the same mob or item, each with its own loot and name.
* **Vanilla Spawners:** Normal Minecraft spawners that spawn real mobs, for mixed setups.
* **In-game Loot Editor:** Change any spawner's loot with `/ss editloot`, no file editing needed.

## Admin Tools
* **Spawner List:** Browse every spawner by world, filter active and inactive ones, teleport to them, change them or remove them.
* **Nearby Scanner:** `/ss near` highlights spawners through walls, visible only to the player who ran it.
* **Action Logging:** Record who placed, broke, stacked or sold, to log files or a Discord webhook.

<p align="center">
  <img src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/spawner-list.gif" alt="Picking a world in the spawner list, filtering its spawners and opening one to manage it" width="600">
</p>

## More Features
* **Mineable Spawners:** Require Silk Touch or certain pickaxes, set drop chances, and break up to 64 from a stack while sneaking.
* **Hoppers:** When turned on in `config.yml`, hoppers under a spawner pull items out of its storage.
* **Database:** SQLite by default, MySQL or MariaDB for networks that share spawner data.
* **Languages:** Every message and menu text is editable, with MiniMessage colours, and can follow each player's game language.

## Plugin Compatibility

All integrations are optional and turn on only when their plugin is installed.

<table>
  <tr>
    <th>Function</th>
    <th>Plugins</th>
    <th>What SmartSpawner does</th>
  </tr>
  <tr>
    <td><b>Shop prices</b></td>
    <td><img alt="EconomyShopGUI icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/economyshopgui.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/shops/economyshopgui">EconomyShopGUI</a><br><img alt="ShopGUI+ icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/shopguiplus.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/shops/shopguiplus">ShopGUI+</a><br><img alt="zShop icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/zshop.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/shops/zshop">zShop</a></td>
    <td>Prices the items sold from spawner storage</td>
  </tr>
  <tr>
    <td><b>Economy</b></td>
    <td><img alt="Vault icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/vault.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/shops/vault">Vault</a><br><img alt="ExcellentEconomy icon" src="https://cdn.modrinth.com/data/r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/shops/excellenteconomy">ExcellentEconomy</a> (Java 25)</td>
    <td>Pays players when they sell</td>
  </tr>
  <tr>
    <td><b>Protection and claims</b></td>
    <td><img alt="WorldGuard icon" src="https://cdn.modrinth.com/data/DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/worldguard">WorldGuard</a><br><img alt="GriefPrevention icon" src="https://cdn.modrinth.com/data/O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/griefprevention">GriefPrevention</a><br><img alt="Lands icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/lands.jpg" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/lands">Lands</a><br><img alt="Towny icon" src="https://cdn.modrinth.com/data/Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/towny">Towny</a><br><img alt="Residence icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/residence.jpg" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/residence">Residence</a><br><img alt="RedProtect icon" src="https://cdn.modrinth.com/data/loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/redprotect">RedProtect</a><br><img alt="SimpleClaimSystem icon" src="https://cdn.modrinth.com/data/80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/simpleclaimsystem">SimpleClaimSystem</a><br><img alt="FactionsUUID icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/factionsuuid.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/factionsuuid">FactionsUUID</a><br><img alt="BlockLocker icon" src="https://hangarcdn.papermc.io/avatars/project/3666.webp?v=1" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/protections/blocklocker">BlockLocker</a></td>
    <td>Checks region and claim permissions before a spawner is opened, stacked or broken</td>
  </tr>
  <tr>
    <td><b>Islands and plots</b></td>
    <td><img alt="SuperiorSkyblock2 icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/superiorskyblock2.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/islands/superiorskyblock2">SuperiorSkyblock2</a><br><img alt="BentoBox icon" src="https://cdn.modrinth.com/data/aBVLHiAW/2ad201b237497093c4957095fa9f97bf61708ed1_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/islands/bentobox">BentoBox</a><br><img alt="IridiumSkyblock icon" src="https://cdn.modrinth.com/data/uVMG0MzO/1db3be2f57ed47d7b0dfd30902c437768c34d966.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/islands/iridiumskyblock">IridiumSkyblock</a><br><img alt="PlotSquared icon" src="https://raw.githubusercontent.com/OpenVdra/SmartSpawner/main/docs/public/icons/integrations/plotsquared.png" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/islands/plotsquared">PlotSquared</a><br><img alt="minePlots icon" src="https://cdn.jsdelivr.net/gh/OpenVdra/SmartSpawner@main/docs/public/icons/integrations/mineplots.svg" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/islands/mineplots">minePlots</a></td>
    <td>Applies island and plot permissions to spawner actions</td>
  </tr>
  <tr>
    <td><b>Skills</b></td>
    <td><img alt="AuraSkills icon" src="https://cdn.modrinth.com/data/uDdZAVls/aadaedbbb7f8c142a625a11081aa0d9bdfafecc2.jpeg" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/auraskills">AuraSkills</a></td>
    <td>Turns claimed spawner experience into skill XP</td>
  </tr>
  <tr>
    <td><b>Custom mobs</b></td>
    <td><img alt="MythicMobs icon" src="https://cdn.modrinth.com/data/NLuc7Jjh/80ddd8e4535a8305eac4edfd5ced1e8e5344e11d_96.webp" width="20" height="20" align="absmiddle"> <a href="https://docs.smartspawner.site/docs/integrations/mythicmobs">MythicMobs</a></td>
    <td>Drops spawner items from MythicMobs drop tables</td>
  </tr>
</table>

Setup for each plugin is in the [integrations guide](https://docs.smartspawner.site/docs/integrations/).

## Version Support

| Minecraft version | Java | Server software | Support status |
|---|---|---|---|
| 26.1 – 26.2 | 25 | Paper, Folia, Purpur | Active |
| 1.21.6 – 1.21.11 | 21 | Paper, Folia, Purpur | Active |

One jar runs on every version listed. The ExcellentEconomy integration needs Java 25, as ExcellentEconomy itself does.

## For Developers

<details>
<summary><b>Java API</b>, click to expand</summary>

Create spawner items, read spawner data and listen to spawner events. Add it from [JitPack](https://jitpack.io/#OpenVdra/SmartSpawner/):

```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { compileOnly 'com.github.OpenVdra:SmartSpawner:<version>' }
```

```java
// Give a stack of 5 zombie spawners.
SmartSpawnerAPI api = SmartSpawnerProvider.getAPI();
player.getInventory().addItem(api.createSpawnerItem(EntityType.ZOMBIE, 5));

// Double what players earn from selling spawner storage.
@EventHandler
public void onSell(SpawnerSellEvent event) {
    event.setMoneyAmount(event.getMoneyAmount() * 2);
}
```

See the [Developer API guide](https://docs.smartspawner.site/developer-api/).

</details>

## Quick FAQ

**Does it work on Folia?**  
Yes. The same jar runs on Paper, Folia and their forks.

**Can players use their own language?**  
Yes. With `language_auto_detect` on, messages and menus follow each player's game language when that language is installed.

**Supported languages:**  
English, Vietnamese, Simplified Chinese, Turkish, German and Italian.

Want to improve or add a translation? [Open a pull request](https://github.com/OpenVdra/SmartSpawner).

## Statistics

Thousands of servers run SmartSpawner.

<div align="center">

<a href="https://bstats.org/plugin/bukkit/SmartSpawner/24822"><img src="https://bstats.org/signatures/bukkit/SmartSpawner.svg" alt="bStats statistics"></a>

</div>
