# 3 小时窗口禁用 UI - 2026-03-26

## 变更内容

### `src/app/components/calendar/calendar.ts`
- 新增 `BLOCK_MINUTES = 180` 和 `ROOM_MAX` 常量
- `bookedTimesSet` computed 改为计算 3 小时窗口：
  - 对每个可用时间段，统计 ±3h 内有多少已预约
  - Sala Privada: 1 个预约即全部禁用 → 17:00–19:45 灰色
  - Zona Restauración: 需 2 个预约才禁用
- 新增 `timeToMinutes()` 辅助方法

## 验证结果
- ✅ Sala Privada: 17:00 有预约 → 17:00–19:45 全部灰色，20:00–20:45 可选
- ✅ Zona Restauración: 仅 1 个预约 → 全部可选（还可接 1 单）
- ✅ 控制台无错误
