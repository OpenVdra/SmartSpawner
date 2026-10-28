---
title: Vault
---

# ![](/icons/integrations/vault.png){.plugin-icon} Vault

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/34315/)

Vault kết nối SmartSpawner với provider kinh tế mà máy chủ của bạn đang dùng.

## Cấu hình

```yaml
sell_integration:
  enabled: true
  currency: VAULT
```

## Yêu cầu

Bản thân Vault không phải plugin kinh tế. Một plugin kinh tế (như EssentialsX, CMI, v.v.) phải đăng ký dịch vụ kinh tế Vault. Nếu không có provider nào đăng ký, SmartSpawner vẫn tắt tính năng bán.
