# WakeUp 24/7

Ứng dụng Android giữ thiết bị thức trong một phiên do người dùng chủ động bật, hỗ trợ các ứng dụng tự động hóa tiếp tục nhận và xử lý thông báo.

## Chức năng

- Phiên treo vô hạn, 1/5/15/30 phút, 1/2 giờ hoặc số phút tùy chọn.
- Foreground service và thông báo trạng thái có nút **+30 phút** / **Dừng**.
- Màn hình đen, độ sáng rất thấp, ẩn thanh điều hướng và chống chạm nhầm bằng thao tác trượt.
- Screen Pinning khóa Home, Recent, cử chỉ cạnh và ngăn thông báo mở sang ứng dụng khác; chỉ bỏ ghim khi trượt dừng.
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

Cuộc gọi hệ thống và bong bóng chat có thể hiển thị phía trên màn hình ghim nếu ROM cho phép. Khi đóng cuộc gọi/bong bóng, màn hình treo vẫn nằm bên dưới. Android không cho ứng dụng thường tùy ý allowlist ứng dụng khác trong Lock Task; khả năng đó chỉ dành cho Device Owner/kiosk được quản trị doanh nghiệp.
