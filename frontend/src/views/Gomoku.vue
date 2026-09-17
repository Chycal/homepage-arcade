<template>
  <div class="gomoku-page">
    <!-- 顶部标题栏 -->
    <div class="header">
      <router-link to="/" class="back-btn">← 返回</router-link>
      <h1>♟ 五子棋</h1>
      <span class="online-badge">{{ onlineCount }}人在线</span>
    </div>

    <!-- 座位区 -->
    <div class="seat-area">
      <div class="seat black-seat" :class="{ occupied: state.black, isYou: state.yourSeat === 'black', isAi: state.aiBlack }">
        <div class="seat-label">⚫ 黑方<span v-if="state.aiBlack" class="ai-tag">🤖</span></div>
        <div class="seat-player">{{ state.aiBlack ? '🤖 AI' : (state.black || '空位') }}</div>
        <button v-if="canSitOn('black')" class="sit-btn" @click="sitSeat('black')">坐下</button>
        <button v-if="canDeployAiOn('black')" class="ai-deploy-btn" @click="deployAi('black')">🤖 部署AI</button>
        <button v-if="canDismissAiOn('black')" class="dismiss-btn" @click="dismissAi('black')">🔌 移除AI</button>
      </div>
      <div class="vs-text">VS</div>
      <div class="seat white-seat" :class="{ occupied: state.white, isYou: state.yourSeat === 'white', isAi: state.aiWhite }">
        <div class="seat-label">⚪ 白方<span v-if="state.aiWhite" class="ai-tag">🤖</span></div>
        <div class="seat-player">{{ state.aiWhite ? '🤖 AI' : (state.white || '空位') }}</div>
        <button v-if="canSitOn('white')" class="sit-btn" @click="sitSeat('white')">坐下</button>
        <button v-if="canDeployAiOn('white')" class="ai-deploy-btn" @click="deployAi('white')">🤖 部署AI</button>
        <button v-if="canDismissAiOn('white')" class="dismiss-btn" @click="dismissAi('white')">🔌 移除AI</button>
      </div>
    </div>

    <!-- 棋盘 -->
    <div class="board-wrapper">
      <canvas ref="canvas"
              :style="{ width: boardDisplaySize + 'px', height: boardDisplaySize + 'px' }"
              @click="handleCanvasClick"
              @touchstart.prevent="handleCanvasTouch"></canvas>
    </div>

    <!-- 状态提示 -->
    <div class="status-bar">
      <template v-if="state.status === 'waiting'">
        <span class="status-text">⏳ 等待玩家加入...</span>
      </template>
      <template v-else-if="state.status === 'playing'">
        <span class="status-text" :class="turnClass">
          {{ isMyTurn ? '👇 轮到你落子' : '👆 等待对手落子...' }}
        </span>
        <span class="turn-indicator">{{ currentTurn === 'black' ? '⚫' : '⚪' }} {{ currentTurn === 'black' ? '黑方' : '白方' }}回合</span>
      </template>
      <template v-else-if="state.status === 'over'">
        <span class="status-text over">🏆 对局结束</span>
      </template>
      <span v-if="pendingInfo" class="pending-hint">📩 {{ pendingInfo }}</span>
    </div>

    <!-- 操作按钮 -->
    <div class="actions" v-if="isPlayer && state.status === 'playing'">
      <button class="act-btn" @click="send({type:'undo_req'})" :disabled="state.moveCount < 2 || !!state.pendingRequest">↩ 悔棋</button>
      <button class="act-btn" @click="send({type:'draw_req'})" :disabled="!!state.pendingRequest">🤝 求和</button>
      <button class="act-btn act-danger" @click="confirmResign">🏳 认输</button>
      <button class="act-btn" @click="send({type:'restart_req'})" :disabled="!!state.pendingRequest">🔄 重开</button>
    </div>

    <!-- 请求确认按钮 -->
    <div class="actions" v-if="needApprove">
      <button class="act-btn act-approve" @click="approveRequest(true)">✅ 同意</button>
      <button class="act-btn act-reject" @click="approveRequest(false)">❌ 拒绝</button>
    </div>

    <!-- 观战列表 -->
    <div class="spectators" v-if="state.spectatorCount > 0">
      <span class="spec-label">👁 观战 ({{ state.spectatorCount }})：</span>
      <span class="spec-names">{{ state.spectators.join('、') }}</span>
    </div>

    <!-- 评论区 -->
    <div class="chat-area">
      <div class="chat-header">💬 评论</div>
      <div class="chat-messages" ref="chatMessages">
        <div v-for="(c, i) in comments" :key="i" class="chat-msg">
          <span class="chat-user">{{ c.user }}</span>
          <span class="chat-text">{{ c.text }}</span>
        </div>
        <div v-if="comments.length === 0" class="chat-empty">暂无评论</div>
      </div>
      <div class="chat-input-row">
        <input
          v-model="newComment"
          class="chat-input"
          placeholder="输入评论..."
          maxlength="200"
          @keyup.enter="sendComment"
        />
        <button class="chat-send-btn" @click="sendComment" :disabled="!newComment.trim()">发送</button>
      </div>
    </div>

    <!-- 已入座但等待中：可离座 -->
    <div class="actions" v-if="isPlayer && state.status === 'waiting'">
      <button class="act-btn" @click="handleLeave">🚪 离开座位</button>
    </div>

    <!-- 观战者 -->
    <div class="actions" v-if="!isPlayer">
      <span class="spec-hint" v-if="state.yourSeat === 'spectator' && (!state.black || !state.white || state.aiBlack || state.aiWhite)">👆 点击上方座位入座</span>
    </div>

    <!-- 胜利弹窗 -->
    <div class="modal-overlay" v-if="showResult" @click="dismissResult">
      <div class="modal-box" @click.stop>
        <div class="modal-icon">{{ resultEmoji }}</div>
        <h2>{{ resultText }}</h2>
        <p>双方已离开座位，等待下一局...</p>
        <button class="modal-btn" @click="dismissResult">知道了</button>
      </div>
    </div>
  </div>
