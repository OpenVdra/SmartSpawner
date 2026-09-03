---
title: MMOItems
---

# MMOItems

**Tải về:** [Spigot](https://www.spigotmc.org/resources/mmoitems.39267/)

Vật phẩm MMOItems có thể được đặt ở bất cứ nơi nào SmartSpawner đọc giá trị `item`: làm drop trong
bảng loot, và làm vật phẩm mà một Item Spawner tạo ra.

```text
mmoitems:<TYPE>:<ID>
```

`TYPE` là loại vật phẩm MMOItems, `ID` là id của vật phẩm trong loại đó. Cả hai được so khớp ở dạng
chữ in hoa.

## Spawner drop vật phẩm MMOItems

```yaml
# spawner_mobs.yml
zombie_spawner:
  entity: ZOMBIE
  loot:
    1:
      item: mmoitems:MATERIAL:RUBY
      amount: 1-1
      chance: 5.0
```

## Spawner chính là vật phẩm MMOItems

```yaml
# spawner_items.yml
ruby_spawner:
  item: mmoitems:MATERIAL:RUBY
  experience: 1
  loot:
    1:
      item: mmoitems:MATERIAL:RUBY
      amount: 1-1
      chance: 100.0
```

Phát nó bằng `/ss give <player> item_spawner ruby_spawner`. Spawner lấy tên, biểu tượng menu và vật
phẩm xoay trong lồng từ chính vật phẩm MMOItems.

Nhiều mục có thể tạo ra vật phẩm dùng chung một material gốc. Chúng vẫn là các spawner riêng biệt với
bảng drop, tên và biểu tượng riêng, và không xếp chồng với nhau.

## Bán vật phẩm

Plugin cửa hàng chỉ định giá được cho material vanilla, nên một vật phẩm MMOItems sẽ bị bán theo giá
material gốc của nó. Hãy đặt cho nó một mức giá riêng trong `sell_integration.yml`, dưới đúng giá trị
mà mục loot đặt tên:

```yaml
custom_prices:
  prices:
    "mmoitems:MATERIAL:RUBY": 250.0
```

Mức giá đó được dùng bất kể `price_source_mode` là gì. Nếu không có mục nào, vật phẩm quay về giá của
material gốc.

## Ghi chú

- Vật phẩm được dựng một lần, khi cấu hình được đọc. Một template MMOItems có chỉ số ngẫu nhiên chỉ
  được random một lần, nên mọi bản sao spawner tạo ra đều giống nhau và xếp chồng bình thường.
- Sau `/mmoitems reload`, hãy chạy `/ss reload` để SmartSpawner nhận template đã dựng lại.
- Khi không có MMOItems, mục đặt tên nó sẽ bị bỏ qua và ghi vào console, còn Item Spawner đã đặt sẽ
  quay về material gốc thay vì biến mất.
- Sai type hoặc id được báo riêng trong console, nên dễ phân biệt lỗi gõ nhầm với plugin bị thiếu.
