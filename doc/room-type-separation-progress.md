# 区分 Sala Privada 和 Zona Restauración - 2026-03-25

## 变更内容

### 1. `src/app/services/booking.ts`
- 新增 `RoomType` 类型: `'SALA_PRIVADA' | 'ZONA_RESTAURACION'`
- `DayAvailability` 接口: `bookedTimes` → `bookedTimesByRoom: Record<RoomType, string[]>`
- 响应转换中按 `room` 字段分组已预约时间

### 2. `src/app/components/calendar/calendar.ts`
- 新增 `selectedRoom` signal
- `bookedTimesSet` computed 改为根据选中的房间类型查找对应的已预约时间
- `bookingUrl` 新增 `room` URL 参数
- 新增 `selectRoom()` / `isRoomSelected()` 方法
- 选择日期/切换月份时重置房间选择

### 3. `src/app/components/calendar/calendar.html`
- 新增房间选择器：两个 tab 按钮（🔒 Sala privada / 🍴 Zona restauració）
- 时间选择器仅在选择房间后显示
- 不同房间对应不同的禁用时间

### 4. `src/app/components/calendar/calendar.scss`
- 新增 `.room-selector`, `.room-btn` 样式（flex布局、hover/selected状态）
- 新增 `.time-subtitle` margin 样式

### 5. i18n (es/ca/en)
- 新增 `CALENDAR.SELECT_ROOM` 翻译键

## 验证结果
- ✅ Sala Privada: 17:00 禁用，17:30 可选
- ✅ Zona Restauración: 17:30 禁用，17:00 可选
- ✅ 预约链接包含 `room=SALA_PRIVADA` 或 `room=ZONA_RESTAURACION`
- ✅ 控制台无错误
