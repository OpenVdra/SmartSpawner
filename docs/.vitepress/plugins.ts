// The plugins SmartSpawner hooks into, and their icons, for the sidebar (config.mts) and the
// integrations overview grid (components/card/PluginGrid.vue). A plugin on Modrinth or Hangar links
// its icon from there. SpigotMC blocks hotlinking, so Spigot-only plugins have a copy in
// public/icons/integrations/. minePlots has no public icon and uses a Lucide land-plot icon.

export interface Plugin {
  name: string
  icon: string
  // The page under /docs/integrations/, the same for every locale.
  page: string
  // A white icon on transparent, drawn on a dark tile so it shows in light mode.
  dark?: boolean
}

const local = (file: string) => `/icons/integrations/${file}`
const modrinth = (path: string) => `https://cdn.modrinth.com/data/${path}`
const hangar = (id: number) => `https://hangarcdn.papermc.io/avatars/project/${id}.webp?v=1`

export const SHOPS: Plugin[] = [
  { name: 'EconomyShopGUI', icon: local('economyshopgui.png'), page: 'shops/economyshopgui' },
  { name: 'ShopGUI+', icon: local('shopguiplus.png'), page: 'shops/shopguiplus' },
  { name: 'zShop', icon: local('zshop.png'), page: 'shops/zshop' },
  { name: 'Vault', icon: local('vault.png'), page: 'shops/vault' },
  { name: 'ExcellentEconomy', icon: modrinth('r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp'), page: 'shops/excellenteconomy' }
]

export const PROTECTIONS: Plugin[] = [
  { name: 'WorldGuard', icon: modrinth('DKY9btbd/e1281ffadc4e194780bd31eb89ae6ea2537ec41b_96.webp'), page: 'protections/worldguard' },
  { name: 'GriefPrevention', icon: modrinth('O4o4mKaq/6aeade7ea5551c41dbe71e379ea893777356650d_96.webp'), page: 'protections/griefprevention' },
  { name: 'Lands', icon: local('lands.jpg'), page: 'protections/lands' },
  { name: 'Towny', icon: modrinth('Vs77PB2W/f094516d1b227a8a0495c9e1d0a6f8c58aec5ccf_96.webp'), page: 'protections/towny' },
  { name: 'Residence', icon: local('residence.jpg'), page: 'protections/residence' },
  { name: 'RedProtect', icon: modrinth('loXscTUV/d3cba12115315c4841794e4f8821cb1f9518f3ec_96.webp'), page: 'protections/redprotect' },
  { name: 'SimpleClaimSystem', icon: modrinth('80Ke0mYG/d9cf637da756d12d3e43f3cebf6731ea60af9f71_96.webp'), page: 'protections/simpleclaimsystem' },
  { name: 'FactionsUUID', icon: local('factionsuuid.png'), page: 'protections/factionsuuid' },
  { name: 'BlockLocker', icon: hangar(3666), page: 'protections/blocklocker' }
]

export const ISLANDS: Plugin[] = [
  { name: 'PlotSquared', icon: local('plotsquared.png'), page: 'islands/plotsquared' },
  { name: 'minePlots', icon: local('mineplots.svg'), page: 'islands/mineplots' },
  { name: 'SuperiorSkyblock2', icon: local('superiorskyblock2.png'), page: 'islands/superiorskyblock2' },
  { name: 'BentoBox', icon: modrinth('aBVLHiAW/2ad201b237497093c4957095fa9f97bf61708ed1_96.webp'), page: 'islands/bentobox' },
  { name: 'IridiumSkyblock', icon: modrinth('uVMG0MzO/1db3be2f57ed47d7b0dfd30902c437768c34d966.png'), page: 'islands/iridiumskyblock' }
]

export const OTHERS: Plugin[] = [
  { name: 'AuraSkills', icon: modrinth('uDdZAVls/aadaedbbb7f8c142a625a11081aa0d9bdfafecc2.jpeg'), page: 'auraskills' },
  { name: 'MythicMobs', icon: modrinth('NLuc7Jjh/80ddd8e4535a8305eac4edfd5ced1e8e5344e11d_96.webp'), page: 'mythicmobs', dark: true }
]