</template>

<script>
import { isLoggedIn, getUsername } from '@/utils/auth'

const SIZE = 15
const CELL = 22      // 逻辑像素（交点间距）
const MARGIN = 22    // 棋盘边距
const PIECE_R = 9    // 棋子半径

export default {
  name: 'GomokuView',
  data() {
    return {
      ws: null,
      userId: '',
      connected: false,

      state: {
        black: null,
        white: null,
        yourSeat: 'spectator',
        spectators: [],
        spectatorCount: 0,
        status: 'waiting',
        currentTurn: null,
        board: [],
        lastMove: null,
        winner: null,
        moveCount: 0,
        pendingRequest: null
      },

      comments: [],
      newComment: '',
      showResult: false,
      resultWinner: null,  // 'black' | 'white' | 'draw'

      // 用于 canvas 的物理大小（按设备像素比缩放）
      boardDisplaySize: 352,
      canvasScale: 1
    }
  },
  computed: {
    onlineCount() { return (this.state.black ? 1 : 0) + (this.state.white ? 1 : 0) + this.state.spectatorCount },
    isPlayer() { return this.state.yourSeat === 'black' || this.state.yourSeat === 'white' },
    isMyTurn() {
      if (!this.isPlayer || this.state.status !== 'playing') return false
      return this.state.currentTurn === this.state.yourSeat
    },
    currentTurn() { return this.state.currentTurn },
    turnClass() { return this.isMyTurn ? 'my-turn' : '' },
    needApprove() {
      if (!this.state.pendingRequest || !this.isPlayer) return false
      return this.state.pendingRequest.fromSeat !== this.state.yourSeat
    },
    pendingInfo() {
      if (!this.state.pendingRequest) return null
      const req = this.state.pendingRequest
      const typeMap = { undo: '悔棋', restart: '重开', draw: '求和' }
      if (req.fromSeat === this.state.yourSeat) return `等待对方同意${typeMap[req.type] || req.type}...`
      return `${req.from} 请求${typeMap[req.type] || req.type}`
    },
    resultText() {
      if (this.resultWinner === 'draw') return '🤝 平局！'
      return this.resultWinner === 'black' ? '⚫ 黑方获胜！' : '⚪ 白方获胜！'
    },
    resultEmoji() {
      if (this.resultWinner === 'draw') return '🤝'
      return this.resultWinner === 'black' ? '⚫' : '⚪'
    }
  },
  mounted() {
    this.userId = this.getUserId()
    this.calcBoardSize()
    this.connect()
    window.addEventListener('resize', this.calcBoardSize)
    window.addEventListener('beforeunload', this.cleanup)
  },
  beforeUnmount() {
    this.cleanup()
    window.removeEventListener('resize', this.calcBoardSize)
    window.removeEventListener('beforeunload', this.cleanup)
  },
  methods: {
    getUserId() {
      // 已登录用户使用真实用户名
      if (isLoggedIn()) {
        return getUsername()
      }
      // 未登录用户使用随机匿名 ID
      let uid = sessionStorage.getItem('gomoku_uid')
      if (!uid) {
        uid = 'u' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
        sessionStorage.setItem('gomoku_uid', uid)
      }
      return uid
    },

    calcBoardSize() {
      const maxWidth = Math.min(window.innerWidth - 24, 400)
      const size = Math.min(maxWidth, 352)
      this.boardDisplaySize = size
      this.canvasScale = window.devicePixelRatio || 1
      this.$nextTick(() => this.drawBoard())
    },

    connect() {
      this.userId = this.getUserId()
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = `${proto}//${location.host}/ws/game`
      this.ws = new WebSocket(url)

      this.ws.onopen = () => {
        this.connected = true
        this.send({ type: 'join', userId: this.userId })
      }

      this.ws.onmessage = (e) => {
        try {
          const msg = JSON.parse(e.data)
          this.handleMessage(msg)
        } catch (err) {
          console.warn('消息解析失败', err)
        }
      }

      this.ws.onclose = () => {
        this.connected = false
        // 5秒后重连
        setTimeout(() => {
          if (!this.connected) this.connect()
        }, 5000)
      }

      this.ws.onerror = () => {}
    },

    send(obj) {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(obj))
      }
    },

    handleMessage(msg) {
      switch (msg.type) {
        case 'room_state':
          this.state = msg
          this.comments = msg.comments || []
          this.$nextTick(() => {
            this.drawBoard()
            this.scrollChatToBottom()
          })
          break
        case 'game_over':
          this.showResult = true
          this.resultWinner = msg.winner === 1 ? 'black' : msg.winner === 2 ? 'white' : 'draw'
          break
        case 'error':
          alert(msg.message)
          break
      }
    },

    sitSeat(color) {
      this.send({ type: 'sit', color })
    },

    // 观战者能否坐该位置（空位或AI占位可替换）
    canSitOn(color) {
      if (this.state.yourSeat !== 'spectator') return false
      const occ = color === 'black' ? this.state.black : this.state.white
      const isAi = color === 'black' ? this.state.aiBlack : this.state.aiWhite
      return !occ || isAi
    },

    // 对家在座时可部署 AI（目标为空位）
    canDeployAiOn(color) {
      const mySide = color === 'black' ? 'white' : 'black'
      if (this.state.yourSeat !== mySide) return false
      const occ = color === 'black' ? this.state.black : this.state.white
      return !occ
    },

    // 对家在座时可移除 AI（目标已有 AI）
    canDismissAiOn(color) {
      const mySide = color === 'black' ? 'white' : 'black'
      if (this.state.yourSeat !== mySide) return false
      return color === 'black' ? this.state.aiBlack : this.state.aiWhite
    },

    deployAi(color) {
      this.send({ type: 'deploy_ai', color })
    },

    dismissAi(color) {
      this.send({ type: 'dismiss_ai', color })
    },

    sendComment() {
      const text = this.newComment.trim()
      if (!text || !this.connected) return
      this.send({ type: 'chat', text })
      this.newComment = ''
    },

    scrollChatToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.chatMessages
        if (el) el.scrollTop = el.scrollHeight
      })
    },

    handleLeave() {
      this.send({ type: 'leave' })
    },

    confirmResign() {
      if (confirm('确定要认输吗？')) {
        this.send({ type: 'resign' })
      }
    },

    approveRequest(agree) {
      if (!this.state.pendingRequest) return
      const type = this.state.pendingRequest.type
      this.send({ type: `${type}_resp`, agree })
    },

    dismissResult() {
      this.showResult = false
    },

    // ===== 棋盘绘制 =====

    canvasPosToBoard(clientX, clientY) {
      const canvas = this.$refs.canvas
      if (!canvas) return null
      const rect = canvas.getBoundingClientRect()
      const scaleX = (CELL * (SIZE - 1) + MARGIN * 2) / rect.width
      const scaleY = (CELL * (SIZE - 1) + MARGIN * 2) / rect.height
      const x = (clientX - rect.left) * scaleX
      const y = (clientY - rect.top) * scaleY
      const col = Math.round((x - MARGIN) / CELL)
      const row = Math.round((y - MARGIN) / CELL)
      if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return null
      return { row, col }
    },

    handleCanvasClick(e) {
      if (!this.isMyTurn) return
      const pos = this.canvasPosToBoard(e.clientX, e.clientY)
      if (pos == null) return
      // 检查该位置是否已有棋子
      if (this.state.board[pos.row] && this.state.board[pos.row][pos.col] !== 0) return
      this.send({ type: 'move', row: pos.row, col: pos.col })
    },

    handleCanvasTouch(e) {
      if (!this.isMyTurn) return
      const touch = e.touches[0]
      if (!touch) return
      const pos = this.canvasPosToBoard(touch.clientX, touch.clientY)
      if (pos == null) return
      if (this.state.board[pos.row] && this.state.board[pos.row][pos.col] !== 0) return
      this.send({ type: 'move', row: pos.row, col: pos.col })
    },

    drawBoard() {
      const canvas = this.$refs.canvas
      if (!canvas) return

      const logicalSize = CELL * (SIZE - 1) + MARGIN * 2
      const dpr = this.canvasScale
      canvas.width = logicalSize * dpr
      canvas.height = logicalSize * dpr

      const ctx = canvas.getContext('2d')
      ctx.scale(dpr, dpr)

      // 背景
      ctx.fillStyle = '#DEB887'
      ctx.fillRect(0, 0, logicalSize, logicalSize)

      // 网格线
      ctx.strokeStyle = '#333'
      ctx.lineWidth = 0.8
      for (let i = 0; i < SIZE; i++) {
        const pos = MARGIN + i * CELL
        ctx.beginPath()
        ctx.moveTo(MARGIN, pos)
        ctx.lineTo(MARGIN + (SIZE - 1) * CELL, pos)
        ctx.stroke()
        ctx.beginPath()
        ctx.moveTo(pos, MARGIN)
        ctx.lineTo(pos, MARGIN + (SIZE - 1) * CELL)
        ctx.stroke()
      }

      // 星位点
      const stars = [[3,3],[3,7],[3,11],[7,3],[7,7],[7,11],[11,3],[11,7],[11,11]]
      ctx.fillStyle = '#333'
      for (const [r, c] of stars) {
        ctx.beginPath()
        ctx.arc(MARGIN + c * CELL, MARGIN + r * CELL, 2.5, 0, Math.PI * 2)
        ctx.fill()
      }

      // 坐标标注
      ctx.font = '9px sans-serif'
      ctx.fillStyle = '#555'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      for (let i = 0; i < SIZE; i++) {
        ctx.fillText(String.fromCharCode(65 + i), MARGIN + i * CELL, MARGIN - 12)
        ctx.fillText(String(i + 1), MARGIN - 14, MARGIN + i * CELL)
      }

      // 棋子
      const board = this.state.board
      if (!board || board.length === 0) return

      for (let r = 0; r < SIZE; r++) {
        for (let c = 0; c < SIZE; c++) {
          const val = board[r] && board[r][c]
          if (val === 0) continue

          const cx = MARGIN + c * CELL
          const cy = MARGIN + r * CELL

          // 阴影
          ctx.beginPath()
          ctx.arc(cx + 1, cy + 1, PIECE_R, 0, Math.PI * 2)
          ctx.fillStyle = 'rgba(0,0,0,0.2)'
          ctx.fill()

          // 棋子本体
          const grad = ctx.createRadialGradient(cx - 2, cy - 2, 1, cx, cy, PIECE_R)
          if (val === 1) {
            grad.addColorStop(0, '#555')
            grad.addColorStop(1, '#111')
          } else {
            grad.addColorStop(0, '#fff')
            grad.addColorStop(1, '#ccc')
          }
          ctx.beginPath()
          ctx.arc(cx, cy, PIECE_R, 0, Math.PI * 2)
          ctx.fillStyle = grad
          ctx.fill()

          // 最后一步标记
          const lm = this.state.lastMove
          if (lm && lm.row === r && lm.col === c) {
            ctx.beginPath()
            ctx.arc(cx, cy, 3, 0, Math.PI * 2)
            ctx.fillStyle = val === 1 ? '#ff4444' : '#ff4444'
            ctx.fill()
          }
        }
      }
    },

    cleanup() {
      if (this.ws) {
        this.send({ type: 'leave' })
        this.ws.close()
        this.ws = null
      }
    }
  }
}
</script>

