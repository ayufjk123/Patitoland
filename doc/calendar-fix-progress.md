# Calendar TypeError Fix - 2026-03-25

## 问题
控制台报错: `TypeError: Cannot read properties of undefined (reading 'forEach')` — 出现在 `calendar.ts:112`

## 根本原因
**前后端响应格式不匹配:**
- 后端 `BookingController.getAvailability()` 返回 **扁平 Map** 格式: `{ "2026-03-05": { totalBookings, available, ... } }`
- 前端 `MonthAvailability` 接口期望: `{ month: "2026-03", days: [{ date, status, ... }] }`
- 导致 `data.days` 为 `undefined`，调用 `.forEach()` 时崩溃

## 修复内容

### 1. `src/app/services/booking.ts`
- 新增 `RawDayInfo` 接口，匹配后端实际返回格式
- 在 `getMonthAvailability()` 中使用 RxJS `map()` 操作符转换响应
- 将后端扁平 Map 转换为前端期望的 `{ month, days }` 结构
- 根据 `available` 和 `totalBookings` 字段推导日历状态 (available/partial/full)

### 2. `src/app/components/calendar/calendar.ts`
- 在 `data.days` 上添加空值保护: `(data.days ?? []).forEach(...)`

## 验证结果
- ✅ 控制台无错误
- ✅ 日历正确渲染，显示颜色编码的可用性
- ✅ 后端 API 数据正确映射到前端组件
