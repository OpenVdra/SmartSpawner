---
title: File Ngôn Ngữ
---

# File Ngôn Ngữ

Mọi tin nhắn, chữ trong menu và tên vật phẩm đều lấy từ các file trong `plugins/SmartSpawner/language/<ngôn ngữ>/`. Chọn ngôn ngữ bằng `language` trong `config.yml`, hoặc đổi trực tiếp với `/ss language <tên>`.

Chỉ ngôn ngữ đang dùng được ghi vào thư mục plugin. Ngôn ngữ có sẵn khác sẽ được thêm vào lần đầu được chọn.

## Các File

| File | Nội dung |
|------|----------|
| `messages.yml` | Tin nhắn chat, tiêu đề, thanh hành động và âm thanh. Tin nhắn của lệnh nằm trong `command`. |
| `gui.yml` | Tiêu đề và nút của menu, nhóm theo menu: `main`, `storage`, `sell_confirm`, `filter`. Menu mở bằng lệnh nằm trong `command`. |
| `items.yml` | Tên và mô tả vật phẩm spawner, cùng các dòng hologram trong `hologram`. |
| `formatting.yml` | Định dạng số và tên mob. |
| `hooks/iridium_skyblock.yml` | Tên quyền IridiumSkyblock. Chỉ được ghi khi có cài plugin đó. |

Key bị thiếu trong một file sẽ dùng key tương ứng bằng tiếng Anh, nên ngôn ngữ dịch chưa xong vẫn hiện đủ chữ.

Một tin nhắn gồm nhiều phần: `message` (chat), `title`, `subtitle`, `action_bar` và `sound`. Xóa một phần để không gửi phần đó, hoặc đặt `enabled: false` để tắt cả tin nhắn.

## Màu Và Kiểu Chữ

Mọi đoạn chữ đều dùng được MiniMessage, mã `&` cũ, hoặc cả hai trong cùng một dòng.

| Kiểu | MiniMessage | Mã cũ |
|------|-------------|-------|
| Màu có tên | `<red>Chữ` | `&cChữ` |
| Màu hex | `<#5DB5F1>Chữ` | `&#5DB5F1Chữ` |
| Chuyển màu | `<gradient:#5DB5F1:#FFFFFF>Chữ</gradient>` | không có |
| In đậm | `<bold>Chữ` | `&lChữ` |
| Xóa định dạng | `<reset>` | `&r` |

Các file đi kèm dùng MiniMessage. Chữ đã sửa bằng mã `&` vẫn hoạt động và không cần đổi.

Mã màu cũ vẫn xóa kiểu in đậm `&l` đứng trước nó, như trước đây. Viết `&a&lChữ` để có chữ xanh in đậm, không phải `&l&aChữ`.

Chuyển màu được hiển thị thành một màu cho mỗi chữ cái.

## Click Và Hover

`<click>`, `<hover>` và `<insert>` dùng được trong phần `message` của `messages.yml`, tức dòng chat:

```yaml
command:
  reload:
    success:
      message: '<click:open_url:https://docs.smartspawner.site>Đã tải lại. <u>Mở tài liệu</u></click>'
```

Ở mọi nơi khác, trong menu, vật phẩm, tiêu đề, thanh hành động và hologram, các thẻ này hiện đúng như đã gõ và không có tác dụng. Trên vật phẩm, click có thể theo vật phẩm vào chat qua plugin khác, nên không bao giờ được thêm vào.

Không dùng được placeholder bên trong click hoặc hover, và giá trị của placeholder không bao giờ trở thành click. Người chơi đặt tên vật phẩm là `<click:run_command:/op me>` chỉ hiện ra đúng chữ đó. `<selector>`, `<score>` và `<nbt>` không dùng được ở đâu cả.

Xem [định dạng MiniMessage](https://docs.advntr.dev/minimessage/format.html) để biết mọi thẻ.

## Placeholder

Placeholder được viết dạng `{tên}`, ví dụ `{amount}`. Dạng MiniMessage `<amount>` cũng dùng được. Chú thích phía trên mỗi key liệt kê các placeholder nó nhận.

Placeholder không bao giờ tự thêm màu. Tên người chơi hoặc vật phẩm có chứa `<red>` sẽ hiển thị đúng như đã gõ.

Để hiện một từ trong ngoặc nhọn giống placeholder dưới dạng chữ thường, ví dụ trong dòng hướng dẫn lệnh, thêm dấu gạch chéo ngược phía trước: `\<value>`.

## Chữ In Hoa Nhỏ

`<smallcaps>Open Storage</smallcaps>` hiển thị thành `ᴏᴘᴇɴ ꜱᴛᴏʀᴀɢᴇ`. Placeholder bên trong cũng được chuyển. Chữ không có dạng in hoa nhỏ, như tiếng Trung hay tiếng Việt có dấu, được giữ nguyên.

`{entity_small}` là tên mob ở dạng in hoa nhỏ. Tên cũ `{ᴇɴᴛɪᴛʏ}` vẫn dùng được.

## Ngôn Ngữ Riêng Của Mỗi Người Chơi

Đặt `language_auto_detect: true` trong `config.yml` để tin nhắn và menu hiện theo ngôn ngữ trong game client của từng người chơi, khi có thư mục cho ngôn ngữ đó. Khi đó mọi ngôn ngữ có sẵn đều được ghi vào thư mục plugin. Ngôn ngữ client không có thư mục sẽ dùng `language`. Client tiếng Tây Ban Nha vùng Mexico dùng `es_ES` khi không có `es_MX`.

Vật phẩm spawner, hologram, tên mob và console luôn dùng `language`, vì mọi người cùng thấy một vật phẩm hay hologram.

File `texts.yml` của một GUI layout chỉ có một ngôn ngữ. Thêm `texts_<ngôn ngữ>.yml` bên cạnh, ví dụ `texts_vi_VN.yml`, chứa chữ cho người chơi dùng ngôn ngữ đó.

## Thêm Ngôn Ngữ

1. Tạo thư mục trong `language/`, ví dụ `de_DE`.
2. Chép các file từ `en_US/` vào và dịch phần chữ. Giữ nguyên key và placeholder.
3. Chạy `/ss language de_DE`.

Ngôn ngữ tự thêm vẫn được cập nhật khi key được đổi tên ở phiên bản sau. Key mới không được thêm vào và sẽ hiện bằng tiếng Anh cho đến khi được dịch, nên hãy so sánh với `en_US` sau khi cập nhật.

## Nâng Cấp Từ 1.8.3 Trở Về Trước

Thư mục ngôn ngữ theo kiểu cũ, mỗi thư mục tám file, được chuyển đổi ở lần khởi động đầu tiên, kể cả thư mục tự thêm. Mọi chữ đã sửa được giữ lại dưới tên mới, và file cũ được lưu trong `language.backup-<ngày>/`. Key mà plugin không biết vẫn được giữ và được liệt kê trong console.
