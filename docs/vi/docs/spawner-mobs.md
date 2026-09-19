# Spawner Mob

File `plugins/SmartSpawner/spawner_mobs.yml` điều khiển bảng vật phẩm, XP, texture head và tỷ lệ rơi tùy chọn cho từng loại mob của Smart Spawner.

## Quản Lý Trong Game

Dùng `/ss edit smartspawner` để chỉnh các mục mob hiện có. GUI này tách biệt với trình sửa Item
Spawner và không có nút chuyển đổi. Để tạo mục mới, dùng:

```bash
/ss add smartspawner <mob> [name] [NBT tag]
```

Đối số mob tự động gợi ý các entity sống và có thể spawn trong phiên bản máy chủ hiện tại, đồng thời
nhận ID có namespace như `minecraft:zombie`. NBT là tùy chọn và mặc định là `{}`. Khi được dùng, NBT
là compound SNBT giống `/summon` và phải có cặp dấu ngoặc nhọn bên ngoài:

Tên cấu hình cũng là tùy chọn và mặc định theo entity, ví dụ `zombie_spawner`. Khoảng trắng trong tên
được tự động đổi thành dấu gạch dưới.

```bash
/ss add smartspawner zombie {}
/ss add smartspawner minecraft:zombie
/ss add smartspawner zombie Boss Room {NoAI:1b,Silent:1b}
```

SmartSpawner kiểm tra NBT mà không spawn entity thật rồi lưu vào `nbt_data`. Entity mang NBT này
được dùng làm model quay bên trong lồng spawner; plugin vẫn tạo loot ảo và không spawn mob đó ra thế
giới. Lệnh không ghi đè mục đã tồn tại.

Màn hình loot có 27 slot, không phân trang và không có item điều hướng. Chỉ có một ô kính xanh lá nằm
ngay sau loot cuối cùng; khi thêm item, ô kính này tự dịch sang slot kế tiếp.

::: info Hệ số vật phẩm
Mỗi chu kỳ tạo vật phẩm chạy từ **min_mobs** đến **max_mobs** lần (mặc định 1–4). Số lượng cấu hình là giá trị cơ sở cho mỗi mob nên đầu ra thực tế có thể lớn hơn.
:::

## Định Dạng Cấu Hình

```yaml
custom_spawner_name:
  entity: MOB_NAME
  experience: <number>
  nbt_data: <SNBT giống lệnh summon> # Có trên mục được tạo bằng /ss add
  drop_chance: <percentage>   # Tùy chọn, mặc định 100.0 nếu bỏ qua
  mob_head:
    item: <MATERIAL>
    hash_texture: <hash>    # null đối với head vanilla
  loot:                       # Tùy chọn
    1:
      item: <item>            # Bắt buộc
      amount: <min>-<max>
      chance: <percentage>
      durability: <min>-<max> # Tùy chọn, cho công cụ và vũ khí
```

## Chỉ Định Vật Phẩm

Mỗi mục trong `loot` chỉ định vật phẩm qua trường `item`. Trường này bắt buộc: mục nào thiếu sẽ bị
bỏ qua và báo trong console.

`item` nhận bốn dạng:

| Dạng | Ví dụ | Dùng khi |
|------|-------|----------|
| Tên material | `ARROW` | Vật phẩm thường |
| Chuỗi vật phẩm của `/give` | `tipped_arrow[potion_contents={potion:"minecraft:poison"}]` | Potion, đồ phù phép, đồ đặt tên, mọi thứ có dữ liệu kèm theo |
| `nbt:` kèm một mã | `nbt:H4sIAAAA...` | Vật phẩm sao chép nguyên vẹn từ trong game |
| Tên plugin, dấu hai chấm, rồi id vật phẩm của plugin đó | `mmoitems:SWORD:CUTLASS` | Vật phẩm thuộc về plugin khác |

Dạng thứ hai chính là chuỗi mà lệnh `/give` gợi ý sẵn trong game. Bạn dựng vật phẩm mong muốn bằng
`/give`, sao chép phần sau tên người chơi, rồi dán vào đây trong dấu nháy đơn.

Dạng thứ tư bắt đầu bằng tên plugin sở hữu vật phẩm; mọi thứ sau dấu hai chấm đầu tiên là cú pháp
riêng của plugin đó, vì vậy một giá trị MMOItems mang hai dấu hai chấm. Hiện chỉ hỗ trợ
[MMOItems](/vi/docs/integrations/mmoitems), và mục sẽ bị bỏ qua kèm thông báo console khi plugin
không được cài. `minecraft:` không phải tên plugin: `minecraft:arrow` được đọc là material vanilla,
còn `nbt` không bao giờ có thể là tên plugin.

