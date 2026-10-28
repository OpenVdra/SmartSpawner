---
title: ExcellentEconomy
---

# ![](https://cdn.modrinth.com/data/r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp){.plugin-icon} ExcellentEconomy

**Download:** [Modrinth](https://modrinth.com/plugin/excellenteconomy)

ExcellentEconomy can be used directly without Vault.

## Configuration

Set the currency identifier to an existing ExcellentEconomy currency ID:

```yaml
sell_integration:
  enabled: true
  currency: EXCELLENTECONOMY
  excellenteconomy_currency: money
```

The `excellenteconomy_currency` value is case-sensitive and must match the configured ExcellentEconomy currency ID exactly. SmartSpawner disables selling if it cannot resolve that currency.
