---
title: Sell Integration
---

# Sell Integration

The `sell_integration.yml` file is located in `plugins/SmartSpawner/`. It controls the sell button in spawner storage: which economy plugin gets used, where prices come from, and what each item is worth.

Everything in this file applies on `/ss reload`.

## Where prices come from

SmartSpawner has two price sources. A shop plugin, if one is installed, and the price list in this file. `price_source_mode` decides which wins.

| Mode | Behavior |
|------|----------|
| `SHOP_PRIORITY` | Shop prices first, then the price list as fallback. Recommended. |
| `SHOP_ONLY` | Shop plugin prices only. The price list is ignored. |
| `CUSTOM_PRIORITY` | The price list first, then shop prices as fallback. |
| `CUSTOM_ONLY` | The price list only. Shop prices are ignored. |

If the chosen mode has no working source behind it, selling is disabled and the console says why on startup.

## Options

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="enabled" value="true" type="boolean">
Enables selling items from spawner storage. Set to <code>false</code> to turn off the sell button entirely.
</ConfigProperty>

<ConfigProperty name="currency" value="VAULT" type="string">
Economy backend. Supported values: <code>VAULT</code>, <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="excellenteconomy_currency" value="money" type="string">
ExcellentEconomy currency name. Only used when <code>currency</code> is <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="price_source_mode" value="SHOP_PRIORITY" type="string">
Where sell prices come from. See the table above.
</ConfigProperty>

<ConfigGroup name="shop_integration">

<ConfigProperty name="enabled" value="true" type="boolean">
Enables shop plugin price lookup.
</ConfigProperty>

<ConfigProperty name="preferred_plugin" value="auto" type="string">
Which shop plugin to use: <code>auto</code>, <code>EconomyShopGUI</code>, <code>EconomyShopGUI-Premium</code>, <code>ShopGUIPlus</code>, or <code>zShop</code>. On <code>auto</code> the first one installed is used.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="custom_prices">

<ConfigProperty name="enabled" value="true" type="boolean">
Enables the price list below.
</ConfigProperty>

<ConfigProperty name="default_price" value="1.0" type="number">
Price for an item with no entry in the list. Set to <code>0.0</code> to stop unlisted items being sold.
</ConfigProperty>

<ConfigProperty name="prices" value="(material list)" type="string">
One line per material, in currency units. Add or remove entries freely. A material removed from this list is not added back on the next start.

```yaml
custom_prices:
  prices:
    LEATHER: 4.0
    ENDER_PEARL: 10.0
    WITHER_SKELETON_SKULL: 100.0
```

An item owned by another plugin is priced under the exact value its loot entry names, and that price
is used whatever `price_source_mode` says, because a shop plugin can only price a vanilla material.
Keep dots out of the key: they are read as a path and the entry is ignored.

```yaml
custom_prices:
  prices:
    "mmoitems:MATERIAL:RUBY": 250.0
```
</ConfigProperty>

</ConfigGroup>

</div>

## Checking prices in game

`/ss prices` opens a browser showing the price of every item spawners can drop, and which source each price came from. It is the quickest way to confirm a change landed.

## Upgrading from an older version

Before 1.8.0 these settings were the `sell_integration` section of `config.yml`, and the price list was a separate `item_prices.yml`. Both move into this file automatically on the first start, keeping their values. `item_prices.yml` is deleted once its prices have been copied across.