Các mục được đánh số, và con số chỉ là vị trí trong danh sách. Dòng `item` mới là thứ quyết định
vật phẩm rơi ra, nhờ vậy cùng một material có thể xuất hiện nhiều lần:

```yaml
poison_bogged_spawner:
  entity: BOGGED
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

Mục nào máy chủ không đọc được sẽ bị bỏ qua và báo trong console kèm tên mob và tên mục. Phần còn
lại của file vẫn nạp bình thường.

## Tham Chiếu Thuộc Tính

### Thuộc Tính Spawner

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `experience` | `5` | XP tạo ra mỗi lần spawner kích hoạt |
| `nbt_data` | `{profile:DrDonutt}` | SNBT kiểu `/summon` dùng cho model entity quay bên trong lồng spawner |
| `drop_chance` | `75.0` | Xác suất vật phẩm Smart Spawner rơi khi bị phá; bỏ qua để dùng 100.0 |
| `material` | `"PLAYER_HEAD"` | Material head hiển thị trong block spawner |
| `hash_texture` | `"abc123..."` | Hash texture cho player head; dùng `null` cho head vanilla |

### Thuộc Tính Vật Phẩm

| Thuộc tính | Định dạng | Mô tả |
|------------|-----------|-------|
| `item` | `ARROW` | Vật phẩm sẽ rơi. Bỏ trống để dùng tên mục. |
| `amount` | `1-3` | Khoảng số lượng vật phẩm mỗi chu kỳ |
| `chance` | `50.0` | Xác suất rơi từ 0.0 đến 100.0 |
| `durability` | `1-384` | Khoảng độ bền của công cụ và vũ khí. Một giá trị đơn như `100` cũng được chấp nhận. |

## Tỷ Lệ Rơi Khi Phá Spawner

`drop_chance` quyết định **vật phẩm spawner** có rơi khi block bị phá hay không. Nó độc lập với `chance` trong `loot`, vốn điều khiển vật phẩm được tạo.

- Bỏ qua `drop_chance`: spawner luôn rơi, tỷ lệ 100%.
- Khi đặt giá trị, mỗi lần phá có xác suất tương ứng để trả lại vật phẩm spawner.
- Khi bật `sneak_break`, spawner có `drop_chance` **không thể** bị phá cả stack khi cúi; người chơi phải phá từng chiếc.
- Người có `smartspawner.break.bypassdropchance` luôn nhận vật phẩm và dùng được mọi tính năng stack.

## Ví Dụ

### Mob Dùng Custom Head

```yaml
cow_spawner:
  entity: COW
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

### Mob Dùng Head Vanilla

```yaml
skeleton_spawner:
  entity: SKELETON
  experience: 5
  mob_head:
    item: "SKELETON_SKULL"
    hash_texture: null
  loot:
    1:
      item: BONE
      amount: 0-2
      chance: 66.67
    2:
      item: ARROW
      amount: 0-2
      chance: 66.67
    3:
      item: BOW
      amount: 1-1
      chance: 8.5
      durability: 1-384
```

### Mob Có Vũ Khí

```yaml
wither_skeleton_spawner:
  entity: WITHER_SKELETON
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
      item: WITHER_SKELETON_SKULL
      amount: 0-1
      chance: 2.5
    4:
      item: STONE_SWORD
      amount: 1-1
      chance: 8.5
      durability: 1-131
```

### Mob Có Tipped Arrow

```yaml
bogged_spawner:
  entity: BOGGED
  experience: 5
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9"
  loot:
    1:
      item: BONE
      amount: 0-2
      chance: 66.67
    2:
      item: 'tipped_arrow[potion_contents={potion:"minecraft:poison"}]'
      amount: 0-2
      chance: 50.0
```

### Mob Có Potion Và Đồ Phù Phép

```yaml
witch_spawner:
  entity: WITCH
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
allay_spawner:
  entity: ALLAY
  experience: 0
  drop_chance: 75.0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
```

### Mob Không Có Vật Phẩm

```yaml
bat_spawner:
  entity: BAT
  experience: 0
  mob_head:
    item: "PLAYER_HEAD"
    hash_texture: "81c5cc1f40005a33124c60384a0f17a36a7b19ae90f1c32dcda17b5b56280a43"
  # Không có mục loot = không tạo vật phẩm
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

## Trao Spawner

```bash
/ss give <player> smart_spawner <name> [amount]
```

```bash
/ss give Steve smart_spawner skeleton_spawner 1
/ss give Player123 smart_spawner wither_skeleton_spawner 3
```
