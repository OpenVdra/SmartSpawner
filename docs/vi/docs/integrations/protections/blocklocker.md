---
title: BlockLocker
---

# ![](https://hangarcdn.papermc.io/avatars/project/3666.webp?v=1){.plugin-icon} BlockLocker

**Tải về:** [SpigotMC](https://www.spigotmc.org/resources/3268/)

BlockLocker bảo vệ từng khối bằng biển `[Private]`. SmartSpawner kiểm tra lớp bảo vệ đó cho cả ba thao tác.

| Thao tác | Ai được phép |
|---|---|
| Mở menu | Chủ sở hữu hoặc người được tin cậy |
| Xếp chồng | Chủ sở hữu hoặc người được tin cậy |
| Phá | Chủ sở hữu hoặc người được tin cậy |

## Cách quyết định quyền

Khi một spawner bị khóa bằng biển `[Private]`, chỉ chủ sở hữu và những người được liệt kê trên biển `[More Users]` mới có thể mở menu, xếp chồng hoặc phá nó. Những người còn lại bị chặn.

- Spawner không có biển BlockLocker được xem là không được bảo vệ. SmartSpawner để các plugin khác của bạn quyết định.
- Người chơi có quyền bypass của BlockLocker vẫn được cho qua, giống như với các khối bị khóa thông thường.

## Cho phép khóa spawner

BlockLocker chỉ bảo vệ các loại khối được liệt kê trong cấu hình của nó. Mặc định spawner không nằm trong danh sách đó, nên bạn cần thêm một lần.

1. Mở `plugins/BlockLocker/config.yml`.
2. Tìm danh sách `protectableContainers`.
3. Thêm dòng này vào danh sách:

   ```yaml
   - minecraft:spawner
   ```

4. Khởi động lại server để BlockLocker đọc thay đổi.

## Khóa spawner trong game

1. Cầm một tấm biển và nhấn giữ sneak.
2. Trong khi sneak, chuột phải vào spawner để đặt biển lên nó.

Dòng đầu tự điền `[Private]` và tên bạn trở thành chủ sở hữu. Để cho người khác vào, thêm một biển thứ hai bắt đầu bằng `[More Users]` và liệt kê tên họ bên dưới.

::: warning Nhớ sneak khi đặt biển
Chuột phải vào spawner mà không sneak sẽ mở menu SmartSpawner thay vì đặt biển. Hãy giữ sneak để biển được gắn lên spawner.
:::
