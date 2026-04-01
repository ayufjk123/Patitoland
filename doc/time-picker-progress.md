# 时间选择器功能 - 2026-03-25

## 变更内容

### 1. `src/app/components/calendar/calendar.ts`
- **移除** 周二=关闭的错误逻辑（`isTuesday` 判断已删除）
- **新增** `selectedTime` signal 保存选中时间
- **新增** `availableTimes` computed：工作日 17:00-20:45，周末/节假日 11:00-20:45（15分钟间隔）
- **新增** `bookingUrl` computed：日期+时间自动拼入 Google Form 链接
- **新增** 节假日集合 `HOLIDAYS` + `isWeekendOrHoliday()` 方法
- **新增** `generateTimeSlots()` 生成时间数组
- **新增** `selectTime()` / `isTimeSelected()` 交互方法

### 2. `src/app/components/calendar/calendar.html`
- 日期详情面板中新增时间选择器 grid
- 时间按钮 pill 形式，选中高亮（橙色渐变）
- 预约按钮绑定 `bookingUrl()` 含日期+时间参数

### 3. `src/app/components/calendar/calendar.scss`
- 新增 `.time-picker`, `.time-picker-title`, `.time-grid`, `.time-slot` 完整样式
- hover/selected/disabled 状态动画
- 移动端响应式适配（600px 断点）

### 4. i18n 翻译
- `es.json`: 新增 `CALENDAR.SELECT_TIME: "Selecciona la hora"`
- `ca.json`: 新增 `CALENDAR.SELECT_TIME: "Selecciona l'hora"`
- `en.json`: 补全整个 CALENDAR section + `SELECT_TIME: "Select a time"`

## 验证结果
- ✅ 工作日显示 16 个时间按钮（17:00-20:45）
- ✅ 选中时间后按钮橙色高亮
- ✅ 预约链接正确包含 `?date=2026-03-27&time=18:00` 参数
- ✅ 控制台无错误
- ✅ 周二不再显示为关闭状态
