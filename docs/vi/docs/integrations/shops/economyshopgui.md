---
title: EconomyShopGUI
---

# ![](/icons/integrations/economyshopgui.png){.plugin-icon} EconomyShopGUI

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/69927/)

SmartSpawner đọc giá bán của EconomyShopGUI cho thao tác bán trong kho spawner.

## Định danh

- `EconomyShopGUI`
- `EconomyShopGUI-Premium`

## Cấu hình

Với `preferred_plugin: auto`, SmartSpawner kiểm tra EconomyShopGUI trước. Để chọn rõ ràng:

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  shop_integration:
    enabled: true
    preferred_plugin: EconomyShopGUI
```

Giá lấy từ cấu hình cửa hàng EconomyShopGUI của bạn. Vật phẩm không có giá bán sẽ dùng phương án dự phòng theo [chế độ nguồn giá](/vi/docs/integrations/shops/#che-do-nguon-gia).