<style scoped>
.gomoku-page {
  max-width: 420px;
  margin: 0 auto;
  padding: 0 0 24px;
  min-height: 100vh;
  background: #f7f5f0;
}

/* 顶部 */
.header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  gap: 12px;
}
.header h1 {
  font-size: 18px;
  margin: 0;
  flex: 1;
}
.back-btn {
  color: rgba(255,255,255,0.85);
  text-decoration: none;
  font-size: 14px;
}
.online-badge {
  font-size: 12px;
  background: rgba(255,255,255,0.2);
  padding: 3px 10px;
  border-radius: 10px;
}

/* 座位 */
.seat-area {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 14px 16px;
}
.seat {
  flex: 1;
  max-width: 140px;
  text-align: center;
  padding: 10px 8px;
  border-radius: 10px;
  border: 2px dashed #ccc;
  background: #fff;
  transition: all 0.3s;
}
.seat.occupied {
  border-color: #667eea;
  border-style: solid;
  background: #f0f0ff;
}
.seat.isYou {
  border-color: #4CAF50;
  background: #e8f5e9;
  box-shadow: 0 0 8px rgba(76,175,80,0.3);
}
.seat.isAi {
  border-color: #FF9800;
  border-style: solid;
  background: #fff8e1;
}
.seat-label {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}
.ai-tag {
  font-size: 12px;
  margin-left: 2px;
}
.seat-player {
  font-size: 12px;
  color: #666;
  word-break: break-all;
}
.sit-btn {
  margin-top: 6px;
  padding: 4px 16px;
  border-radius: 14px;
  border: 1px solid #667eea;
  background: #667eea;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
}
.ai-deploy-btn {
  margin-top: 6px;
  padding: 4px 14px;
  border-radius: 14px;
  border: 1px solid #FF9800;
  background: #FFF3E0;
  color: #E65100;
  font-size: 12px;
  cursor: pointer;
}
.dismiss-btn {
  margin-top: 6px;
  padding: 4px 14px;
  border-radius: 14px;
  border: 1px solid #E91E63;
  background: #FCE4EC;
  color: #C62828;
  font-size: 12px;
  cursor: pointer;
}
.vs-text {
  font-size: 16px;
  font-weight: 700;
  color: #999;
}

