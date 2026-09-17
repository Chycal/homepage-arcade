<template>
  <div class="snake-container">
    <!-- 游戏区域（画布 + 遮罩 + 移动端方向键） -->
    <div class="game-area" :style="{ width: canvasDisplaySize + 'px' }">
      <!-- 遮罩层 -->
      <div v-if="overlay" class="overlay">
        <div class="overlay-content" v-if="!gameOver">
          <div class="overlay-title">{{ paused ? '已暂停' : '🐍 贪吃蛇' }}</div>
          <button class="overlay-btn primary" @click="startGame">
            {{ paused ? '▶ 继续游戏' : '▶ 开始游戏' }}
          </button>
        </div>

        <div class="overlay-content" v-if="gameOver && !scoreSubmitted">
          <div class="overlay-title">💥 游戏结束</div>
          <div class="final-score-text">最终得分：{{ score }}</div>
          <div class="final-time-text">用时：{{ formatDuration(gameEndTime - gameStartTime) }}</div>

          <!-- 分数提交区 -->
          <div class="submit-area">
            <template v-if="isLoggedIn">
              <div class="name-display">👤 {{ loggedInUsername }}</div>
            </template>
            <template v-else>
              <input
                v-model="playerName"
                class="name-input"
                placeholder="输入你的昵称"
                maxlength="20"
                :disabled="submitting"
                @keyup.enter="submitScore"
              />
            </template>
            <div v-if="submitError" class="submit-error">{{ submitError }}</div>
            <div class="submit-actions">
              <template v-if="isLoggedIn">
                <button class="overlay-btn primary" :disabled="submitting" @click="submitScore">
                  {{ submitting ? '提交中...' : '📤 提交分数' }}
                </button>
              </template>
              <template v-else>
                <button class="overlay-btn primary" :disabled="submitting" @click="goLogin">
                  🔑 登录后提交
                </button>
              </template>
              <button class="overlay-btn" :disabled="submitting" @click="startGame">跳过</button>
            </div>
          </div>
        </div>

        <div class="overlay-content" v-if="scoreSubmitted">
          <div class="overlay-title">✅ 分数已提交</div>
          <div class="final-score-text">得分：{{ score }}</div>
          <button class="overlay-btn primary" @click="startGame">🔄 再来一局</button>
        </div>
      </div>

      <!-- 游戏画布 -->
      <canvas
        ref="canvas"
        :width="CANVAS_SIZE"
        :height="CANVAS_SIZE"
        class="game-canvas"
        :style="{ width: canvasDisplaySize + 'px', height: canvasDisplaySize + 'px' }"
      ></canvas>

      <!-- 状态信息条（移动端显示在画布下方） -->
      <div class="game-info-bar">
        <span class="info-score">{{ score }} 分</span>
        <span class="info-time">{{ formatDuration(gameRunning ? displayTime : 0) }}</span>
      </div>

      <!-- 移动端方向键 (D-Pad) -->
      <div class="dpad">
        <div class="dpad-row">
          <button
            class="dpad-btn dpad-up"
            @touchstart.prevent="setDir('UP')"
            @mousedown.prevent="setDir('UP')"
          >▲</button>
        </div>
        <div class="dpad-row dpad-mid">
          <button
            class="dpad-btn dpad-left"
            @touchstart.prevent="setDir('LEFT')"
            @mousedown.prevent="setDir('LEFT')"
          >◀</button>
          <span class="dpad-center"></span>
          <button
            class="dpad-btn dpad-right"
            @touchstart.prevent="setDir('RIGHT')"
            @mousedown.prevent="setDir('RIGHT')"
          >▶</button>
        </div>
        <div class="dpad-row">
          <button
            class="dpad-btn dpad-down"
            @touchstart.prevent="setDir('DOWN')"
            @mousedown.prevent="setDir('DOWN')"
          >▼</button>
        </div>
      </div>
    </div>

    <!-- 排行榜 -->
    <div class="leaderboard">
      <h3>🏆 排行榜</h3>
      <div v-if="leaderboard.length === 0" class="no-data">暂无数据</div>
      <ul v-else>
        <li v-for="(entry, idx) in leaderboard" :key="idx" :class="{ 'top-1': idx === 0, 'top-2': idx === 1, 'top-3': idx === 2 }">
          <span class="rank">{{ idx + 1 }}</span>
          <span class="name">{{ entry.playerName }}</span>
          <span class="pts">{{ entry.score }} 分</span>
          <span class="duration">{{ formatDuration(entry.durationSeconds * 1000) }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script>
import { isLoggedIn, getUsername } from '@/utils/auth'

const GRID_SIZE = 20
const CELL_COUNT = 20   // 20x20 网格
const BASE_SPEED = 100  // 毫秒/步
const CANVAS_SIZE = 400 // 内部绘制分辨率

export default {
  name: 'SnakeGame',
  data() {
    return {
      // 覆盖层 & 游戏状态
      overlay: true,
      paused: false,
      gameOver: false,
      gameRunning: false,

      // 蛇
      snake: [],
      direction: 'RIGHT',
      nextDirection: 'RIGHT',
      food: { x: 0, y: 0 },
      score: 0,

      // 游戏计时
      gameStartTime: 0,
      gameEndTime: 0,
      displayTime: 0,

      // 循环
      gameLoop: null,
      timerInterval: null,

      // 提交
      playerName: '',
      submitting: false,
      scoreSubmitted: false,
      submitError: '',

      // 排行榜
      leaderboard: [],

      CANVAS_SIZE,
      CELL_SIZE: CANVAS_SIZE / CELL_COUNT,

      // 响应式画布显示尺寸
      canvasDisplaySize: 400
    }
  },
  computed: {
    isLoggedIn() { return isLoggedIn() },
    loggedInUsername() { return getUsername() },
    speed() {
      return Math.max(50, BASE_SPEED - this.score * 2)
    }
  },
  created() {
    this.CELL_SIZE = this.CANVAS_SIZE / CELL_COUNT
    this.updateCanvasSize()
    this.loadLeaderboard()
  },
  mounted() {
    window.addEventListener('keydown', this.handleKeydown)
    window.addEventListener('resize', this.updateCanvasSize)
  },
  beforeUnmount() {
    window.removeEventListener('keydown', this.handleKeydown)
    window.removeEventListener('resize', this.updateCanvasSize)
    this.clearGameLoop()
    this.clearTimer()
  },
  methods: {
    // ===== 响应式画布尺寸 =====
    updateCanvasSize() {
      const maxWidth = Math.min(window.innerWidth - 32, 400)
      this.canvasDisplaySize = Math.max(200, maxWidth)
    },

    // ===== 排行榜 =====
    async loadLeaderboard() {
      try {
        const res = await fetch('/api/scores/top?limit=20')
        if (res.ok) {
          const data = await res.json()
          this.leaderboard = data.scores || []
        }
      } catch (e) {
        console.warn('加载排行榜失败:', e)
      }
    },

    // ===== D-Pad 移动端控制 =====
    setDir(dir) {
      const opp = { UP: 'DOWN', DOWN: 'UP', LEFT: 'RIGHT', RIGHT: 'LEFT' }
      if (opp[dir] !== this.direction) {
        this.nextDirection = dir
      }
    },

    // ===== 游戏控制 =====
    startGame() {
      this.clearGameLoop()
      this.clearTimer()
      this.overlay = false
      this.paused = false
      this.gameOver = false
      this.gameRunning = true
      this.scoreSubmitted = false
      this.submitError = ''
      this.submitting = false

      // 蛇初始化：头(10,10)，身(9,10)(8,10)
      this.snake = [
        { x: 10, y: 10 },
        { x: 9, y: 10 },
        { x: 8, y: 10 }
      ]
      this.direction = 'RIGHT'
      this.nextDirection = 'RIGHT'
      this.score = 0
      this.gameStartTime = Date.now()
      this.gameEndTime = 0
      this.displayTime = 0

      this.generateFood()
      this.draw()

      // 启动游戏循环
      this.gameLoop = setInterval(this.tick, this.speed)
      // 启动计时器（每秒更新显示时间）
      this.timerInterval = setInterval(() => {
        if (this.gameRunning) {
          this.displayTime = Date.now() - this.gameStartTime
        }
      }, 500)
    },

    tick() {
      if (this.paused || this.gameOver) return

      // 应用方向
      this.direction = this.nextDirection

      // 计算新头部
      const head = { ...this.snake[0] }
      switch (this.direction) {
        case 'UP':    head.y--; break
        case 'DOWN':  head.y++; break
        case 'LEFT':  head.x--; break
        case 'RIGHT': head.x++; break
      }

      // 碰撞检测：墙壁
      if (head.x < 0 || head.x >= CELL_COUNT || head.y < 0 || head.y >= CELL_COUNT) {
        this.doGameOver()
        return
      }

      // 碰撞检测：自身
      if (this.snake.some(seg => seg.x === head.x && seg.y === head.y)) {
        this.doGameOver()
        return
      }

      // 插入头部
      this.snake.unshift(head)

      // 检查是否吃到食物
      if (head.x === this.food.x && head.y === this.food.y) {
        this.score++
        this.generateFood()
        // 用新的速度重启循环
        this.clearGameLoop()
        this.gameLoop = setInterval(this.tick, this.speed)
      } else {
        this.snake.pop()
      }

      this.draw()
    },

    doGameOver() {
      this.gameOver = true
      this.gameRunning = false
      this.gameEndTime = Date.now()
      this.displayTime = this.gameEndTime - this.gameStartTime
      this.overlay = true
      this.clearGameLoop()
      this.clearTimer()
      this.draw()
    },

    goLogin() {
      this.$router.push('/login')
    },

    // ===== 食物生成（本地随机） =====
    generateFood() {
      const occupied = new Set(this.snake.map(s => `${s.x},${s.y}`))
      const free = []
      for (let x = 0; x < CELL_COUNT; x++) {
        for (let y = 0; y < CELL_COUNT; y++) {
          if (!occupied.has(`${x},${y}`)) {
            free.push({ x, y })
          }
        }
      }
      if (free.length > 0) {
        this.food = free[Math.floor(Math.random() * free.length)]
      }
    },

    // ===== 分数提交 =====
    async submitScore() {
      if (!this.isLoggedIn) {
        this.$router.push('/login')
        return
      }
      this.submitting = true
      this.submitError = ''
      try {
        const { authFetch } = await import('@/utils/auth')
        const res = await authFetch('/api/scores/submit', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            score: this.score,
            startTime: this.gameStartTime,
            endTime: this.gameEndTime
          })
        })
        if (res.ok) {
          this.scoreSubmitted = true
          this.loadLeaderboard()
        } else {
          const data = await res.json()
          this.submitError = data.error || '提交失败'
        }
      } catch (e) {
        this.submitError = '网络错误，请重试'
      }
      this.submitting = false
    },

    // ===== 键盘 =====
    handleKeydown(e) {
      const map = {
        ArrowUp: 'UP', ArrowDown: 'DOWN', ArrowLeft: 'LEFT', ArrowRight: 'RIGHT',
        w: 'UP', s: 'DOWN', a: 'LEFT', d: 'RIGHT',
        W: 'UP', S: 'DOWN', A: 'LEFT', D: 'RIGHT'
      }
      const dir = map[e.key]
      if (!dir) return
      e.preventDefault()

      this.setDir(dir)
    },

    // ===== 绘制 =====
    draw() {
      const canvas = this.$refs.canvas
      if (!canvas) return
      const ctx = canvas.getContext('2d')
      const s = this.CELL_SIZE

      // 背景
      ctx.fillStyle = '#1a1a2e'
      ctx.fillRect(0, 0, this.CANVAS_SIZE, this.CANVAS_SIZE)

      // 网格线
      ctx.strokeStyle = 'rgba(255,255,255,0.04)'
      ctx.lineWidth = 0.5
      for (let i = 0; i <= CELL_COUNT; i++) {
        ctx.beginPath(); ctx.moveTo(i * s, 0); ctx.lineTo(i * s, this.CANVAS_SIZE); ctx.stroke()
        ctx.beginPath(); ctx.moveTo(0, i * s); ctx.lineTo(this.CANVAS_SIZE, i * s); ctx.stroke()
      }

      // 食物
      ctx.fillStyle = '#ff6b6b'
      ctx.beginPath()
      ctx.arc(this.food.x * s + s / 2, this.food.y * s + s / 2, s / 2 - 2, 0, Math.PI * 2)
      ctx.fill()

      // 蛇身渐变
      this.snake.forEach((seg, i) => {
        const ratio = 1 - i / (this.snake.length + 3)
        const r = Math.floor(74 + 180 * ratio)
        const g = Math.floor(222 + 30 * ratio)
        const b = Math.floor(128 - 80 * ratio)
        ctx.fillStyle = `rgb(${r},${g},${b})`
        ctx.fillRect(seg.x * s + 1, seg.y * s + 1, s - 2, s - 2)

        // 蛇头高亮
        if (i === 0) {
          ctx.fillStyle = '#fff'
          ctx.beginPath()
          ctx.arc(seg.x * s + s / 2, seg.y * s + s / 2, s / 4, 0, Math.PI * 2)
          ctx.fill()
        }
      })
    },

    // ===== 工具 =====
    clearGameLoop() {
      if (this.gameLoop) {
        clearInterval(this.gameLoop)
        this.gameLoop = null
      }
    },
    clearTimer() {
      if (this.timerInterval) {
        clearInterval(this.timerInterval)
        this.timerInterval = null
      }
    },
    formatDuration(ms) {
      const sec = Math.floor(ms / 1000)
      const min = Math.floor(sec / 60)
      const s = sec % 60
      return min > 0 ? `${min}分${s}秒` : `${s}秒`
    }
  }
}
</script>

