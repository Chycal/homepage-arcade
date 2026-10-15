/**
 * 拉斯维加斯的展示常量：赌场、骰子颜色、金额格式化。
 *
 * 抽到这里的理由：赌场名与骰子颜色本来在 `views/LasVegas.vue` 和
 * `components/LasVegasRules.vue` 里各写了一份，改规则时必然漏改一处。
 *
 * 数值口径与后端保持一致：
 * - `DICE_COLORS` 对应 `LasVegasRoom.COLORS`（下标即 colorIndex）；
 * - 赌场名对应 `LasVegasGame.CASINO_COUNT`（点数 1–6）；
 * - 钞票张数与总额对应 `LasVegasBanknotes.TOTAL_NOTES` / `TOTAL_VALUE`。
 * 后端改了这几处，前端必须同步。
 */

/** 玩家/骰子颜色，下标即 colorIndex；与后端 LasVegasRoom.COLORS 一致 */
export const DICE_COLORS = ['#e53935', '#1e88e5', '#43a047', '#fb8c00', '#8e24aa']

/**
 * 六个赌场，按点数 1–6 排列。
 *
 * 用数组（而不是对象）是为了让规则页能直接 v-for 出表格；
 * 需要按点数取名字的地方用 casinoName()。
 */
export const CASINOS = [
  { value: 1, name: 'Golden Nugget' },
  { value: 2, name: "Caesar's Palace" },
  { value: 3, name: 'The Mirage' },
  { value: 4, name: 'Sahara' },
  { value: 5, name: 'Luxor' },
  { value: 6, name: 'Circus Circus' }
]

/** 点数 → 赌场名；越界返回空串，避免页面出现 "undefined" */
export function casinoName(value) {
  const found = CASINOS.find(c => c.value === value)
  return found ? found.name : ''
}

/** 座位号 → 颜色。取模是为了在超出颜色数时循环复用 */
export function diceColor(seat) {
  return DICE_COLORS[seat % DICE_COLORS.length]
}

/** 千分位金额，不带 $ 前缀（模板里自己加 $） */
export function formatMoney(value) {
  return Number(value || 0).toLocaleString('en-US')
}