/* 棋盘 */
.board-wrapper {
  display: flex;
  justify-content: center;
  padding: 0 12px;
}
.board-wrapper canvas {
  border-radius: 6px;
  box-shadow: 0 3px 12px rgba(0,0,0,0.12);
  cursor: pointer;
  touch-action: manipulation;
}

/* 状态 */
.status-bar {
  text-align: center;
  padding: 10px 16px 6px;
}
.status-text {
  font-size: 15px;
  font-weight: 600;
  color: #555;
  display: block;
}
.status-text.my-turn {
  color: #4CAF50;
  animation: pulse 1.5s infinite;
}
.status-text.over {
  color: #E91E63;
}
.turn-indicator {
  font-size: 12px;
  color: #999;
}
.pending-hint {
  display: inline-block;
  margin-top: 4px;
  font-size: 13px;
  color: #FF9800;
  background: #FFF3E0;
  padding: 3px 12px;
  border-radius: 10px;
}

@keyframes pulse {
  0%,100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 按钮 */
.actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 8px 16px;
  flex-wrap: wrap;
}
.act-btn {
  padding: 8px 16px;
  border-radius: 20px;
  border: 1px solid #ddd;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
  color: #333;
}
.act-btn:active {
  transform: scale(0.95);
}
.act-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.act-danger {
  color: #e53935;
  border-color: #e53935;
}
.act-approve {
  background: #4CAF50;
  color: #fff;
  border-color: #4CAF50;
}
.act-reject {
  background: #f44336;
  color: #fff;
  border-color: #f44336;
}