<style scoped>
.snake-container {
  display: flex;
  gap: 24px;
  justify-content: center;
  align-items: flex-start;
  padding: 20px;
  min-height: 440px;
}

/* ===== 游戏区域 ===== */
.game-area {
  flex-shrink: 0;
  position: relative;
}

.game-canvas {
  display: block;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0,0,0,0.3);
}

/* ===== 游戏状态信息条 ===== */
.game-info-bar {
  display: none;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  margin-top: 8px;
  background: rgba(255,255,255,0.06);
  border-radius: 10px;
  color: #ccc;
}

.info-score {
  color: #4ade80;
  font-weight: 700;
  font-size: 18px;
}

.info-time {
  color: #888;
  font-size: 14px;
}

/* ===== 覆盖层 ===== */
.overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.78);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  border-radius: 12px;
}

.overlay-content {
  text-align: center;
  color: #fff;
  padding: 0 16px;
}

.overlay-title {
  font-size: 32px;
  font-weight: 700;
  margin-bottom: 16px;
}

.final-score-text {
  font-size: 22px;
  color: #ffd700;
  margin-bottom: 6px;
}

.final-time-text {
  font-size: 15px;
  color: #aaa;
  margin-bottom: 16px;
}

.overlay-btn {
  margin: 6px;
  padding: 10px 28px;
  font-size: 16px;
  border: 2px solid rgba(255,255,255,0.3);
  border-radius: 10px;
  background: transparent;
  color: #fff;
  cursor: pointer;
  transition: all 0.3s;
}

