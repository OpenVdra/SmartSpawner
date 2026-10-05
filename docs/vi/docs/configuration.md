# Cấu Hình Chính

File `plugins/SmartSpawner/config.yml` điều khiển ngôn ngữ, hành vi spawner, hiệu ứng, cơ sở dữ liệu và hiệu năng.

Hai phần có file riêng. Bán vật phẩm và bảng giá nằm trong `sell_integration.yml`, ghi nhật ký thao tác nằm trong `activity_log.yml`.

Hầu hết tùy chọn áp dụng khi chạy `/ss reload`. Những tùy chọn ghi RESTART chỉ được đọc lúc máy chủ khởi động.

Có thể chỉnh bảng loot của mob và Item Spawner ngay trong game bằng `/ss editloot <name>`. Xem
trang [Lệnh](/vi/docs/commands#ss-editloot) để biết cách dùng.

Nhấp vào một tùy chọn hoặc nhóm để xem thông tin chi tiết.

## Định Dạng Thời Gian

SmartSpawner hỗ trợ thời lượng ngắn, dễ đọc ở mọi nơi cần thời gian:

| Định dạng | Ý nghĩa |
|-----------|---------|
| `20s` | 20 giây |
| `5m` | 5 phút |
| `1h` | 1 giờ |
| `1d` | 1 ngày |
| `1d_2h_30m_15s` | 1 ngày, 2 giờ, 30 phút, 15 giây |

Đơn vị hỗ trợ: `s` `m` `h` `d` `w` `mo` `y`

---

<div style="background-color: var(--vp-c-bg-alt); padding: 20px; border-radius: 12px; margin-top: 20px;">

<ConfigProperty name="language" value="en_US" type="string">

Thư mục ngôn ngữ được tải từ <code>plugins/SmartSpawner/language/</code>.<br><br>

| Locale | Ngôn ngữ |
|--------|----------|
| <code>en_US</code> | Tiếng Anh |
| <code>en_US_DonutSMP</code> | Tiếng Anh – bố cục DonutSMP |
| <code>en_US_DonutSMP_v2</code> | Tiếng Anh – bố cục DonutSMP v2 |
| <code>tr_TR</code> | Tiếng Thổ Nhĩ Kỳ |
| <code>vi_VN</code> | Tiếng Việt |

Để thêm ngôn ngữ riêng, tạo thư mục mới trong <code>language/</code>, sao chép file từ <code>en_US/</code> làm mẫu rồi dịch nội dung.

</ConfigProperty>

<ConfigProperty name="gui_layout" value="default" type="string">
Thư mục bố cục GUI trong <code>plugins/SmartSpawner/gui_layouts/</code>. Tùy chọn có sẵn: <code>default</code>, <code>DonutSMP</code>, <code>DonutSMP_v2</code>.
</ConfigProperty>

<ConfigGroup name="spawner_properties">
<template #info>
Các thiết lập này áp dụng cho mọi Smart Spawner trên máy chủ. Chúng điều khiển tần suất tạo vật phẩm, phạm vi kích hoạt, dung lượng kho, cách xếp chồng và đặt spawner. Mọi spawner dùng chung các giá trị này; không có ghi đè riêng theo mob ở đây. Các giá trị riêng theo mob như XP và bảng vật phẩm được đặt trong <code>spawner_mobs.yml</code>. Xem [Mob Spawners](/vi/docs/spawner-mobs).
</template>

<ConfigGroup name="default">

<ConfigProperty name="min_mobs" value="1" type="number">
Số mob ảo tối thiểu mỗi chu kỳ. Giá trị thật được chọn ngẫu nhiên giữa <code>min_mobs</code> và <code>max_mobs</code>.
</ConfigProperty>

<ConfigProperty name="max_mobs" value="4" type="number">
Số mob ảo tối đa mỗi chu kỳ.
</ConfigProperty>

<ConfigProperty name="range" value="16" type="number">
Khoảng cách kích hoạt theo block. Spawner chỉ tạo vật phẩm khi có người chơi trong phạm vi này.
</ConfigProperty>

<ConfigProperty name="delay" value="25s" type="string">
Thời gian giữa các chu kỳ, dùng định dạng phía trên.
</ConfigProperty>

<ConfigProperty name="max_storage_pages" value="1" type="number">
Kích thước kho nội bộ. Mỗi trang có 45 slot.
</ConfigProperty>

<ConfigProperty name="max_stored_exp" value="1000" type="number">
XP tối đa spawner lưu trước khi ngừng tạo thêm.
</ConfigProperty>

<ConfigProperty name="max_stack_size" value="10000" type="number">
Số spawner tối đa có thể xếp trong một block.
</ConfigProperty>

<ConfigProperty name="sneak_stack" value="true" type="boolean">
Khi <code>true</code>, khom người lúc phải chuột vào spawner sẽ xếp toàn bộ spawner đang cầm vào chồng cùng lúc. Khi <code>false</code>, mỗi lần bấm chỉ xếp một cái.
</ConfigProperty>

<ConfigProperty name="sneak_place" value="true" type="boolean">
Khi <code>true</code>, khom người lúc đặt spawner sẽ đặt cả chồng đang cầm thành một spawner. Khi <code>false</code>, mỗi lần đặt chỉ một cái.
</ConfigProperty>

<ConfigProperty name="allow_exp_mending" value="true" type="boolean">
Cho phép XP đã lưu sửa vật phẩm có Mending khi người chơi nhận XP.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="true" type="boolean">
Ngăn vụ nổ phá block Smart Spawner.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="spawner_break">
<template #info>
Điều khiển cách người chơi phá và thu thập Smart Spawner.
</template>

<ConfigProperty name="enabled" value="true" type="boolean">
Công tắc chính cho việc phá và thu thập Smart Spawner.
</ConfigProperty>

<ConfigProperty name="direct_to_inventory" value="false" type="boolean">
Đưa spawner thẳng vào inventory thay vì rơi xuống đất.
</ConfigProperty>

<ConfigProperty name="required_tools" :value="['IRON_PICKAXE', 'GOLDEN_PICKAXE', 'DIAMOND_PICKAXE', 'NETHERITE_PICKAXE']" type="list">
Danh sách công cụ được phép phá và thu thập Smart Spawner.
</ConfigProperty>

<ConfigProperty name="durability_loss" value="1" type="number">
Số điểm độ bền trừ khỏi công cụ khi phá spawner.
</ConfigProperty>

<ConfigProperty name="sneak_break" value="true" type="boolean">
Khi bật, cúi trong lúc phá một stack sẽ lấy tối đa 64 spawner cùng lúc.<br><br>
::: warning Tỷ lệ rơi và phá khi cúi
Nếu một loại mob có `drop_chance` trong `spawner_mobs.yml`, không thể phá theo stack trừ khi người chơi có `smartspawner.break.bypassdropchance`.
:::
</ConfigProperty>

<ConfigProperty name="sell_and_xp_break" value="true" type="boolean">
Khi Smart Spawner bị xóa hoàn toàn, tự bán vật phẩm và nhận XP còn lại. Cần tích hợp bán và quyền <code>smartspawner.sellall</code>.
</ConfigProperty>

<ConfigGroup name="silk_touch">

<ConfigProperty name="required" value="true" type="boolean">
Yêu cầu enchantment Silk Touch để nhận vật phẩm spawner.
</ConfigProperty>

<ConfigProperty name="level" value="1" type="number">
Cấp Silk Touch tối thiểu.
</ConfigProperty>

</ConfigGroup>
</ConfigGroup>

<ConfigGroup name="natural_spawner">
<template #info>
Thiết lập cho spawner vanilla sinh tự nhiên trong dungeon, mineshaft và các công trình khác.
</template>

<ConfigProperty name="breakable" value="false" type="boolean">
Cho phép phá và thu thập spawner tự nhiên.
</ConfigProperty>

<ConfigProperty name="convert_to_smart_spawner" value="false" type="boolean">
Nếu bật, spawner tự nhiên bị phá sẽ trở thành Smart Spawner; nếu tắt, nó rơi vật phẩm vanilla spawner.
</ConfigProperty>

<ConfigProperty name="drop_chance" value="(commented out)" type="string">
Xác suất tùy chọn từ <code>0.0</code> đến <code>100.0</code> để spawner tự nhiên rơi vật phẩm. Dùng khóa <code>default</code> cho mọi loại mob và thêm loại mob cụ thể để ghi đè. Nếu bỏ comment hoặc không có giá trị phù hợp, tỷ lệ mặc định là 100%.

```yaml
drop_chance:
  default: 80.0
  ZOMBIE: 75.0
  SKELETON: 50.0
  BLAZE: 25.0
```
</ConfigProperty>

<ConfigProperty name="spawn_mobs" value="true" type="boolean">
Cho phép spawner tự nhiên sinh mob bình thường.
</ConfigProperty>

<ConfigProperty name="protect_from_explosions" value="false" type="boolean">
Bảo vệ block spawner tự nhiên khỏi vụ nổ.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="hopper">
<template #info>
Điều khiển tự chuyển vật phẩm từ kho spawner qua hopper bên dưới.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Bật hopper lấy vật phẩm từ kho spawner.
</ConfigProperty>

<ConfigProperty name="check_delay" value="3s" type="string">
Khoảng thời gian giữa các lần kiểm tra chuyển vật phẩm.
</ConfigProperty>

<ConfigProperty name="stack_per_transfer" value="5" type="number">
Số stack được chuyển mỗi chu kỳ, tối đa 5.
</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="hologram">
<template #info>
Điều khiển văn bản nổi phía trên block spawner.
</template>

<ConfigProperty name="enabled" value="false" type="boolean">
Hiển thị loại spawner và kích thước stack phía trên block.
</ConfigProperty>
<ConfigProperty name="offset_x" value="0.5" type="number">Độ lệch ngang từ tâm block.</ConfigProperty>
<ConfigProperty name="offset_y" value="1.6" type="number">Độ lệch dọc phía trên block.</ConfigProperty>
<ConfigProperty name="offset_z" value="0.5" type="number">Độ lệch chiều sâu từ tâm block.</ConfigProperty>
<ConfigProperty name="alignment" value="CENTER" type="string">Căn chữ: <code>CENTER</code>, <code>LEFT</code> hoặc <code>RIGHT</code>.</ConfigProperty>
<ConfigProperty name="shadowed_text" value="true" type="boolean">Thêm bóng cho chữ hologram.</ConfigProperty>
<ConfigProperty name="see_through" value="false" type="boolean">Cho phép nhìn hologram xuyên block.</ConfigProperty>
<ConfigProperty name="transparent_background" value="false" type="boolean">Loại bỏ nền của hologram.</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="particle">
<template #info>
Hiệu ứng particle tùy chọn cho sự kiện spawner.
</template>

<ConfigProperty name="spawner_stack" value="true" type="boolean">Hiển thị particle khi xếp chồng.</ConfigProperty>
<ConfigProperty name="spawner_activate" value="true" type="boolean">Hiển thị particle khi spawner kích hoạt.</ConfigProperty>
<ConfigProperty name="spawner_generate_loot" value="true" type="boolean">Hiển thị particle khi vật phẩm được thêm vào kho.</ConfigProperty>

</ConfigGroup>

<ConfigGroup name="database">
<template #info>
Cấu hình nơi lưu dữ liệu spawner. Xem <a href="/vi/docs/database-support">Hỗ trợ cơ sở dữ liệu</a> để có hướng dẫn đầy đủ.

::: warning RESTART
Mọi tùy chọn trong mục này, trừ <code>autosave-interval</code>, chỉ được đọc lúc máy chủ khởi động. <code>/ss reload</code> không áp dụng chúng.
:::
</template>

<ConfigProperty name="type" value="SQLITE" type="string">
Backend lưu trữ: <code>SQLITE</code> hoặc <code>MYSQL</code>. Cấu hình còn để <code>YAML</code> sẽ tự chuyển sang <code>SQLITE</code> trong lần khởi động kế tiếp và nhập file cũ một lần.
</ConfigProperty>

<ConfigProperty name="table-prefix" value="sspawner_" type="string">
Tiền tố cho hai bảng plugin tạo ra là <code>sspawner_data</code> và <code>sspawner_schema_meta</code>. Chỉ giữ lại chữ cái, chữ số và dấu gạch dưới, ký tự khác bị loại bỏ. Đổi khi có plugin khác đã dùng tên đó trong cùng cơ sở dữ liệu, hoặc để tách hai bản cài SmartSpawner trong cùng một cơ sở dữ liệu MySQL.

Bảng đang có sẽ được đổi tên tự động khi giá trị này thay đổi.
</ConfigProperty>

<ConfigProperty name="autosave-interval" value="3m" type="string">
Khoảng thời gian giữa hai lần ghi thay đổi spawner xuống cơ sở dữ liệu. Dùng định dạng thời gian ở trên, tối thiểu <code>30s</code>. Dữ liệu cũng được lưu khi thế giới lưu và khi máy chủ tắt, nên đây là lớp bảo vệ chứ không phải lần lưu duy nhất.

Tăng lên trên máy chủ đông người để giảm ghi đĩa. Giảm xuống để thu hẹp lượng hoạt động gần nhất có thể mất nếu máy chủ sập. Đây là tùy chọn duy nhất trong mục này mà <code>/ss reload</code> áp dụng được.
</ConfigProperty>

<ConfigProperty name="sqlite-file" value="spawners.db" type="string">
Tên file cơ sở dữ liệu, nằm trong <code>plugins/SmartSpawner/</code>. Chỉ dùng ở chế độ <code>SQLITE</code>.
</ConfigProperty>

<ConfigProperty name="host" value="localhost" type="string">
Địa chỉ máy chủ cơ sở dữ liệu. Chỉ dùng ở chế độ <code>MYSQL</code>, cùng với năm tùy chọn bên dưới.
</ConfigProperty>

<ConfigProperty name="port" value="3306" type="number">
Cổng máy chủ cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="database" value="smartspawner" type="string">
Tên cơ sở dữ liệu MySQL hoặc MariaDB cần dùng.
</ConfigProperty>

<ConfigProperty name="username" value="root" type="string">
Người dùng cơ sở dữ liệu.
</ConfigProperty>

<ConfigProperty name="password" value="" type="string">
Mật khẩu của người dùng đó.
</ConfigProperty>

<ConfigProperty name="pool-size" value="10" type="number">
Số kết nối tối đa plugin được phép mở cùng lúc. Giá trị mặc định phù hợp với hầu hết máy chủ. SQLite chạy chế độ WAL nên việc đọc không bị chặn trong lúc lưu.
</ConfigProperty>

<ConfigProperty name="server-name" value="server1" type="string">
Tên máy chủ duy nhất cho mô hình MySQL liên máy chủ.
</ConfigProperty>

<ConfigProperty name="sync-across-servers" value="false" type="boolean">
Hiển thị trang chọn máy chủ trong <code>/ss list</code> để xem spawner từ mọi máy chủ dùng chung MySQL. Chỉ có ở chế độ <code>MYSQL</code>.
</ConfigProperty>

<ConfigProperty name="migrate-from-local" value="true" type="boolean">
Tự chuyển dữ liệu local khi đổi chế độ. File đã chuyển được thêm hậu tố <code>.migrated</code> để không bị nhập hai lần.
</ConfigProperty>

</ConfigGroup>


<ConfigGroup name="performance">
<template #info>
Điều khiển cách SmartSpawner tính vật phẩm cho stack cực lớn.
</template>

<ConfigProperty name="approximate_loot" value="true" type="boolean">
Dùng phép tính trung bình nhanh cho batch cực lớn thay vì roll từng mob. Khuyến nghị cho hầu hết máy chủ.
</ConfigProperty>

<ConfigProperty name="approximation_threshold" value="1000" type="number">
Ngưỡng bắt đầu xấp xỉ khi <code>approximate_loot</code> được bật.

| Giá trị | Hành vi |
|---------|---------|
| 10–100 | Rất mạnh, phù hợp stack khổng lồ |
| 100–1000 | Cân bằng hiệu năng và độ chính xác |
| 1000–10000 | Thận trọng, gần với roll chính xác từng mob |
</ConfigProperty>

</ConfigGroup>

</div>
