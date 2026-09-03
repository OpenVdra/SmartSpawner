---
title: Tích Hợp Bán
---

# Tích Hợp Bán

File `plugins/SmartSpawner/sell_integration.yml` điều khiển nút bán trong kho spawner: dùng plugin kinh tế nào, giá lấy từ đâu, và mỗi vật phẩm đáng giá bao nhiêu.

Mọi tùy chọn trong file này áp dụng khi chạy `/ss reload`.

## Giá lấy từ đâu

SmartSpawner có hai nguồn giá: plugin cửa hàng, nếu có cài, và bảng giá trong chính file này. `price_source_mode` quyết định nguồn nào thắng.

| Chế độ | Hành vi |
|--------|---------|
| `SHOP_PRIORITY` | Giá cửa hàng trước, bảng giá dự phòng. Khuyến nghị. |
| `SHOP_ONLY` | Chỉ dùng giá plugin cửa hàng, bỏ qua bảng giá. |
| `CUSTOM_PRIORITY` | Bảng giá trước, giá cửa hàng dự phòng. |
| `CUSTOM_ONLY` | Chỉ dùng bảng giá, bỏ qua giá cửa hàng. |

Nếu chế độ đã chọn không có nguồn nào hoạt động, tính năng bán bị tắt và console báo lý do lúc khởi động.

## Tùy chọn

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="enabled" value="true" type="boolean">
Bật bán vật phẩm trong kho spawner. Đặt <code>false</code> để tắt hẳn nút bán.
</ConfigProperty>

<ConfigProperty name="currency" value="VAULT" type="string">
Backend kinh tế: <code>VAULT</code> hoặc <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="excellenteconomy_currency" value="money" type="string">
Tên currency ExcellentEconomy, chỉ dùng khi <code>currency</code> là <code>EXCELLENTECONOMY</code>.
</ConfigProperty>

<ConfigProperty name="price_source_mode" value="SHOP_PRIORITY" type="string">
Nguồn lấy giá bán. Xem bảng ở trên.
</ConfigProperty>

<ConfigGroup name="shop_integration">

<ConfigProperty name="enabled" value="true" type="boolean">
Bật tra cứu giá từ plugin cửa hàng.
</ConfigProperty>

<ConfigProperty name="preferred_plugin" value="auto" type="string">
Plugin cửa hàng cần dùng: <code>auto</code>, <code>EconomyShopGUI</code>, <code>EconomyShopGUI-Premium</code>, <code>ShopGUIPlus</code> hoặc <code>zShop</code>. Với <code>auto</code>, plugin được cài đầu tiên sẽ được dùng.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="custom_prices">

<ConfigProperty name="enabled" value="true" type="boolean">
Bật bảng giá bên dưới.
</ConfigProperty>

<ConfigProperty name="default_price" value="1.0" type="number">
Giá cho vật phẩm không có trong bảng. Đặt <code>0.0</code> để không cho bán vật phẩm chưa liệt kê.
</ConfigProperty>

<ConfigProperty name="prices" value="(danh sách material)" type="string">
Mỗi dòng là một material, tính theo đơn vị tiền tệ. Thêm hoặc xoá tuỳ ý. Material đã xoá khỏi bảng sẽ không bị thêm lại trong lần khởi động sau.

```yaml
custom_prices:
  prices:
    LEATHER: 4.0
    ENDER_PEARL: 10.0
    WITHER_SKELETON_SKULL: 100.0
```

Vật phẩm thuộc plugin khác được định giá dưới đúng giá trị mà mục loot của nó đặt tên, và mức giá đó
được dùng bất kể `price_source_mode` là gì, bởi plugin cửa hàng chỉ định giá được cho material
vanilla. Đừng dùng dấu chấm trong khoá: nó được đọc như một đường dẫn và mục sẽ bị bỏ qua.

```yaml
custom_prices:
  prices:
    "mmoitems:MATERIAL:RUBY": 250.0
```
</ConfigProperty>

</ConfigGroup>

</div>

## Kiểm tra giá trong game

`/ss prices` mở bảng liệt kê giá của mọi vật phẩm spawner có thể rơi ra, kèm nguồn của từng giá. Đây là cách nhanh nhất để xác nhận thay đổi đã có hiệu lực.

## Nâng cấp từ phiên bản cũ

Trước 1.8.0, các tùy chọn này nằm ở mục `sell_integration` trong `config.yml`, còn bảng giá là file `item_prices.yml` riêng. Cả hai được chuyển vào file này tự động trong lần khởi động đầu tiên và giữ nguyên giá trị. `item_prices.yml` bị xoá sau khi giá đã được chuyển sang.
