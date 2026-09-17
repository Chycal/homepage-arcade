<template>
  <div class="game24-page">
    <h1 class="title">🃏 24点</h1>

    <!-- 得分 -->
    <div class="score-bar">⭐ 得分：<strong>{{ score }}</strong></div>

    <!-- 四张牌 -->
    <div class="cards-row">
      <div
        v-for="(card, i) in cards"
        :key="i"
        class="card"
        :class="{ red: isRed(card.value) }"
      >
        <span class="card-corner top-left">{{ card.label }}</span>
        <span class="card-suit">{{ card.suit }}</span>
        <span class="card-corner bottom-right">{{ card.label }}</span>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <input
        v-model="expression"
        class="expr-input"
        placeholder="输入算式，如 (1+2+3)*4"
        @keyup.enter="check"
        autofocus
      />
      <button class="btn check-btn" @click="check" :disabled="checking">验证</button>
    </div>

    <!-- 快捷填入 -->
    <div class="quick-insert">
      快速填入：
      <button
        v-for="(card, i) in cards"
        :key="'q'+i"
        class="quick-btn"
        @click="insertNum(card.value)"
      >{{ card.value }}</button>
      <button class="quick-btn op" @click="insertOp('+')">+</button>
      <button class="quick-btn op" @click="insertOp('-')">−</button>
      <button class="quick-btn op" @click="insertOp('*')">×</button>
      <button class="quick-btn op" @click="insertOp('/')">÷</button>
      <button class="quick-btn op" @click="insertOp('(')">(</button>
      <button class="quick-btn op" @click="insertOp(')')">)</button>
      <button class="quick-btn clear" @click="expression=''">清空</button>
    </div>

    <!-- 提示消息 -->
    <div v-if="message" class="message" :class="{ ok: correct, fail: !correct && message }">
      {{ message }}
    </div>

    <!-- 行动 -->
    <div class="actions">
      <button class="btn new-btn" @click="newDeal" :disabled="checking">🔄 换一副牌</button>
    </div>

    <!-- 历史 -->
    <div v-if="history.length > 0" class="history">
      <div class="history-title">📋 最近记录</div>
      <div
        v-for="(h, i) in history"
        :key="i"
        class="history-item"
        :class="{ correct: h.correct }"
      >
        <span>{{ h.expr }}</span>
        <span class="history-result">{{ h.correct ? '✅' : '❌' + h.result }}</span>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'TwentyFour',
  data() {
    return {
      cards: [],
      expression: '',
      score: 0,
      message: '',
      correct: false,
      checking: false,
      history: []
    }
  },
  mounted() {
    this.newDeal()
  },
  methods: {
    async newDeal() {
      this.expression = ''
      this.message = ''
      this.correct = false
      try {
        const res = await fetch('/api/game24/deal')
        const data = await res.json()
        this.cards = data.cards.map(v => ({
          value: v,
          label: this.cardLabel(v),
          suit: this.randomSuit()
        }))
      } catch (e) {
        this.message = '无法连接服务器，请稍后重试'
      }
    },

    async check() {
      const expr = this.expression.trim()
      if (!expr) return
      if (this.checking) return

      this.checking = true
      try {
        const res = await fetch('/api/game24/verify', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            expression: expr,
            cards: this.cards.map(c => c.value)
          })
        })
        const data = await res.json()
        this.correct = data.correct
        this.message = data.message || ''
        if (data.correct) {
          this.score++
          this.history.unshift({ expr, correct: true, result: '' })
        } else {
          const result = data.result !== undefined ? ' = ' + data.result : ''
          this.history.unshift({ expr, correct: false, result })
        }
        if (this.history.length > 10) this.history.pop()
      } catch (e) {
        this.message = '请求失败，请稍后重试'
      } finally {
        this.checking = false
      }
    },

    insertNum(val) {
      this.expression += val
    },
    insertOp(op) {
      this.expression += op
    },

    cardLabel(v) {
      if (v === 1) return 'A'
      if (v === 11) return 'J'
      if (v === 12) return 'Q'
      if (v === 13) return 'K'
      return String(v)
    },
    randomSuit() {
      const suits = ['♠', '♥', '♦', '♣']
      return suits[Math.floor(Math.random() * 4)]
    },
    isRed(v) {
      // 让花色看起来更丰富：交替红黑
      return false
    }
  }
}
</script>

<style scoped>
.game24-page {
  max-width: 420px;
  margin: 0 auto;
  padding: 20px 16px 40px;
  text-align: center;
  font-family: 'Segoe UI', system-ui, sans-serif;
}
.title {
  margin: 0 0 8px;
  font-size: 24px;
  color: #333;
}
.score-bar {
  font-size: 20px;
  color: #555;
  margin-bottom: 16px;
}
.score-bar strong {
  color: #e65100;
}

/* 扑克牌 */
.cards-row {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.card {
  width: 72px;
  height: 100px;
  background: #fff;
  border: 2px solid #444;
  border-radius: 10px;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 2px 3px 8px rgba(0,0,0,0.12);
  transition: transform 0.2s;
}
.card:hover {
  transform: translateY(-4px);
}
.card.red {
  border-color: #d32f2f;
  color: #d32f2f;
}
.card-corner {
  position: absolute;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}
.top-left { top: 6px; left: 8px; }
.bottom-right { bottom: 6px; right: 8px; transform: rotate(180deg); }
.card-suit {
  font-size: 32px;
  user-select: none;
}

/* 输入区 */
.input-area {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}
.expr-input {
  flex: 1;
  padding: 10px 14px;
  font-size: 18px;
  border: 2px solid #ccc;
  border-radius: 10px;
  outline: none;
  text-align: center;
  letter-spacing: 2px;
}
.expr-input:focus {
  border-color: #667eea;
}
.btn {
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  cursor: pointer;
  font-weight: 600;
}
.check-btn {
  background: #4CAF50;
  color: #fff;
}
.check-btn:disabled { opacity: 0.5; }
.new-btn {
  background: #FF9800;
  color: #fff;
}
.new-btn:disabled { opacity: 0.5; }

/* 快捷填入 */
.quick-insert {
  font-size: 13px;
  color: #777;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
  justify-content: center;
}
.quick-btn {
  padding: 3px 10px;
  border-radius: 6px;
  border: 1px solid #667eea;
  background: #eef1ff;
  color: #333;
  font-size: 14px;
  cursor: pointer;
  font-weight: 600;
  transition: background 0.15s;
}
.quick-btn:hover { background: #dde3ff; }
.quick-btn.op {
  background: #f5f5f5;
  border-color: #bbb;
}
.quick-btn.op:hover { background: #e0e0e0; }
.quick-btn.clear {
  background: #fff0f0;
  border-color: #d32f2f;
  color: #d32f2f;
}

/* 消息 */
.message {
  margin: 10px 0;
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 500;
}
.message.ok {
  background: #e8f5e9;
  color: #2e7d32;
}
.message.fail {
  background: #fbe9e7;
  color: #c62828;
}

/* 历史 */
.history {
  margin-top: 20px;
  text-align: left;
}
.history-title {
  font-size: 14px;
  font-weight: 600;
  color: #555;
  margin-bottom: 8px;
}
.history-item {
  display: flex;
  justify-content: space-between;
  padding: 5px 10px;
  border-radius: 6px;
  font-size: 14px;
  color: #555;
  background: #fafafa;
  margin-bottom: 3px;
}
.history-item.correct {
  font-weight: 600;
  color: #2e7d32;
  background: #e8f5e9;
}
.history-result {
  flex-shrink: 0;
  font-size: 13px;
}
</style>
