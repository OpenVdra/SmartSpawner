---
title: Vault
---

# ![](/icons/integrations/vault.png){.plugin-icon} Vault

**Download:** [SpigotMC](https://www.spigotmc.org/resources/34315/)

Vault connects SmartSpawner to the economy provider already used by your server.

## Configuration

```yaml
sell_integration:
  enabled: true
  currency: VAULT
```

## Requirement

Vault by itself is not an economy. An economy plugin (such as EssentialsX, CMI, or similar) must register a Vault economy service. If no provider is registered, SmartSpawner keeps selling disabled.
