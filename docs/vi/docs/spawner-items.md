# Item Spawner

File `plugins/SmartSpawner/spawner_items.yml` cấu hình vật phẩm, XP và texture cho **Item Spawner**, loại spawner tạo nguyên liệu trực tiếp thay vì drop mob.

## Quản Lý Trong Game

Dùng `/ss edit itemspawner` để chỉnh các mục hiện có. GUI này tách biệt với trình sửa mob
SmartSpawner và không có nút chuyển đổi.

Chạy `/ss add itemspawner [name]` để tạo mục mới. Tên là tùy chọn, khoảng trắng tự đổi thành dấu gạch dưới;
nếu bỏ qua, tên mặc định dựa trên material như `diamond_spawner`. Đặt vật phẩm nguồn vào GUI rồi xác nhận.

Vật phẩm nguồn được lưu nguyên vẹn dưới `nbt_data` và hiển thị bên trong lồng spawner. Các
component của item được giữ lại, vì vậy splash potion Jump Boost sẽ hiện đúng potion đó thay vì một
splash potion thường. Mục cũ chưa có `nbt_data` sẽ dùng loot hợp lệ đầu tiên làm model.

Màn hình loot có 27 slot, không phân trang và không có item điều hướng. Chỉ có một ô kính xanh lá nằm
ngay sau loot cuối cùng; khi thêm item, ô kính này tự dịch sang slot kế tiếp.

::: info Hệ số vật phẩm
Mỗi chu kỳ tạo từ **min_mobs** đến **max_mobs** lần (mặc định 1–4). Số lượng cấu hình là giá trị cơ sở được nhân lên.
:::

::: warning Giới hạn
Item Spawner không hỗ trợ potion hoặc enchanted book. Chỉ **tipped arrow** hỗ trợ hiệu ứng potion.
:::

## Định Dạng Cấu Hình

```yaml

custom_spawner_name:
  item: ITEM_MATERIAL
  experience: <number>
  nbt_data: <vật phẩm đã bắt> # Được /ss add itemspawner tự ghi
  loot:
    1:
      item: <item>          # Bắt buộc
      amount: <min>-<max>
      chance: <percentage>
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>  # null đối với material vanilla
```

## Tham Chiếu Thuộc Tính

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `material` | `"DIAMOND"` | Material chính mà spawner đại diện |
| `experience` | `1` | XP tạo ra mỗi lần kích hoạt |
| `nbt_data` | `nbt:...` | Vật phẩm nguyên vẹn được dùng làm model quay bên trong lồng spawner |
| `amount` | `1-1` | Khoảng số lượng cơ sở mỗi chu kỳ |
| `chance` | `100.0` | Xác suất rơi từ 0.0–100.0 |
| `item` | `DIAMOND` | Vật phẩm sẽ rơi. Bỏ trống để dùng tên mục. |

Cả hai trường `item` đều nhận tên material, chuỗi vật phẩm của `/give` như
`tipped_arrow[potion_contents={potion:"minecraft:poison"}]`, một mã `nbt:` sao chép từ trong game,
hoặc một vật phẩm thuộc plugin khác như `mmoitems:MATERIAL:RUBY`. Xem
[Cài Đặt Spawner](/vi/docs/spawner-mobs) để biết giải thích đầy đủ.

Một Item Spawner được nhận diện bằng tên mục của nó, nên nhiều mục có thể tạo ra vật phẩm dùng chung
một material gốc. Hai mục dựa trên `EMERALD`, một vanilla và một từ
[MMOItems](/vi/docs/integrations/mmoitems), vẫn là hai spawner riêng: mỗi cái giữ bảng drop, biểu
tượng và tên riêng, và chúng không xếp chồng với nhau.

::: tip Tên material
Mỗi giá trị `material` là một tên material của Bukkit viết hoa, ví dụ `DIAMOND` hoặc `NETHERITE_INGOT`. Xem danh sách đầy đủ các tên hợp lệ tại đây: [Danh sách Bukkit Material](https://jd.papermc.io/paper/26.2/org/bukkit/Material.html).
:::

## Ví Dụ

### Spawner Tài Nguyên Cơ Bản

```yaml
diamond_spawner:
  item: DIAMOND
  experience: 1
  loot:
    1:
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "DIAMOND"
    hash_texture: null
```

### Nhiều Loại Vật Phẩm

```yaml
gold_ingot_spawner:
  item: GOLD_INGOT
  experience: 1
  loot:
    1:
      amount: 1-2
      chance: 100.0
    2:
      amount: 3-5
      chance: 50.0
  mob_head:
    item: "GOLD_INGOT"
    hash_texture: null
```

### Custom Head

```yaml
emerald_spawner:
  item: EMERALD
  experience: 1
  loot:
    1:
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "abc123def456..."
```

### Tipped Arrow

```yaml
tipped_arrow_spawner:
  item: TIPPED_ARROW
  experience: 1
  loot:
    1:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 8-16
      chance: 100.0
  mob_head:
    material: "TIPPED_ARROW"
    hash_texture: null
```

### Vật Phẩm Hiếm Có Xác Suất

```yaml
totem_of_undying_spawner:
  item: TOTEM_OF_UNDYING
  experience: 2
  loot:
    1:
      amount: 1-1
      chance: 75.0
    2:
      amount: 1-3
      chance: 50.0
  mob_head:
    material: "TOTEM_OF_UNDYING"
    hash_texture: null
```

## Cơ Chế Tạo Vật Phẩm

```
actual_drops = base_amount × random(min_mobs, max_mobs)
```

| Số lượng cấu hình | Đầu ra có thể có |
|-------------------|------------------|
| `1-1` | 1–4 vật phẩm |
| `1-2` | 1–8 vật phẩm |
| `2-3` | 2–12 vật phẩm |

## Cấu Hình Mặc Định

SmartSpawner có sẵn cấu hình cho các nguyên liệu giá trị phổ biến.

- **Xem online:** [spawner_items.yml trên GitHub](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawner_items.yml)
- **Đặt lại:** Xóa file rồi khởi động lại để tạo mới

## Trao Item Spawner

```bash
/ss give <player> item_spawner <name> [amount]
```

```bash
/ss give Steve item_spawner diamond_spawner 1
/ss give Player123 item_spawner netherite_ingot_spawner 5
```
