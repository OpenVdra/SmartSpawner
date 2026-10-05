# Spawner Mob

File `plugins/SmartSpawner/spawner_mobs.yml` điều khiển bảng vật phẩm, XP, head trong menu và tỷ lệ rơi của mọi spawner mob.

Mỗi mob có một spawner nằm dưới chính tên của nó, ví dụ `zombie`. Spawner thêm cho cùng một mob, mỗi cái có loot riêng, đặt trong `custom_spawners`.

## Chỉnh Trong Game

Dùng `/ss editloot <name>` để đổi loot của spawner mà không cần mở file. Lệnh dùng được cho cả spawner mob lẫn spawner custom, ví dụ `/ss editloot zombie` hoặc `/ss editloot golden_zombie`. Xem trang [Lệnh](/vi/docs/commands#ss-editloot) để biết cách dùng trình chỉnh.

::: info Hệ Số Rơi Đồ
Mỗi chu kỳ roll vật phẩm từ **min_mobs** đến **max_mobs** lần (mặc định 1–4). Số lượng cấu hình là giá trị gốc cho mỗi mob; đầu ra thực tế cao hơn.
:::

## Định Dạng Cấu Hình

```yaml
zombie:                       # Spawner của chính mob, đặt theo tên mob
  experience: <number>
  drop_chance: <percentage>   # Tùy chọn, mặc định 100.0
  nbt_data: <SNBT kiểu /summon> # Tùy chọn
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>      # null cho head vanilla
  loot:                       # Tùy chọn
    1:
      item: <item>            # Bắt buộc
      amount: <min>-<max>
      chance: <percentage>
      durability: <min>-<max> # Tùy chọn, cho công cụ và vũ khí

custom_spawners:
  golden_zombie:              # Tên bất kỳ không trùng tên mob
    entity: ZOMBIE            # Bắt buộc
    display_name: Golden Zombie # Tùy chọn
    loot:
      1:
        item: GOLD_INGOT
        amount: 1-2
        chance: 50.0
```

## Spawner Custom

Spawner custom là spawner thứ hai cho một mob đã có spawner, với loot riêng. Người chơi thấy nó như một spawner khác: có tên riêng, không stack được với spawner thường của mob đó, và `/ss give` trao nó theo tên riêng.

- `entity` là bắt buộc, cho biết mob nào.
- `display_name` thay tên mob ở mọi chỗ người chơi nhìn thấy spawner này: tên item, tiêu đề menu và hologram. Chỉ dùng chữ thường, màu lấy từ file ngôn ngữ.
- `experience`, `drop_chance`, `nbt_data` và `mob_head` là tùy chọn. Thiếu cái nào thì lấy từ spawner của chính mob đó.
- `loot` không bao giờ lấy từ spawner của mob. Spawner custom không có `loot` thì không rơi gì.
- Tên không được trùng tên mob, và không được trùng giữa `spawner_mobs.yml` và `spawner_items.yml`. Console sẽ cảnh báo mọi tên vi phạm.

### Đổi Tên Spawner Custom

Spawner đã đặt ngoài thế giới nhớ tên được trao lúc đầu. Muốn đổi tên một spawner custom mà vẫn giữ các spawner đã đặt, hãy liệt kê tên cũ trong `aliases`:

```yaml
custom_spawners:
  golden_zombie:
    entity: ZOMBIE
    aliases: [lucky_zombie]
    loot: ...
```

Khi đó spawner đã đặt và item spawner mang tên cũ sẽ thuộc về `golden_zombie`. Chúng stack được với spawner mới, và spawner đã đặt tự chuyển sang tên mới.

Nếu xóa hẳn một spawner custom, các spawner đã đặt của nó hoạt động như spawner của chính mob đó, và console báo tên spawner bị thiếu một lần. Thêm lại mục đó là chúng trở về như cũ.

## Đặt Tên Vật Phẩm

Mỗi mục loot ghi vật phẩm của nó ở trường `item`. Trường này bắt buộc: mục nào thiếu sẽ bị
bỏ qua và báo trong console.

`item` nhận ba dạng:

| Dạng | Ví dụ | Dùng khi |
|------|-------|----------|
| Tên material | `ARROW` | Vật phẩm thường |
| Chuỗi vật phẩm của `/give` | `tipped_arrow[potion_contents={potion:"minecraft:poison"}]` | Potion, đồ phù phép, đồ đặt tên, mọi thứ có dữ liệu kèm theo |
| `nbt:` kèm một mã | `nbt:H4sIAAAA...` | Vật phẩm sao chép nguyên vẹn từ trong game |

Dạng thứ hai chính là chuỗi mà lệnh `/give` gợi ý sẵn trong game. Bạn dựng vật phẩm mong muốn bằng
`/give`, sao chép phần sau tên người chơi, rồi dán vào đây trong dấu nháy đơn.

Các mục được đánh số, và con số chỉ là vị trí trong danh sách. Dòng `item` mới là thứ quyết định
vật phẩm rơi ra, nhờ vậy cùng một material có thể xuất hiện nhiều lần:

```yaml
bogged:
  loot:
    1:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 0-1
      chance: 50.0
    2:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:slowness"}]'
      amount: 0-1
      chance: 10.0
```

Mục nào máy chủ không đọc được sẽ bị bỏ qua và báo trong console kèm tên spawner và tên mục. Phần còn
lại của file vẫn nạp bình thường.

## Tham Chiếu Thuộc Tính

### Thuộc Tính Spawner

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `entity` | `ZOMBIE` | Mob của spawner custom. Chỉ dùng trong `custom_spawners`. |
| `display_name` | `Golden Zombie` | Tên người chơi thấy thay cho tên mob |
| `aliases` | `[lucky_zombie]` | Tên cũ của một spawner custom đã đổi tên |
| `experience` | `5` | XP tạo ra mỗi lần spawner kích hoạt |
| `nbt_data` | `{profile:DrDonutt}` | SNBT kiểu `/summon` dùng cho model entity quay bên trong lồng spawner |
| `drop_chance` | `75.0` | Xác suất vật phẩm Smart Spawner rơi khi bị phá; bỏ qua để dùng 100.0 |
| `mob_head.item` | `"PLAYER_HEAD"` | Head hiển thị cho spawner này trong menu |
| `mob_head.hash_texture` | `"abc123..."` | Hash texture cho player head; dùng `null` cho head vanilla |

### Thuộc Tính Vật Phẩm

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `item` | `ARROW` | Vật phẩm sẽ rơi |
| `amount` | `1-3` | Khoảng số lượng vật phẩm mỗi chu kỳ |
| `chance` | `50.0` | Xác suất rơi (0.0 đến 100.0) |
| `durability` | `1-384` | Khoảng độ bền cho công cụ và vũ khí. Cũng nhận một giá trị đơn như `100`. |

## Tỷ Lệ Rơi Spawner Khi Phá

Thuộc tính `drop_chance` quyết định **vật phẩm spawner** có rơi ra khi spawner bị phá hay không. Nó độc lập với `chance` của loot.

- Nếu **không đặt** `drop_chance`, spawner luôn rơi (100%).
- Nếu có đặt, mỗi lần phá có đúng tỷ lệ đó để nhận lại vật phẩm spawner.
- Khi bật `sneak_break`, spawner có `drop_chance` **không thể** bị phá cả stack khi cúi; người chơi phải phá từng chiếc.
- Người có `smartspawner.break.bypassdropchance` luôn nhận vật phẩm và dùng được mọi tính năng stack.

## Ví Dụ

### Mob Dùng Custom Head

```yaml
cow:
  experience: 3
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "b667c0e107be79d7679bfe89bbc57c6bf198ecb529a3295fcfdfd2f24408dca3"
  loot:
    1:
      item: LEATHER
      amount: 0-2
      chance: 66.67
    2:
      item: BEEF
      amount: 1-3
      chance: 100.0
```

### Mob Có Vũ Khí

```yaml
wither_skeleton:
  experience: 5
  mob_head:
    item: "WITHER_SKELETON_SKULL"
    hash_texture: null
  loot:
    1:
      item: COAL
      amount: 0-1
      chance: 33.33
    2:
      item: BONE
      amount: 0-2
      chance: 66.67
    3:
      item: STONE_SWORD
      amount: 1-1
      chance: 8.5
      durability: 1-131
```

### Mob Có Potion Và Đồ Phù Phép

```yaml
witch:
  experience: 5
  loot:
    1:
      item: 'potion[potion_contents={potion:"minecraft:strength"}]'
      amount: 0-1
      chance: 5.0
    2:
      item: 'diamond_sword[enchantments={"minecraft:sharpness":5}]'
      amount: 1-1
      chance: 0.5
```

### Mob Có Tỷ Lệ Rơi Spawner

```yaml
allay:
  experience: 0
  drop_chance: 75.0
```

### Mob Không Có Vật Phẩm

```yaml
bat:
  experience: 0
  # Không có mục loot = không tạo vật phẩm
```

### Hai Spawner Cho Cùng Một Mob

Spawner zombie thường, cùng một spawner hiếm hơn rơi vàng và dùng head khác. Spawner custom giữ XP của zombie vì nó không khai báo `experience`.

```yaml
zombie:
  experience: 5
  loot:
    1:
      item: ROTTEN_FLESH
      amount: 0-2
      chance: 100.0

custom_spawners:
  golden_zombie:
    entity: ZOMBIE
    display_name: Golden Zombie
    mob_head:
      item: GOLD_BLOCK
    loot:
      1:
        item: GOLD_INGOT
        amount: 1-2
        chance: 50.0
```

## Cơ Chế Tạo Vật Phẩm

Đầu ra thực tế mỗi chu kỳ:

```
actual_drops = base_amount × random(min_mobs, max_mobs)
```

Với mặc định `min_mobs=1`, `max_mobs=4`:

| Số lượng cấu hình | Đầu ra có thể có |
|-------------------|------------------|
| `1-1` | 1–4 vật phẩm |
| `2-3` | 2–12 vật phẩm |
| `1-2` | 1–8 vật phẩm |

Mỗi mục loot được roll độc lập, vì vậy một chu kỳ có thể tạo nhiều loại vật phẩm cùng lúc.

## Tìm Texture Head

- [Minecraft-Heads.com](https://minecraft-heads.com/)
- [MCHeads.net](https://mc-heads.net/)

Chỉ dùng phần hash trong URL texture, không bao gồm `http://textures.minecraft.net/texture/`.

### Material Head Vanilla

- `SKELETON_SKULL`
- `WITHER_SKELETON_SKULL`
- `ZOMBIE_HEAD`
- `PIGLIN_HEAD`
- `DRAGON_HEAD`

## Cấu Hình Mặc Định

SmartSpawner cung cấp `spawner_mobs.yml` đầy đủ cho mọi mob vanilla với bảng vật phẩm dựa trên [Minecraft Wiki](https://minecraft.wiki).

- **Xem online:** [spawner_mobs.yml trên GitHub](https://github.com/OpenVdra/SmartSpawner/blob/main/core/src/main/resources/spawner_mobs.yml)
- **Đặt lại:** Xóa file rồi khởi động lại máy chủ

::: info Nâng Cấp Từ 1.8
File `spawner_mobs.yml` theo định dạng 1.8 được chuyển đổi ở lần khởi động đầu tiên. Các mục có tên như `zombie_spawner` thành `zombie`, các mục còn lại chuyển vào `custom_spawners`, và file cũ được giữ lại thành `spawner_mobs.yml.1.8-backup`. Spawner đã đặt và item spawner vẫn hoạt động bình thường.
:::

## Trao Spawner

```bash
/ss give <player> <name> [amount]
```

```bash
/ss give Steve skeleton 1
/ss give Player123 golden_zombie 3
```
