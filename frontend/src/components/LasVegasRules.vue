<template>
  <div class="rl">
    <!-- 速览 -->
    <div class="rl-card">
      <h2>一句话规则</h2>
      <p class="rl-lead">
        掷出你剩下的<b>全部</b>骰子，从中选<b>一个点数</b>，把这个点数的<b>全部</b>骰子
        放进对应赌场抢钞票。共 <b>4 轮</b>，最后钱最多的赢。
      </p>
      <ul class="rl-facts">
        <li v-for="f in facts" :key="f.k">
          <span class="rl-fact-k">{{ f.k }}</span>
          <span class="rl-fact-v">{{ f.v }}</span>
        </li>
      </ul>
    </div>

    <!-- 配件 -->
    <div class="rl-card">
      <h2>1. 配件</h2>

      <h3>骰子</h3>
      <p>
        共 <b>40 颗</b>，<b>5 种颜色</b>，每色 <b>8 颗</b>。每位玩家拿一色。
        基础版骰子<b>完全同质</b>，没有大小之分，只看颗数。这也决定了人数上限是 5 人。
      </p>

      <h3>赌场（6 个，点数 1–6）</h3>
      <table class="rl-table">
        <thead>
          <tr><th>点数</th><th>赌场</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in casinos" :key="c.value">
            <td class="rl-num">{{ c.value }}</td>
            <td>{{ c.name }}</td>
          </tr>
        </tbody>
      </table>

      <h3>钞票（54 张）</h3>
      <table class="rl-table">
        <thead>
          <tr><th>面额</th><th>张数</th></tr>
        </thead>
        <tbody>
          <tr v-for="n in banknotes" :key="n.value">
            <td class="rl-num">${{ money(n.value) }}</td>
            <td>{{ n.count }} 张</td>
          </tr>
          <tr class="rl-total">
            <td>合计</td>
            <td>54 张 · ${{ money(2500000) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 开局 -->
    <div class="rl-card">
      <h2>2. 开局设置</h2>
      <ol class="rl-steps">
        <li>6 个赌场按点数 1→6 依次横排在桌面中央。</li>
        <li>54 张钞票洗混，面朝下成一叠放在旁边。</li>
        <li>
          给每个赌场发钞票，<b>直到该赌场总额 ≥ $50,000</b> 为止，所有面额朝上可见。
          <span class="rl-note">是「加到不少于 5 万」，不是「恰好 5 万」，所以每个赌场实际是 $50,000–$99,000 不等。</span>
        </li>
        <li>每位玩家选一色，拿 8 颗骰子。</li>
        <li>决定起始玩家（本站由房主开局，起始玩家随机）。</li>
      </ol>
    </div>

    <!-- 回合流程 -->
    <div class="rl-card">
      <h2>3. 回合流程</h2>
      <p>整局 <b>4 轮</b>。每轮里每位玩家大约有 4–5 次投骰机会。</p>

      <div class="rl-turnflow">
        <div class="rl-flow-step">
          <span class="rl-flow-no">1</span>
          <div>
            <b>掷</b>
            <p>掷出自己手上<b>全部剩余骰子</b>（轮初 8 颗，越往后越少）。</p>
          </div>
        </div>
        <div class="rl-flow-step">
          <span class="rl-flow-no">2</span>
          <div>
            <b>选</b>
            <p>从掷出的结果里，<b>必须且只能选一个点数</b>。</p>
          </div>
        </div>
        <div class="rl-flow-step">
          <span class="rl-flow-no">3</span>
          <div>
            <b>放</b>
            <p>把该点数的<b>全部</b>骰子放到对应点数的赌场。</p>
          </div>
        </div>
      </div>

      <div class="rl-warn">
        <b>⚠ 最容易搞错的一条：不能只放一部分。</b>
        <p>
          例：掷出 4 个 3 和 1 个 5。若你选 3，那 <b>4 颗 3 必须一起</b>放进 Mirage（点数 3 的赌场），
          不能只放 1–3 颗；那颗 5 这回合就<b>白掷了</b>，只能留着下回合再掷。
        </p>
        <p>
          赌场里已经有谁的骰子都无所谓，也不要求这个点数是「你独有的」。
        </p>
      </div>

      <h3>骰子用完了怎么办</h3>
      <p>
        该玩家本轮剩余回合<b>全部跳过</b>。所以轮末经常出现只剩一个人还有骰子、
        连续行动好几次的情况。
      </p>

      <h3>一轮什么时候结束</h3>
      <p>
        当<b>最后一名玩家把最后一颗骰子</b>放进赌场后，立刻结算 6 个赌场，然后进入下一轮。
      </p>
    </div>

    <!-- 结算 -->
    <div class="rl-card">
      <h2>4. 结算规则（核心）</h2>
      <p class="rl-lead">
        一轮结束、或所有人骰子用完时，<b>逐个赌场</b>单独结算。分三步，顺序不能颠倒。
      </p>

      <div class="rl-phase">
        <span class="rl-phase-tag">第一步</span>
        <h3>平手作废（必须先做）</h3>
        <p>
          若在同一个赌场里，<b>有 2 名或以上玩家的骰子数相同</b>，
          这些玩家<b>全部</b>把骰子收回，且从这个赌场<b>什么都拿不到</b>。
        </p>
        <p class="rl-note">
          注意是「按骰子数量分组，整组作废」，不是「只作废并列第一」。
          2/2/1/1 这种两组都并列的情况，会<b>四个人全灭</b>。
        </p>
      </div>

      <div class="rl-phase">
        <span class="rl-phase-tag">第二步</span>
        <h3>按骰子数从多到少发钱</h3>
        <ol class="rl-steps">
          <li>骰子<b>最多</b>的玩家：收回自己在该赌场的全部骰子，拿走该赌场<b>面额最高</b>的钞票。</li>
          <li>其余玩家按骰子数<b>降序</b>依次拿剩下的钞票（次高 → 再低 → ……）。</li>
          <li>钞票不够分时，排在后面的玩家<b>空手而归</b>，收骰子走人。</li>
          <li>每名玩家在每个赌场<b>最多只能拿 1 张</b>钞票。</li>
        </ol>
      </div>

      <div class="rl-phase">
        <span class="rl-phase-tag">第三步</span>
        <h3>剩余钞票</h3>
        <p>
          分配完仍有剩余钞票 → 面朝下<b>放回钞票堆底部</b>（不是移出游戏，后续轮次还会被发出来）。
          玩家赢到的钱面朝下放在自己面前。
        </p>
      </div>

      <!-- 交互演示 -->
      <div class="rl-demo">
        <h3>🧪 动手试一下结算</h3>
        <p class="rl-note">
          调整下面 5 位玩家在<b>同一个赌场</b>里的骰子数，看看谁作废、谁能拿到哪张钞票。
          赌场当前钞票：{{ demoNotes.map(n => '$' + money(n)).join('、') }}。
        </p>

        <div class="rl-demo-players">
          <div v-for="(name, i) in demoNames" :key="i" class="rl-demo-player">
            <span class="rl-demo-name">{{ name }}</span>
            <div class="rl-demo-ctrl">
              <button class="rl-stepper" @click="bump(i, -1)" :disabled="demoDice[i] <= 0"
                      :aria-label="name + ' 减少骰子'">−</button>
              <span class="rl-demo-count">{{ demoDice[i] }}</span>
              <button class="rl-stepper" @click="bump(i, 1)" :disabled="demoDice[i] >= 8"
                      :aria-label="name + ' 增加骰子'">+</button>
            </div>
            <span v-if="demoResult.voided.includes(i)" class="rl-tag void">作废</span>
            <span v-else-if="demoAwardOf(i)" class="rl-tag get">
              拿 ${{ money(demoAwardOf(i).note) }}
            </span>
            <span v-else-if="demoDice[i] > 0" class="rl-tag none">空手</span>
            <span v-else class="rl-tag idle">没放骰子</span>
          </div>
        </div>

        <p class="rl-demo-summary">
          <template v-if="demoResult.voided.length">
            作废：{{ demoResult.voided.map(i => demoNames[i]).join('、') }}（骰子数并列）
          </template>
          <template v-else>本赌场没有人骰子数并列，全部正常参与分配。</template>
        </p>
        <p v-if="demoResult.leftover.length" class="rl-demo-summary">
          剩余 {{ demoResult.leftover.length }} 张（{{
            demoResult.leftover.map(n => '$' + money(n)).join('、')
          }}）放回钞票堆底部。
        </p>
      </div>

      <div class="rl-warn">
        <b>完整例子</b>
        <p>
          Caesar's Palace（点数 2）本轮结束时：Anna 放了 5 颗，Benno 和 Carla 各放 3 颗，Denny 放 1 颗。
        </p>
        <p>
          → Benno 和 Carla 骰子数相同（都是 3），<b>两人全部作废</b>，收回骰子、分文不得。
          剩下 Anna(5) 和 Denny(1) 参与分配：Anna 拿最高面额，Denny 拿次高。
        </p>
        <p>
          反例：Anna 和 Carla 各放 2 颗，Benno 和 Denny 各放 1 颗 →
          2/2/1/1，两组都并列，<b>四个人全部作废，该赌场无人得分</b>。
        </p>
      </div>
    </div>

    <!-- 下一轮与结束 -->
    <div class="rl-card">
      <h2>5. 下一轮</h2>
      <ol class="rl-steps">
        <li>给 6 个赌场补钞，使每个再次 <b>≥ $50,000</b>。</li>
        <li>起始玩家<b>传给左手边</b>的玩家。</li>
      </ol>
    </div>

    <div class="rl-card">
      <h2>6. 游戏结束与胜负</h2>
      <ol class="rl-steps">
        <li><b>4 轮</b>结束后各自数钱，<b>钱最多者胜</b>。</li>
        <li>平手 → 比<b>钞票张数</b>（注意是张数，不是面额）多者胜。</li>
        <li>仍然平手 → <b>并列胜利</b>（可以有多位赢家）。</li>
      </ol>
      <p class="rl-note">
        所以别只盯着大钞——多抢几张小额钞票，在平手时可能就是胜负手。
      </p>
    </div>

    <!-- 在线对局说明 -->
    <div class="rl-card">
      <h2>7. 本站对局说明</h2>
      <ul class="rl-list">
        <li><b>人数</b>：2–5 人。房主（第一位进入房间的人）点击「开始对局」后开局，开局后不能再加入。</li>
        <li><b>游客可玩</b>：不登录也能加入对局；但只有<b>登录</b>玩家的对局结果会写进战绩表。</li>
        <li><b>操作</b>：轮到你时，先点「掷骰子」，再从结果里点一个点数放置。</li>
        <li><b>掉线</b>：一人掉线对局继续，重连后回到原座位；若<b>全员掉线</b>，本局作废并回到大厅。</li>
        <li><b>聊天</b>：房间内可发消息。</li>
        <li><b>战绩表</b>：登录后切到「战绩表」标签，可查看自己参与过的对局、名次、奖金和逐位明细。</li>
      </ul>
    </div>

    <!-- AI 对手 -->
    <div class="rl-card">
      <h2>8. 电脑对手（AI）</h2>
      <p class="rl-note">
        房主可以在大厅里添加电脑对手，一个人也能开局。电脑会自己掷骰、自己选点数，
        不需要任何人操作。最多 5 位玩家（真人 + 电脑合计）。
      </p>
      <ul class="rl-list">
        <li>
          <b>三种性格</b>：添加时可选，只影响它愿意冒多大风险。
          <ul class="rl-list">
            <li><b>稳健</b>：优先拿稳的钱，尽量不跟人硬碰。</li>
            <li><b>激进</b>：落后时会主动加注，去抢更大的赌场。</li>
            <li><b>赌徒</b>：明显落后时敢赌高风险的翻盘机会。</li>
          </ul>
        </li>
        <li>
          <b>它怎么算</b>：电脑会在心里把这一局「模拟很多遍」，
          估算每个点数最终大概能带回来多少钱，再挑期望最高的那个；
          越落后的性格越愿意为了更高的上限去承担「可能一分不得」的风险。
        </li>
        <li><b>管理</b>：只有房主能添加 / 移除电脑对手，且只能在大厅里操作；开局后不能增删。</li>
        <li><b>战绩</b>：电脑不是真实账号，它的成绩不会出现在任何人的战绩表里。</li>
        <li><b>移除</b>：如果房间里真人全部离开，电脑会被一并清空。</li>
      </ul>
    </div>

    <!-- 未实现 -->
    <div class="rl-card rl-muted">
      <h2>9. 本实现未包含的官方内容</h2>
      <ul class="rl-list">
        <li><b>中立骰（2–4 人官方变体）</b>：官方为 2–4 人局提供额外中立色骰子以填满赌场，本站未实现。</li>
        <li>
          <b>扩展模块</b>：Biggies、Kickers、彩虹卡、老虎机等属于扩展《Las Vegas Royale》，
          不属于基础版，本站均未实现。
        </li>
      </ul>
    </div>

    <p class="rl-source">
      规则依据：Rüdiger Dorn《Las Vegas》(alea 2012，规则书编号 230512)。
    </p>
  </div>
</template>

<script>
const CASINOS = [
  { value: 1, name: 'Golden Nugget' },
  { value: 2, name: "Caesar's Palace" },
  { value: 3, name: 'The Mirage' },
  { value: 4, name: 'Sahara' },
  { value: 5, name: 'Luxor' },
  { value: 6, name: 'Circus Circus' }
]

const BANKNOTES = [
  { value: 90000, count: 5 },
  { value: 80000, count: 5 },
  { value: 70000, count: 5 },
  { value: 60000, count: 5 },
  { value: 50000, count: 6 },
  { value: 40000, count: 6 },
  { value: 30000, count: 8 },
  { value: 20000, count: 8 },
  { value: 10000, count: 6 }
]

const FACTS = [
  { k: '人数', v: '2–5 人' },
  { k: '骰子', v: '每人 8 颗（40 颗 = 8 颗 × 5 色）' },
  { k: '赌场', v: '6 个（点数 1–6）' },
  { k: '钞票', v: '54 张，共 $2,500,000' },
  { k: '轮数', v: '4 轮' },
  { k: '胜负', v: '钱最多者胜 → 平手比钞票张数 → 再平手并列胜' }
]

export default {
  name: 'LasVegasRules',
  data() {
    return {
      casinos: CASINOS,
      banknotes: BANKNOTES,
      facts: FACTS,
      demoNames: ['Anna', 'Benno', 'Carla', 'Denny', 'Emma'],
      // 默认演示「2/2/1/1 全灭」这个反直觉情形
      demoDice: [2, 1, 2, 1, 0]
    }
  },
  computed: {
    demoNotes() {
      return [90000, 70000, 50000, 30000, 20000]
    },
    /**
     * 与后端 LasVegasPayout.settle 同一套规则：
     * 先整组作废并列者，再按骰子数降序领最高面额钞票。
     */
    demoResult() {
      const byCount = {}
      this.demoDice.forEach((count, seat) => {
        if (count > 0) {
          if (!byCount[count]) byCount[count] = []
          byCount[count].push(seat)
        }
      })

      const voided = []
      Object.keys(byCount).forEach(count => {
        if (byCount[count].length >= 2) voided.push(...byCount[count])
      })

      const ranked = this.demoDice
        .map((count, seat) => ({ seat, count }))
        .filter(x => x.count > 0 && !voided.includes(x.seat))
        .sort((a, b) => b.count - a.count)

      const notes = [...this.demoNotes].sort((a, b) => b - a)
      const awards = []
      ranked.forEach((x, i) => {
        if (i < notes.length) {
          awards.push({ seat: x.seat, count: x.count, note: notes[i] })
        }
      })

      return {
        voided: voided.sort((a, b) => a - b),
        awards,
        leftover: notes.slice(awards.length)
      }
    }
  },
  methods: {
    money(value) {
      return Number(value || 0).toLocaleString('en-US')
    },
    bump(seat, delta) {
      const next = this.demoDice[seat] + delta
      if (next < 0 || next > 8) return
      this.demoDice.splice(seat, 1, next)
    },
    demoAwardOf(seat) {
      return this.demoResult.awards.find(a => a.seat === seat) || null
    }
  }
}
</script>

<style scoped>
.rl {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rl-card {
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 18px 20px;
}

.rl-card h2 {
  font-size: 17px;
  margin: 0 0 12px;
  color: #333;
  padding-bottom: 8px;
  border-bottom: 2px solid #f0f0f5;
}

.rl-card h3 {
  font-size: 14px;
  margin: 16px 0 6px;
  color: #667eea;
}

.rl-card h3:first-of-type {
  margin-top: 8px;
}

.rl-card p {
  font-size: 14px;
  line-height: 1.8;
  color: #444;
  margin: 6px 0;
}

.rl-lead {
  font-size: 15px !important;
  color: #333 !important;
  background: #f7f7fc;
  border-left: 3px solid #667eea;
  border-radius: 0 8px 8px 0;
  padding: 10px 14px;
}

.rl-facts {
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}

.rl-facts li {
  display: flex;
  gap: 10px;
  align-items: baseline;
  background: #fafafa;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
}

.rl-fact-k {
  color: #888;
  flex: none;
  min-width: 36px;
}

.rl-fact-v {
  color: #333;
  font-weight: 500;
}

.rl-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  margin: 8px 0;
}

.rl-table th,
.rl-table td {
  text-align: left;
  padding: 7px 10px;
  border-bottom: 1px solid #eee;
  color: #444;
}

.rl-table th {
  background: #fafafa;
  color: #666;
  font-weight: 600;
  font-size: 12px;
}

.rl-table .rl-num {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #333;
}

.rl-table .rl-total td {
  border-top: 2px solid #eee;
  border-bottom: none;
  font-weight: 600;
  color: #333;
}

.rl-steps {
  margin: 6px 0;
  padding-left: 22px;
}

.rl-steps li {
  font-size: 14px;
  line-height: 1.8;
  color: #444;
  margin-bottom: 6px;
}

.rl-list {
  margin: 6px 0;
  padding-left: 20px;
}

.rl-list li {
  font-size: 14px;
  line-height: 1.8;
  color: #444;
  margin-bottom: 6px;
}

.rl-note {
  font-size: 13px !important;
  color: #8a6d3b !important;
  background: #fcf8e3;
  border-radius: 6px;
  padding: 7px 11px;
  display: block;
}

.rl-warn {
  background: #fff5f5;
  border: 1px solid #ffd6d6;
  border-radius: 10px;
  padding: 12px 16px;
  margin: 14px 0 0;
}

.rl-warn b {
  color: #c62828;
  font-size: 14px;
}

.rl-warn p {
  font-size: 13.5px;
  color: #6b4b4b;
  margin: 6px 0 0;
}

.rl-turnflow {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
  margin: 12px 0;
}

.rl-flow-step {
  display: flex;
  gap: 10px;
  background: #f7f7fc;
  border-radius: 10px;
  padding: 12px 14px;
}

.rl-flow-step b {
  color: #667eea;
  font-size: 15px;
}

.rl-flow-step p {
  font-size: 13px;
  margin: 4px 0 0;
  color: #555;
  line-height: 1.7;
}

.rl-flow-no {
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: #667eea;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.rl-phase {
  border-left: 3px solid #e0e0f0;
  padding: 2px 0 2px 16px;
  margin: 16px 0;
}

.rl-phase h3 {
  margin: 4px 0 6px;
  color: #333;
  font-size: 15px;
}

.rl-phase-tag {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  color: #667eea;
  background: #eef0ff;
  border-radius: 5px;
  padding: 2px 8px;
}

.rl-phase .rl-steps {
  margin-top: 4px;
}

/* 交互演示 */
.rl-demo {
  background: #f7f7fc;
  border: 1px dashed #c5cae9;
  border-radius: 10px;
  padding: 14px 16px;
  margin-top: 16px;
}

.rl-demo h3 {
  margin: 0 0 6px;
}

.rl-demo-players {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 12px 0;
}

.rl-demo-player {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: 8px;
  padding: 7px 12px;
  flex-wrap: wrap;
}

.rl-demo-name {
  font-size: 13.5px;
  font-weight: 600;
  color: #333;
  min-width: 52px;
}

.rl-demo-ctrl {
  display: flex;
  align-items: center;
  gap: 2px;
}

.rl-stepper {
  width: 26px;
  height: 26px;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 6px;
  cursor: pointer;
  font-size: 15px;
  line-height: 1;
  color: #555;
  padding: 0;
}

.rl-stepper:hover:not(:disabled) {
  background: #667eea;
  color: #fff;
  border-color: #667eea;
}

.rl-stepper:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.rl-demo-count {
  min-width: 26px;
  text-align: center;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  color: #333;
  font-size: 14px;
}

.rl-tag {
  font-size: 11.5px;
  border-radius: 5px;
  padding: 2px 8px;
  margin-left: auto;
  white-space: nowrap;
}

.rl-tag.void {
  background: #ffebee;
  color: #c62828;
  font-weight: 600;
}

.rl-tag.get {
  background: #e8f5e9;
  color: #2e7d32;
  font-weight: 600;
}

.rl-tag.none {
  background: #fff8e1;
  color: #ef6c00;
}

.rl-tag.idle {
  background: #f0f0f0;
  color: #999;
}

.rl-demo-summary {
  font-size: 13px !important;
  color: #555 !important;
  margin: 4px 0 0 !important;
}

.rl-muted {
  background: #fbfbfb;
}

.rl-muted h2 {
  color: #777;
}

.rl-muted .rl-list li {
  color: #777;
}

.rl-source {
  font-size: 12px;
  color: #aaa;
  text-align: center;
  margin: 4px 0 0;
}

@media (max-width: 640px) {
  .rl-card {
    padding: 14px 15px;
  }

  .rl-facts {
    grid-template-columns: 1fr;
  }

  .rl-tag {
    margin-left: 0;
  }
}
</style>
