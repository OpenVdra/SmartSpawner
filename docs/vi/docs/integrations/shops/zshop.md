---
title: zShop
---

# ![](/icons/integrations/zshop.png){.plugin-icon} zShop

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/126561/)

::: warning Thử nghiệm
Provider zShop đã có nhưng chưa được chọn bởi luồng tự động phát hiện hiện tại. Hãy xem đây là tính năng thử nghiệm và chọn thủ công.
:::

## Định danh

- `zShop`

## Cấu hình

```yaml
sell_integration:
  enabled: true
  currency: VAULT
  shop_integration:
    enabled: true
    preferred_plugin: zShop
```

Xác nhận log khởi động báo provider zShop đang hoạt động trước khi cho người chơi bán. Nếu không, hãy dùng [giá tùy chỉnh](/vi/docs/integrations/shops/#che-do-nguon-gia) cho tới khi tương thích trở lại.
