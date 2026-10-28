---
title: Shops and Economy
---

# Shops and Economy

Selling from a spawner needs two independent pieces:

1. A **price source** (a supported shop plugin, or the price list in `sell_integration.yml`).
2. A **currency backend** (Vault or ExcellentEconomy).

Both are configured in `sell_integration.yml`. See [Sell Integration](/docs/sell-integration) for every option.

```yaml
enabled: true
currency: VAULT
price_source_mode: SHOP_PRIORITY
shop_integration:
  enabled: true
  preferred_plugin: auto
custom_prices:
  enabled: true
  default_price: 1.0
```

## Price source plugins

| Plugin | Identifier | Page |
|---|---|---|
| ![](/icons/integrations/economyshopgui.png){.plugin-icon} EconomyShopGUI | `EconomyShopGUI`, `EconomyShopGUI-Premium` | [EconomyShopGUI](/docs/integrations/shops/economyshopgui) |
| ![](/icons/integrations/shopguiplus.png){.plugin-icon} ShopGUI+ | `ShopGUIPlus` | [ShopGUI+](/docs/integrations/shops/shopguiplus) |
| ![](/icons/integrations/zshop.png){.plugin-icon} zShop | `zShop` | [zShop](/docs/integrations/shops/zshop) |

## Currency backends

| Plugin | Identifier | Page |
|---|---|---|
| ![](/icons/integrations/vault.png){.plugin-icon} Vault | `VAULT` | [Vault](/docs/integrations/shops/vault) |
| ![](https://cdn.modrinth.com/data/r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp){.plugin-icon} ExcellentEconomy | `EXCELLENTECONOMY` | [ExcellentEconomy](/docs/integrations/shops/excellenteconomy) |

## Price source modes

| Mode | Resolution order |
|---|---|
| `SHOP_ONLY` | Supported shop price only |
| `SHOP_PRIORITY` | Shop price, then custom price |
| `CUSTOM_ONLY` | The `custom_prices.prices` list only |
| `CUSTOM_PRIORITY` | Custom price, then shop price |

Run `/ss prices` to inspect the active providers and resolved values after setup.
