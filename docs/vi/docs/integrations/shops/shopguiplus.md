---
title: ShopGUI+
---

# ![](/icons/integrations/shopguiplus.png){.plugin-icon} ShopGUI+

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/6515/)

SmartSpawner đọc giá bán ShopGUI+ và đăng ký listener tương thích spawner khi plugin có mặt.

## Định danh

- `ShopGUIPlus`

## Cấu hình

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  shop_integration:
    enabled: true
    preferred_plugin: ShopGUIPlus
```

Giá bán lấy từ định nghĩa cửa hàng ShopGUI+ của bạn. Vật phẩm thiếu giá ShopGUI+ sẽ dùng phương án dự phòng theo [chế độ nguồn giá](/vi/docs/integrations/shops/#che-do-nguon-gia).
