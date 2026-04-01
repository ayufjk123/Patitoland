# 上午/下午分组及日期显示 - 2026-03-26

## 变更内容

### `calendar.ts`
- 移除了损坏的 `loadDateDetail` 及相关的 `dateDetail` signal（因为后端现在直接返回预约列表，而不是时间段摘要）。
- 新增 `formattedSelectedDate` computed：根据 `selectedDate` 本地格式化生成如 "sábado, 28 de marzo de 2026" 的本地化带星期日期。
- 新增 `morningTimes` computed：过滤出 `availableTimes` 中 13:00 以前的时间段。
- 新增 `afternoonTimes` computed：过滤出 `availableTimes` 中 13:00 及以后的时间段。

### `calendar.html`
- 移除了旧的 `dateDetail` 取值逻辑及相关的槽位剩余数量 UI。
- 修复了因为移除旧变量导致的 `@else if (errorDetail())` 编译器崩溃错误。
- 在日历图标 `<i class="fas fa-calendar-day"></i>` 旁显示 `{{ formattedSelectedDate() }}`。
- 移除了统一的 `availableTimes()` 循环，替换为两个带标题的网格：
  1. **上午 (Mañana/Matí)** 循环渲染 `morningTimes()`。
  2. **下午 (Tarde/Tarda)** 循环渲染 `afternoonTimes()`。

### `calendar.scss`
- 增加了 `.time-group` 和 `.time-group-title` 样式，增加左侧边框及间距，使用界面主色调区分。

## 验证结果
- ✅ 浏览器 UI 完全符合预期。
- ✅ 周六/周日的 11:00-12:45 会分在“上午”栏，13:00 之后的分在“下午”栏。
- ✅ 工作日只有“下午”栏可见。
- ✅ 没有 Angular 编译错误或浏览器控制台报错。
