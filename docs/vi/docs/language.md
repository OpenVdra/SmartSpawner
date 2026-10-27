---
title: File Ngôn Ngữ
---

# File Ngôn Ngữ

Mọi tin nhắn, chữ trong menu và tên vật phẩm đều lấy từ các file trong `plugins/SmartSpawner/language/<ngôn ngữ>/`. Chọn ngôn ngữ bằng `language` trong `config.yml`, hoặc đổi trực tiếp với `/ss language <tên>`.

Chỉ ngôn ngữ đang dùng được ghi vào thư mục plugin. Ngôn ngữ có sẵn khác sẽ được thêm vào lần đầu được chọn.

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

Chưa hỗ trợ thẻ click và hover. Chuyển màu được hiển thị thành một màu cho mỗi chữ cái.

Xem [định dạng MiniMessage](https://docs.advntr.dev/minimessage/format.html) để biết mọi thẻ.

## Placeholder

Placeholder được viết dạng `{tên}`, ví dụ `{amount}`. Dạng MiniMessage `<amount>` cũng dùng được. Chú thích phía trên mỗi key liệt kê các placeholder nó nhận.

Placeholder không bao giờ tự thêm màu. Tên người chơi hoặc vật phẩm có chứa `<red>` sẽ hiển thị đúng như đã gõ.

Để hiện một từ trong ngoặc nhọn giống placeholder dưới dạng chữ thường, ví dụ trong dòng hướng dẫn lệnh, thêm dấu gạch chéo ngược phía trước: `\<value>`.

## Chữ In Hoa Nhỏ

`<smallcaps>Open Storage</smallcaps>` hiển thị thành `ᴏᴘᴇɴ ꜱᴛᴏʀᴀɢᴇ`. Placeholder bên trong cũng được chuyển. Chữ không có dạng in hoa nhỏ, như tiếng Trung hay tiếng Việt có dấu, được giữ nguyên.

`{entity_small}` là tên mob ở dạng in hoa nhỏ. Tên cũ `{ᴇɴᴛɪᴛʏ}` vẫn dùng được.

## Thêm Ngôn Ngữ

1. Tạo thư mục trong `language/`, ví dụ `de_DE`.
2. Chép các file từ `en_US/` vào và dịch phần chữ. Giữ nguyên key và placeholder.
3. Chạy `/ss language de_DE`.

Ngôn ngữ tự thêm vẫn được cập nhật khi key được đổi tên ở phiên bản sau. Key mới không được thêm vào, nên hãy so sánh với `en_US` sau khi cập nhật.