.overlay-btn:hover {
  background: rgba(255,255,255,0.15);
}

.overlay-btn.primary {
  background: linear-gradient(135deg, #4ade80 0%, #22c55e 100%);
  border-color: transparent;
  color: #000;
  font-weight: 600;
}

.overlay-btn.primary:hover {
  opacity: 0.9;
}

.overlay-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== 提交区 ===== */
.submit-area {
  margin-top: 12px;
}

.name-input {
  padding: 8px 16px;
  font-size: 15px;
  border: 2px solid rgba(255,255,255,0.2);
  border-radius: 8px;
  background: rgba(255,255,255,0.1);
  color: #fff;
  outline: none;
  width: 200px;
  text-align: center;
  transition: border-color 0.3s;
}

.name-input:focus {
  border-color: #667eea;
}

.name-display {
  font-size: 15px;
  color: #4ade80;
  font-weight: 600;
  padding: 6px 14px;
  background: rgba(74, 222, 128, 0.12);
  border-radius: 8px;
  display: inline-block;
  margin-bottom: 8px;
}

.submit-error {
  color: #f87171;
  font-size: 13px;
  margin-top: 6px;
}

.submit-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
}

/* ===== 排行榜 ===== */
.leaderboard {
  background: rgba(255,255,255,0.05);
  border-radius: 12px;
  padding: 16px;
  min-width: 240px;
  max-width: 300px;
  max-height: 420px;
  overflow-y: auto;
  flex-shrink: 0;
}

