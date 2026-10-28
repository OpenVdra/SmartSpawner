---
title: ExcellentEconomy
---

# ![](https://cdn.modrinth.com/data/r0FB9U1e/5e7beb1f67b9ea9dac40616b24fb5fa625a94b66_96.webp){.plugin-icon} ExcellentEconomy

**Tải về:** [Modrinth](https://modrinth.com/plugin/excellenteconomy)

ExcellentEconomy có thể dùng trực tiếp mà không cần Vault.

## Cấu hình

Đặt định danh tiền tệ thành một currency ID đã tồn tại trong ExcellentEconomy:

```yaml
sell_integration:
  enabled: true
  currency: EXCELLENTECONOMY
  excellenteconomy_currency: money
```

Giá trị `excellenteconomy_currency` phân biệt chữ hoa chữ thường và phải khớp chính xác với currency ID đã cấu hình trong ExcellentEconomy. SmartSpawner tắt bán nếu không tìm thấy currency đó.
