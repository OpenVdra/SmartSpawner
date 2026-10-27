# Bố Cục GUI

SmartSpawner cho phép tùy chỉnh hoàn toàn GUI spawner bằng các file trong `plugins/SmartSpawner/gui_layouts/`.

## Cấu Trúc Thư Mục

```
plugins/SmartSpawner/gui_layouts/
├── default/
│   ├── main_gui.yml
│   ├── storage_gui.yml
│   └── sell_confirm_gui.yml
├── DonutSMP/
│   ├── ...
│   └── texts.yml
└── DonutSMP_v2/
    └── ...
```

Chọn bố cục bằng `gui_layout` trong `config.yml` hoặc đổi trực tiếp với `/ss gui_layout <name>`.

## Chữ Của Bố Cục

Bố cục có thể có file `texts.yml` thay thế tên, lore và tiêu đề GUI lấy từ `language/<locale>/gui.yml`. File dùng cùng key với `gui.yml` và chỉ ghi những key cần đổi. `DonutSMP` và `DonutSMP_v2` dùng file này cho giao diện của mình, nên chỉ cần chọn bố cục là đủ.

Xóa một key trong `texts.yml` để dùng lại chữ của ngôn ngữ. Bố cục không có `texts.yml` thì dùng chữ của ngôn ngữ ở mọi chỗ. Chữ trong `texts.yml` không được dịch, nên hiển thị giống nhau với mọi `language`. Với người chơi dùng ngôn ngữ khác, thêm `texts_<ngôn ngữ>.yml` bên cạnh, ví dụ `texts_vi_VN.yml`; file này được đọc trước.

## Cách Chỉnh Sửa Của Bạn Được Giữ Lại

Các nút trong file bố cục là của bạn. Dời một nút sang slot khác hoặc xóa nó đi, nó sẽ giữ nguyên như vậy qua mỗi lần khởi động lại và `/ss reload`. Không có nút nào bị đặt lại ở slot cũ.

Đổi lại, nút mới thêm trong bản cập nhật sau sẽ không xuất hiện trong file bạn đang có. Hãy xem changelog khi cập nhật và tự chép nút mới vào nếu bạn muốn dùng. Các tùy chọn không phải nút, ví dụ `skip_main_gui` và `open_sound`, vẫn được thêm tự động.

Muốn làm lại từ đầu, xóa file rồi khởi động lại server. File sẽ trở lại đúng như bản gốc, kèm cả phần chú thích.

## Cooldown Nút

Mọi nút hỗ trợ cooldown tùy chọn riêng cho từng người chơi:

```yaml
slot_12:
  material: CHEST
  enabled: true
  cooldown: "2s"
  click:
    action: "open_storage"
```

Giá trị dùng cùng định dạng thời gian với `config.yml`: `20`, `5s`, `10m`, `1h`, `1m_30s`, v.v.

- Nút đang cooldown không chạy action và gửi thông báo `action_not_ready` với placeholder `{time}`.
- Mọi nút action có debounce chống spam **100 ms** ngay cả khi không đặt `cooldown`.
- Cooldown cũng có thể được ghi đè trong nhánh điều kiện `if:`.

```yaml
slot_14:
  material: PLAYER_HEAD
  enabled: true
  if:
    sell_integration:
      cooldown: "3s"
      click:
        action: "sell_and_exp"
    no_sell_integration:
      click:
        action: "none"
```

## Âm Thanh Khi Nhấn

Nút điều hướng dùng một `sound` khi chuyển trang thành công:

```yaml
slot_1:
  material: ARROW
  enabled: true
  click:
    action: "previous_page"
    sound: ui.button.click
```

Nút action dùng âm thanh riêng khi thành công hoặc thất bại:

```yaml
slot_6:
  material: BUNDLE
  enabled: true
  click:
    action: "take_all"
    sound_success:
      name: entity.item.pickup
      volume: 1.0
      pitch: 1.0
    sound_fail: block.note_block.pling
```

Nhấp trái và phải có thể dùng action cùng âm thanh riêng:

```yaml
left_click:
  action: "sell_and_exp"
  sound: ui.button.click
  sound_success: block.note_block.bell
  sound_fail: block.note_block.pling
right_click:
  action: "collect_exp"
  sound: ui.button.click
```

Mỗi khóa âm thanh nhận một Bukkit key hoặc danh sách kèm `volume` và `pitch`:

```yaml
sound_success:
  - block.note_block.bell
  - name: entity.experience_orb.pickup
    volume: 0.8
    pitch: 1.2
```

Đặt khóa âm thanh thành `none` để tắt.

## Texture Player Head Tùy Chỉnh

Nút dùng `material: PLAYER_HEAD` có thể hiển thị skin texture qua `custom_texture`:

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f"
  enabled: true
  info_button: true
  if:
    sell_integration:
      left_click:
        action: "sell_and_exp"
      right_click:
        action: "none"
    no_sell_integration:
      click:
        action: "none"
```

- `custom_texture` là hash trong URL texture, không gồm tiền tố `http://textures.minecraft.net/texture/`.
- Có thể tìm texture tại [Minecraft-Heads.com](https://minecraft-heads.com/).
- Nếu bỏ qua trên nút thông tin, plugin dùng head mob từ `spawner_mobs.yml`.
- Texture được cache sau lần tải đầu, không ảnh hưởng hiệu năng khi mở GUI sau đó.

::: warning
`custom_texture` bị bỏ qua nếu `material` được ghi đè thành giá trị khác `PLAYER_HEAD` trong nhánh `if:`.
:::

## Ghi Đè Theo Điều Kiện

Dùng `if:` để đổi giao diện hoặc hành vi nút theo trạng thái máy chủ:

```yaml
slot_14:
  material: PLAYER_HEAD
  custom_texture: "default_texture_hash_here"
  enabled: true
  info_button: true
  if:
    sell_integration:
      material: EMERALD
      left_click:
        action: "sell_and_exp"
    no_sell_integration:
      click:
        action: "none"
```

Điều kiện hiện có gồm `sell_integration` và `no_sell_integration`.