.leaderboard h3 {
  margin: 0 0 12px;
  font-size: 18px;
  color: #ffd700;
  text-align: center;
}

.no-data {
  text-align: center;
  color: #666;
  font-size: 14px;
  padding: 20px 0;
}

.leaderboard ul {
  list-style: none;
  padding: 0;
  margin: 0;
}

.leaderboard li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 14px;
  color: #ccc;
  margin-bottom: 4px;
  background: rgba(255,255,255,0.03);
}

.leaderboard li.top-1 { background: rgba(255,215,0,0.12); }
.leaderboard li.top-2 { background: rgba(192,192,192,0.08); }
.leaderboard li.top-3 { background: rgba(205,127,50,0.08); }

.rank {
  font-weight: 700;
  color: #888;
  width: 24px;
  flex-shrink: 0;
}

.top-1 .rank { color: #ffd700; }
.top-2 .rank { color: #c0c0c0; }
.top-3 .rank { color: #cd7f32; }

.name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pts {
  color: #4ade80;
  font-weight: 600;
  flex-shrink: 0;
}

.duration {
  color: #666;
  font-size: 12px;
  flex-shrink: 0;
}

/* ===== 移动端方向键 (D-Pad) ===== */
.dpad {
  display: none; /* 默认隐藏，移动端显示 */
  flex-direction: column;
  align-items: center;
  gap: 2px;
  margin-top: 12px;
  user-select: none;
  -webkit-user-select: none;
  touch-action: manipulation;
}

.dpad-row {
  display: flex;
  align-items: center;
  gap: 2px;
}

.dpad-btn {
  width: 64px;
  height: 64px;
  border: 2px solid rgba(255,255,255,0.15);
  border-radius: 14px;
  background: rgba(255,255,255,0.08);
  color: #ccc;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background 0.15s;
  -webkit-tap-highlight-color: transparent;
}

.dpad-btn:active {
  background: rgba(74, 222, 128, 0.25);
  border-color: rgba(74, 222, 128, 0.5);
  color: #4ade80;
}

.dpad-center {
  width: 64px;
  height: 64px;
}

/* ===== 移动端响应式 ===== */
@media (max-width: 768px) {
  .snake-container {
    flex-direction: column;
    align-items: center;
    padding: 12px;
    gap: 16px;
  }

  .game-area {
    width: 100%;
    max-width: 400px;
  }

  .game-canvas {
    width: 100%;
    height: auto;
    aspect-ratio: 1;
  }

  .game-info-bar {
    display: flex;
  }

  .dpad {
    display: flex;
  }

  .leaderboard {
    min-width: unset;
    max-width: 100%;
    width: 100%;
    max-height: 300px;
  }

  .overlay {
    /* 覆盖层跟随游戏区域大小；game-area 限制宽度 */
  }

  .overlay-title {
    font-size: 24px;
  }

  .final-score-text {
    font-size: 18px;
  }
}

@media (max-width: 420px) {
  .dpad-btn {
    width: 56px;
    height: 56px;
    font-size: 20px;
  }

  .dpad-center {
    width: 56px;
    height: 56px;
  }
}
</style>
