---
title: Cửa hàng và Kinh tế
---

# Cửa Hàng và Kinh Tế

Tính năng bán từ spawner cần hai phần độc lập:

1. **Nguồn giá** (một plugin cửa hàng được hỗ trợ, hoặc bảng giá trong `sell_integration.yml`).
2. **Backend tiền tệ** (Vault hoặc ExcellentEconomy).

Cả hai đều được cấu hình trong `sell_integration.yml`. Xem [Tích hợp bán](/vi/docs/sell-integration) để biết mọi tùy chọn.

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

## Plugin nguồn giá

| Plugin | Định danh | Trang |
|---|---|---|
| ![](/icons/integrations/economyshopgui.png){.plugin-icon} EconomyShopGUI | `EconomyShopGUI`, `EconomyShopGUI-Premium` | [EconomyShopGUI](/vi/docs/integrations/shops/economyshopgui) |
| ![](/icons/integrations/shopguiplus.png){.plugin-icon} ShopGUI+ | `ShopGUIPlus` | [ShopGUI+](/vi/docs/integrations/shops/shopguiplus) |
| ![](/icons/integrations/zshop.png){.plugin-icon} zShop | `zShop` | [zShop](/vi/docs/integrations/shops/zshop) |

## Backend tiền tệ

| Plugin | Định danh | Trang |
|---|---|---|
| ![](/icons/integrations/vault.png){.plugin-icon} Vault | `VAULT` | [Vault](/vi/docs/integrations/shops/vault) |
| ![](https://cdn.modrinth.com/data/r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp){.plugin-icon} ExcellentEconomy | `EXCELLENTECONOMY` | [ExcellentEconomy](/vi/docs/integrations/shops/excellenteconomy) |

## Chế độ nguồn giá

| Chế độ | Thứ tự tra cứu |
|---|---|
| `SHOP_ONLY` | Chỉ giá từ cửa hàng hỗ trợ |
| `SHOP_PRIORITY` | Giá cửa hàng, sau đó giá tùy chỉnh |
| `CUSTOM_ONLY` | Chỉ bảng giá `custom_prices.prices` |
| `CUSTOM_PRIORITY` | Giá tùy chỉnh, sau đó giá cửa hàng |

Dùng `/ss prices` để kiểm tra provider đang hoạt động và giá đã phân giải sau khi thiết lập.
