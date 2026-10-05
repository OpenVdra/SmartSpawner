---
title: Các loại Spawner
---

# Các Loại Spawner

SmartSpawner cung cấp ba loại spawner riêng biệt để trao cho người chơi.

<CardGrid>

<FeatureCard icon="Box" title="Smart Spawner">

Loại spawner chính. Tạo vật phẩm và XP từ mob mà không thực sự sinh mob. Hỗ trợ xếp chồng và quản lý hoàn toàn qua GUI.

- **Nhấp phải** để mở GUI spawner
- **Không sinh mob**, loại bỏ lag do mob
- Hỗ trợ xếp chồng đến giới hạn đã cấu hình
- Lưu vật phẩm trong kho nội bộ nhiều trang

</FeatureCard>

<FeatureCard icon="Package" title="Item Spawner">

Tạo nguyên liệu như kim cương, ngọc lục bảo và thỏi netherite thay vì vật phẩm mob. Mob hiển thị quay bên trong được thay bằng mô hình vật phẩm nổi.

- Cấu hình trong `spawner_items.yml`
- Dùng chung GUI và hệ thống xếp chồng với Smart Spawner
- Nhận bằng `/ss give <player> <name>`

</FeatureCard>

<FeatureCard icon="Sparkles" title="Vanilla Spawner">

Spawner Minecraft thông thường được trao bằng lệnh plugin. Nó sinh mob thật theo cơ chế mặc định.

- Không có GUI hoặc xếp chồng
- Nhận bằng `/ss give <player> vanilla <type>`
- Phù hợp mô hình máy chủ kết hợp

</FeatureCard>

</CardGrid>
