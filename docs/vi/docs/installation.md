# Cài Đặt

## Yêu Cầu

Trước khi cài SmartSpawner, hãy đảm bảo máy chủ đáp ứng:

| Yêu cầu | Thông số |
|---------|----------|
| **Phiên bản Minecraft** | 1.21.6+ |
| **Phần mềm máy chủ** | [Paper](https://papermc.io/downloads/paper), [Folia](https://papermc.io/downloads/folia), [Purpur](https://purpurmc.org/) hoặc fork tương thích |
| **Phiên bản Java** | Java 25+ |

## Tải Xuống

Chọn nguồn tải bạn muốn:

<div style="display: flex; gap: 12px; flex-wrap: wrap; margin: 1.5rem 0;">
  <a href="https://modrinth.com/plugin/smartspawner" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/modrinth_vector.svg" alt="Modrinth" style="height: 24px;">
    Modrinth
  </a>
  <a href="https://hangar.papermc.io/Nighter/SmartSpawner" target="_blank" rel="noreferrer" style="display: inline-flex; align-items: center; gap: 8px; padding: 10px 16px; background: var(--vp-c-bg-soft); border: 1px solid var(--vp-c-border); border-radius: 8px; text-decoration: none; color: var(--vp-c-text-1); font-weight: 600;">
    <img src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/compact/available/hangar_vector.svg" alt="Hangar" style="height: 24px;">
    Hangar
  </a>
</div>

## Các Bước Cài Đặt

### 1. Cài Plugin

1. **Dừng hoàn toàn máy chủ**
2. Tải file `.jar` mới nhất từ một nguồn phía trên
3. Đặt file vào thư mục `plugins/`
4. **Khởi động máy chủ**; tránh dùng `/reload` vì có thể gây lỗi

### 2. Kiểm Tra

Chạy lệnh sau trong console hoặc trong game:

```
/plugins
```

SmartSpawner phải xuất hiện màu xanh trong danh sách.

### 3. Các File Cấu Hình

Plugin tự tạo file trong `plugins/SmartSpawner/`:

| File | Mô tả |
|------|-------|
| `config.yml` | Cấu hình chính: hành vi spawner, kinh tế, hiệu ứng |
| `spawner_mobs.yml` | Bảng vật phẩm và XP cho từng loại mob |
| `spawner_items.yml` | Bảng vật phẩm cho Item Spawner |
| `sell_integration.yml` | Kinh tế, tích hợp cửa hàng và giá bán tùy chỉnh |
| `activity_log.yml` | Ghi nhật ký thao tác ra file và Discord |
| `spawners.db` | Dữ liệu spawner lâu dài ở chế độ SQLite |
| `language/` | Thư mục ngôn ngữ và thông báo có thể dịch |
| `gui_layouts/` | Thư mục bố cục GUI |
| `auraskills.yml` | Thiết lập AuraSkills nếu plugin này được cài |

## Cập Nhật

1. **Tải** phiên bản mới
2. **Dừng** máy chủ
3. **Thay** file `.jar` cũ
4. **Khởi động** lại máy chủ

::: info Chuyển đổi tự động
SmartSpawner tự nâng file cấu hình lên định dạng mới nhất khi khởi động và tạo bản sao lưu file cũ.
:::

## Nhận Hỗ Trợ

1. Kiểm tra **log console** để tìm lỗi
2. Tham gia **[Discord](https://discord.gg/zrnyG4CuuT)** để nhận hỗ trợ cộng đồng
3. Báo lỗi tại **[GitHub Issues](https://github.com/OpenVdra/SmartSpawner/issues)**
