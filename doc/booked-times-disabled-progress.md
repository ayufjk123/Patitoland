# 已预约时间不可选功能 - 2026-03-25

## 变更内容

### 1. `src/app/services/booking.ts`
- `DayAvailability` 接口新增 `bookedTimes: string[]` 字段
- 响应转换中从后端 `slots[].time` 提取已预约时间列表

### 2. `src/app/components/calendar/calendar.ts`
- 新增 `bookedTimesSet` computed：将选中日期的已预约时间转为 Set 便于 O(1) 查找
- 新增 `isTimeBooked()` 方法检查时间是否已被预约
- `selectTime()` 增加 booked 守卫，阻止选中已预约时间

### 3. `src/app/components/calendar/calendar.html`
- 时间按钮添加 `[class.disabled]` 和 `[disabled]` 绑定

## 验证结果
- ✅ 已预约的时间（如 17:00, 17:30）显示为灰色不可点击
- ✅ 未预约的时间正常可选
- ✅ 控制台无错误
