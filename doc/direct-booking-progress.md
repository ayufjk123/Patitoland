# 直接预约功能（替换 Google Form）- 2026-03-26

## 业务规则
- **Sala Privada**: 预约后 3 小时内不可再接单（最多 1 个）
- **Zona Restauración**: 同一 3 小时窗口最多 2 个预约
- **Children count**: 预估值（非精确数字）
- **邮件通知**: 客户确认邮件 + 商家提醒邮件

## 后端变更

### [NEW] `BookingRequest.java`
- DTO: parentName, email, phone, childrenNames, childrenCount, roomPreference, reservationDateTime, notes
- Jakarta validation 注解

### `BookingRepository.java`
- 新增 `findByRoomPreferenceAndReservationDateTimeBetween()` 查询

### `BookingController.java`
- `POST /api/bookings` 端点
- 3 小时窗口验证（Sala max 1, Zona max 2）
- 验证通过 → 保存到数据库 → 发送邮件 → 返回 201
- 冲突 → 返回 409

### `EmailService.java`
- `sendBookingConfirmation()` — 给客户发确认邮件
- `sendBookingNotification()` — 给商家发提醒邮件

## 前端变更

### `booking.ts`
- 新增 `BookingRequest` 接口 + `createBooking()`

### `calendar.ts`
- 新增表单 signals: parentName, email, phone, childrenNames, childrenCount, notes
- 新增提交状态: submitting, submitSuccess, submitError
- 新增 `showForm` computed（日期+房间+时间全选后显示）
- 新增 `submitBooking()` + `resetForm()`
- 移除 GOOGLE_FORM_URL 和 bookingUrl

### `calendar.html`
- 替换 Google Form 链接为内联表单 6 个字段
- 成功/错误消息显示

### `calendar.scss`
- 新增 booking-form, form-group, form-input, form-message 样式

### i18n (es/ca/en)
- 新增 11 个翻译键 (YOUR_DETAILS, PARENT_NAME, EMAIL, PHONE, CHILDREN_NAMES, CHILDREN_COUNT, NOTES, SUBMIT_SUCCESS, SUBMIT_ERROR, PRIVATE_ROOM, RESTAURANT_ZONE)

## 验证结果
- ✅ 前端渲染正常，无控制台错误
- ✅ 选择日期→房间→时间后表单正确显示
- ⏳ 端到端提交测试需后端运行
