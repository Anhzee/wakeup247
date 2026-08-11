# WakeUp 24/7

Ứng dụng Android giữ thiết bị thức trong một phiên do người dùng chủ động bật, hỗ trợ các ứng dụng tự động hóa tiếp tục nhận và xử lý thông báo.

## Chức năng

- Phiên treo vô hạn, 1/5/15/30 phút, 1/2 giờ hoặc số phút tùy chọn.
- Foreground service và thông báo trạng thái có nút **+30 phút** / **Dừng**.
- Màn hình đen, độ sáng rất thấp, ẩn thanh điều hướng và chống chạm nhầm bằng thao tác trượt.
- Lớp bảo vệ nằm trên các ứng dụng: Home/Recent có thể đổi tác vụ phía dưới nhưng không làm lộ hoặc thao tác được app đó trước khi trượt dừng.
- Thanh thông báo, nội dung mở rộng, trả lời nhanh và điều khiển nhạc của System UI vẫn hoạt động.
- Thanh trạng thái và điều hướng mặc định ẩn; vuốt từ mép trên để hiện bảng thông báo rồi tự ẩn lại khi đóng.
- Nền bảo vệ không nhận chạm; chỉ thanh trượt là cửa sổ tương tác, nên cử chỉ mép trên được chuyển cho System UI mà app phía dưới vẫn bị che/chặn.
- Khi phiên đã chạy, chạm phím nhanh sẽ mở lại màn hình treo thay vì dừng nhầm phiên; trên màn hình khóa dùng đường mở Activity riêng của TileService.
- Trên Xiaomi/HyperOS có nút mở thẳng **Quyền khác** để bật “Hiển thị trên màn hình khóa” và “Mở cửa sổ khi chạy nền”; tile mở Activity rồi thu bảng Quick Settings trên keyguard.
- Cử chỉ Back mới bị chặn; Home/Recent hoặc trợ lý AI làm rời màn hình treo sẽ bị phát hiện và task treo được đưa lại lên trước ngay.
- Quick Settings tile **Treo máy** để bật vô hạn hoặc dừng nhanh.
- Hiển thị phía trên màn hình khóa; khi bấm nút nguồn trong phiên treo, app thử bật lại màn hình bảo vệ.
- Khôi phục phiên sau khi khởi động máy và tùy chọn dừng khi pin thấp.
- Trang hướng dẫn quyền thông báo và loại trừ tối ưu pin.
- Mục cập nhật trong ứng dụng tự kiểm tra GitHub Release mới nhất và mở đúng APK để tải.

## Build và cài đặt

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

APK debug sau khi build nằm tại `app/build/outputs/apk/debug/app-debug.apk`.

## Giới hạn Android cần biết

WakeUp 24/7 giữ CPU và màn hình hoạt động, nhờ đó thiết bị thường không đi vào Doze. Nó không thể sửa quyền hoặc chính sách chạy nền của ứng dụng khác. Trên Xiaomi/HyperOS, OPPO, vivo và một số hãng khác, người dùng vẫn cần bật **Tự khởi động** và đặt cả ứng dụng auto lẫn Telegram thành **Không hạn chế pin**.

Android và từng ROM có thể vẫn tắt màn hình khi bấm nút nguồn; app sẽ thử đánh thức lại trong phiên treo nhưng hãng máy có quyền chặn hành vi này. Thông báo nổi có xuất hiện hay không phụ thuộc kênh thông báo của chính ứng dụng gửi.

Cuộc gọi hệ thống và bong bóng chat có thể hiển thị phía trên lớp bảo vệ nếu ROM cho phép. Khi đóng cuộc gọi/bong bóng, màn hình treo vẫn nằm bên dưới. Cơ chế này cần quyền đặc biệt **Hiển thị trên ứng dụng khác** và không tự khóa thiết bị khi bắt đầu phiên.
