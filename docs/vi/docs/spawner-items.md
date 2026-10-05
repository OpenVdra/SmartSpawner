# Item Spawner

File `plugins/SmartSpawner/spawner_items.yml` cấu hình vật phẩm, XP và texture cho **Item Spawner**, loại spawner tạo nguyên liệu trực tiếp thay vì drop mob.

Mỗi item spawner nằm dưới tên vật phẩm của nó, ví dụ `diamond`. Spawner thêm cho cùng một vật phẩm, mỗi cái có loot riêng, đặt trong `custom_spawners`.

## Chỉnh Trong Game

Dùng `/ss editloot <name>` để đổi loot của item spawner mà không cần mở file, ví dụ `/ss editloot diamond`. Xem trang [Lệnh](/vi/docs/commands#ss-editloot) để biết cách dùng trình chỉnh.

::: info Hệ số vật phẩm
Mỗi chu kỳ tạo từ **min_mobs** đến **max_mobs** lần (mặc định 1–4). Số lượng cấu hình là giá trị cơ sở được nhân lên.
:::

::: warning Giới hạn
Item Spawner không hỗ trợ potion hoặc enchanted book. Chỉ **tipped arrow** hỗ trợ hiệu ứng potion.
:::

## Định Dạng Cấu Hình

```yaml
diamond:                    # Spawner của chính vật phẩm, đặt theo tên vật phẩm
  experience: <number>
  nbt_data: <item>          # Tùy chọn, vật phẩm hiển thị trong lồng spawner
  loot:
    1:
      item: <item>          # Bắt buộc
      amount: <min>-<max>
      chance: <percentage>
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>    # null cho material vanilla

custom_spawners:
  rich_diamond:             # Tên bất kỳ không trùng tên vật phẩm
    item: DIAMOND           # Bắt buộc
    display_name: Rich Diamond # Tùy chọn
    loot:
      1:
        item: DIAMOND_BLOCK
        amount: 1-1
        chance: 10.0
```

## Spawner Custom

Item spawner custom hoạt động giống [spawner mob custom](/vi/docs/spawner-mobs#spawner-custom):

- `item` là bắt buộc, cho biết vật phẩm nào.
- `display_name` thay tên vật phẩm ở mọi chỗ người chơi nhìn thấy spawner này.
- `experience`, `nbt_data` và `mob_head` là tùy chọn. Thiếu cái nào thì lấy từ spawner của chính vật phẩm đó.
- `loot` không bao giờ lấy từ spawner của vật phẩm. Spawner custom không có `loot` thì không rơi gì.
- Muốn đổi tên mà vẫn giữ các spawner đã đặt, hãy liệt kê tên cũ trong `aliases`.

## Tham Chiếu Thuộc Tính

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `item` (cấp spawner) | `DIAMOND` | Vật phẩm của spawner custom. Chỉ dùng trong `custom_spawners`. |
| `display_name` | `Rich Diamond` | Tên người chơi thấy thay cho tên vật phẩm |
| `aliases` | `[old_name]` | Tên cũ của một spawner custom đã đổi tên |
| `experience` | `1` | XP tạo ra mỗi lần spawner kích hoạt |
| `nbt_data` | `nbt:...` | Vật phẩm hiển thị dạng model quay bên trong lồng spawner |
| `item` (loot) | `DIAMOND` | Vật phẩm sẽ rơi |
| `amount` | `1-1` | Khoảng số lượng cơ sở mỗi chu kỳ |
| `chance` | `100.0` | Xác suất rơi (0.0 đến 100.0) |

`item` nhận tên material, chuỗi vật phẩm của `/give` như
`tipped_arrow[potion_contents={potion:"minecraft:poison"}]`, hoặc mã `nbt:` sao chép từ trong game.
Xem giải thích đầy đủ tại [Spawner Mob](/vi/docs/spawner-mobs#đat-ten-vat-pham).

::: tip Tên material
Mọi giá trị material là tên material của Bukkit viết hoa, ví dụ `DIAMOND` hoặc `NETHERITE_INGOT`. Danh sách đầy đủ: [Bukkit Material list](https://jd.papermc.io/paper/26.2/org/bukkit/Material.html).
:::

## Ví Dụ

### Spawner Tài Nguyên Cơ Bản

```yaml
diamond:
  experience: 1
  loot:
    1:
      item: DIAMOND
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "DIAMOND"
    hash_texture: null
```

### Nhiều Loại Vật Phẩm

```yaml
gold_ingot:
  experience: 1
  loot:
    1:
      item: GOLD_INGOT
      amount: 1-2
      chance: 100.0
    2:
      item: GOLD_NUGGET
      amount: 3-5
      chance: 50.0
```

### Head Có Texture Riêng

```yaml
emerald:
  experience: 1
  loot:
    1:
      item: EMERALD
      amount: 1-1
      chance: 100.0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "abc123def456..."
```

### Spawner Tipped Arrow

```yaml
tipped_arrow:
  experience: 1
  loot:
    1:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 8-16
      chance: 100.0
```

### Hai Spawner Cho Cùng Một Vật Phẩm

```yaml
diamond:
  experience: 1
  loot:
    1:
      item: DIAMOND
      amount: 1-1
      chance: 100.0

custom_spawners:
  rich_diamond:
    item: DIAMOND
    display_name: Rich Diamond
    loot:
      1:
        item: DIAMOND_BLOCK
        amount: 1-1
        chance: 10.0
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

::: info Nâng Cấp Từ 1.8
File `spawner_items.yml` theo định dạng 1.8 được chuyển đổi ở lần khởi động đầu tiên, giống như [`spawner_mobs.yml`](/vi/docs/spawner-mobs#cau-hinh-mac-đinh). File cũ được giữ lại thành `spawner_items.yml.1.8-backup`.
:::

## Trao Item Spawner

```bash
/ss give <player> <name> [amount]
```

```bash
/ss give Steve diamond 1
/ss give Player123 rich_diamond 5
```