/* 观战 */
.spectators {
  text-align: center;
  padding: 8px 16px;
  font-size: 12px;
  color: #888;
}
.spec-label { font-weight: 500; }
.spec-names { color: #aaa; }
.spec-hint {
  font-size: 12px;
  color: #4CAF50;
  font-weight: 500;
}

/* 评论区 */
.chat-area {
  margin: 8px 16px;
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 6px rgba(0,0,0,0.06);
}
.chat-header {
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  color: #555;
  border-bottom: 1px solid #eee;
  background: #fafafa;
}
.chat-messages {
  max-height: 180px;
  overflow-y: auto;
  padding: 8px 14px;
}
.chat-msg {
  padding: 3px 0;
  font-size: 13px;
  line-height: 1.5;
}
.chat-user {
  color: #667eea;
  font-weight: 600;
  margin-right: 6px;
}
.chat-text {
  color: #333;
  word-break: break-word;
}
.chat-empty {
  text-align: center;
  color: #ccc;
  font-size: 12px;
  padding: 10px 0;
}
.chat-input-row {
  display: flex;
  border-top: 1px solid #eee;
  padding: 8px 10px;
  gap: 8px;
  background: #fafafa;
}
.chat-input {
  flex: 1;
  border: 1px solid #ddd;
  border-radius: 16px;
  padding: 6px 14px;
  font-size: 13px;
  outline: none;
  background: #fff;
}
.chat-input:focus {
  border-color: #667eea;
}
.chat-send-btn {
  padding: 6px 16px;
  border-radius: 16px;
  border: none;
  background: #667eea;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
  flex-shrink: 0;
}
.chat-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s;
}
.modal-box {
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px;
  text-align: center;
  max-width: 300px;
  width: 85%;
  box-shadow: 0 8px 32px rgba(0,0,0,0.2);
  animation: scaleIn 0.3s;
}
.modal-icon {
  font-size: 48px;
  margin-bottom: 8px;
}
.modal-box h2 {
  margin: 0 0 8px;
  font-size: 20px;
}
.modal-box p {
  color: #888;
  font-size: 13px;
  margin: 0 0 20px;
}
.modal-btn {
  padding: 10px 40px;
  border-radius: 20px;
  border: none;
  background: #667eea;
  color: #fff;
  font-size: 15px;
  cursor: pointer;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes scaleIn {
  from { transform: scale(0.8); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
