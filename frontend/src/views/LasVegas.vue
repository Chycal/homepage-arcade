<template>
  <div class="lv-page">
    <!-- 顶部 -->
    <header class="lv-header">
      <div class="lv-title">
        <h1>🎲 拉斯维加斯</h1>
        <p class="lv-sub">Las Vegas · 2-5 人 · 共 4 轮 · 钞票最多者胜</p>
      </div>
      <div class="lv-tabs">
        <button :class="['lv-tab', { active: tab === 'game' }]" @click="tab = 'game'">
          对局
        </button>
        <button :class="['lv-tab', { active: tab === 'record' }]"
                @click="switchToRecord">
          战绩表
        </button>
        <button :class="['lv-tab', { active: tab === 'rules' }]" @click="tab = 'rules'">
          📖 规则
        </button>
      </div>
    </header>

    <div v-if="toast" class="lv-toast">{{ toast }}</div>

    <!-- ===================== 对局 ===================== -->
    <section v-if="tab === 'game'">
      <div class="lv-conn" :class="connected ? 'ok' : 'bad'">
        <span class="dot"></span>
        {{ connected ? '已连接' : '连接中断，正在重连…' }}
        <span class="lv-room">房间：{{ state.roomCode || 'default' }}</span>
        <span v-if="state.spectator" class="lv-room">
          观战中<span v-if="state.spectatorCount > 1">（{{ state.spectatorCount }} 人围观）</span>
        </span>
      </div>

      <!-- 颜色名册：每个人都能一眼看出自己和别人的颜色 -->
      <div v-if="players.length" class="lv-card lv-legend">
        <div class="lv-legend-me">
          <template v-if="state.you">
            <span class="lv-dot big" :style="{ background: state.yourColor || '#999' }"></span>
            <span>你的颜色：<b>{{ state.yourColorName || '—' }}</b>（{{ me ? me.username : '' }}）</span>
          </template>
          <template v-else>
            <span class="lv-legend-eye">👀</span>
            <span><b>你正在观战</b>，不能操作对局。</span>
          </template>
        </div>
        <div class="lv-legend-list">
          <span v-for="p in players" :key="p.key" class="lv-legend-item"
                :class="{ me: p.key === state.you }">
            <span class="lv-dot" :style="{ background: colorOfPlayer(p) }"></span>
            <span class="lv-legend-name">{{ p.username }}</span>
            <span v-if="p.key === state.you" class="lv-legend-tag">我</span>
            <span v-else-if="p.ai" class="lv-legend-tag ai">电脑</span>
            <span v-else-if="p.autopilot" class="lv-legend-tag ai">托管</span>
          </span>
        </div>
        <button v-if="state.canJoin" class="lv-btn primary small" @click="joinAsPlayer">
          加入这局
        </button>
      </div>

      <!-- 大厅 -->
      <div v-if="phase === 'LOBBY'" class="lv-card lv-lobby">
        <h2>玩家列表（{{ players.length }} / {{ state.maxPlayers || 5 }}）</h2>
        <ul class="lv-players">
          <li v-for="p in players" :key="p.key" :class="{ me: p.key === state.you, ai: p.ai }">
            <span class="lv-dot" :style="{ background: colorOfPlayer(p) }"></span>
            <span class="lv-name">{{ p.username }}</span>
            <span v-if="p.isHost" class="lv-badge">房主</span>
            <span v-if="p.ai" class="lv-badge ai-badge">🤖 {{ p.aiStyleLabel || '电脑' }}</span>
            <span v-else-if="p.autopilot" class="lv-badge ai-badge">🤖 托管中</span>
            <span v-if="p.key === state.you" class="lv-badge me-badge">我</span>
            <span v-if="!p.connected && !p.autopilot" class="lv-offline">掉线</span>
            <button
              v-if="p.ai && state.canRemoveAi"
              class="lv-remove-ai"
              title="移除这个电脑对手"
              @click="removeAi(p.key)"
            >✕</button>
          </li>
        </ul>

        <p class="lv-hint">
          至少 {{ state.minPlayers || 2 }} 人开始。规则：轮到你时掷出全部剩余骰子，
          必须选择<b>一个</b>点数，把该点数的<b>全部</b>骰子放进对应赌场。
          <router-link to="/las-vegas/rules" class="lv-inline-link">看完整规则 →</router-link>
        </p>

        <!-- 房主：添加电脑对手 -->
        <div v-if="state.canAddAi" class="lv-add-ai">
          <span class="lv-add-ai-label">添加电脑对手</span>
          <select v-model="aiStyle" class="lv-select">
            <option v-for="s in aiStyles" :key="s.value" :value="s.value">{{ s.label }}</option>
          </select>
          <button class="lv-btn ghost small" @click="addAi">＋ 添加</button>
        </div>
        <p v-else-if="state.isHost && phase === 'LOBBY'" class="lv-add-ai-full">
          房间已满（{{ state.maxPlayers || 5 }} 人），无法再添加电脑对手。
        </p>
        <p v-else-if="!state.spectator && phase === 'LOBBY' && !state.isHost" class="lv-add-ai-full">
          房主可以添加电脑对手陪你玩。
        </p>

        <div class="lv-actions">
          <template v-if="!state.spectator">
            <button class="lv-btn primary" :disabled="!state.canStart" @click="start">
              {{ state.canStart ? '开始对局' : (state.isHost ? '等待其他玩家…' : '等待房主开始') }}
            </button>
            <button class="lv-btn ghost" @click="leave">离开房间</button>
          </template>
          <template v-else>
            <button v-if="state.canJoin" class="lv-btn primary" @click="joinAsPlayer">
              加入这局
            </button>
            <span v-else class="lv-hint">房间已满，你将以观战身份继续观看。</span>
          </template>
        </div>
      </div>

      <!-- 对局中 / 已结束 -->
      <template v-else>
        <div class="lv-round">
          <span class="lv-round-num">第 {{ state.round }} / {{ state.totalRounds }} 轮</span>
          <span class="lv-turn" v-if="phase === 'PLAYING'">
            当前：<b :style="{ color: state.currentPlayerColor || '#333' }">
              {{ state.currentPlayerName }}</b>
            <span v-if="isMyTurn" class="lv-your-turn">轮到你了！</span>
          </span>
          <span class="lv-turn" v-else>对局结束</span>
          <span class="lv-deck">钞票堆剩余 {{ state.deckSize }} 张</span>
        </div>

        <!-- 当前投骰玩家投出的点数：所有人都能看到（含观战者） -->
        <div v-if="phase === 'PLAYING' && currentRoll.length" class="lv-current-roll">
          <span class="lv-current-roll-label">
            <b :style="{ color: state.currentPlayerColor || '#333' }">
              {{ state.currentPlayerName }}</b>
            {{ isMyTurn ? '你' : '他' }}掷出了：
          </span>
          <span v-for="(v, i) in currentRoll" :key="i" class="lv-roll-die"
                :style="{ background: state.currentPlayerColor || '#999' }">{{ v }}</span>
          <span class="lv-current-roll-tip">
            {{ isMyTurn ? '请选择要放入赌场的点数' : '等待选择点数…' }}
          </span>
        </div>

        <!-- 赌场 -->
        <div class="lv-casinos">
          <div v-for="c in casinos" :key="c.value" class="lv-casino">
            <div class="lv-casino-head">
              <span class="lv-casino-value">{{ c.value }}</span>
              <span class="lv-casino-name">{{ casinoName(c.value) }}</span>
            </div>
            <div class="lv-casino-total">${{ money(c.total) }}</div>
            <div class="lv-notes">
              <span v-for="(n, i) in c.banknotes" :key="i" class="lv-note">
                {{ shortMoney(n) }}
              </span>
            </div>
            <div class="lv-dice-area">
              <span v-if="!c.dice.length" class="lv-empty">暂无骰子</span>
              <span v-for="d in c.dice" :key="d.seat" class="lv-dice"
                    :class="{ mine: d.seat === mySeat }"
                    :style="{ background: d.color || diceColor(d.seat) }"
                    :title="d.username">
                {{ d.count }}
              </span>
            </div>
          </div>
        </div>

        <!-- 我的操作区（观战者只看不玩） -->
        <div v-if="phase === 'PLAYING' && !state.spectator" class="lv-card lv-me">
          <div class="lv-me-head">
            <span class="lv-me-color">
              <span class="lv-dot" :style="{ background: state.yourColor || '#999' }"></span>
              我的颜色：<b>{{ state.yourColorName || '—' }}</b>
            </span>
            <span>我的骰子：<b>{{ me ? me.diceLeft : 0 }}</b> 颗</span>
            <span>我的资金：<b>${{ money(me ? me.money : 0) }}</b>（{{ me ? me.banknotes : 0 }} 张）</span>
          </div>

          <div v-if="isMyTurn" class="lv-me-body">
            <button v-if="state.canRoll" class="lv-btn primary" @click="roll">
              🎲 掷骰（{{ me ? me.diceLeft : 0 }} 颗）
            </button>

            <template v-if="state.canChoose">
              <p class="lv-hint">
                本次掷出：{{ (state.yourRoll || []).join('、') }}。请选择要放入赌场的点数：
                <span class="lv-tip">选中的点数会把该点数的<b>全部</b>骰子一起放进去，不能只放一部分。</span>
              </p>
              <div class="lv-options">
                <button v-for="o in options" :key="o.value" class="lv-option" @click="choose(o.value)">
                  <span class="lv-option-value">{{ o.value }}</span>
                  <span class="lv-option-count">{{ o.count }} 颗</span>
                </button>
              </div>
            </template>
          </div>
          <p v-else class="lv-hint">等待 {{ state.currentPlayerName }} 行动…</p>
        </div>

        <!-- 观战者：看得到双方资金，但没有操作按钮 -->
        <div v-else-if="phase === 'PLAYING'" class="lv-card lv-me lv-watching">
          <p class="lv-hint">
            👀 你正在观战，只能观看不能操作。本局玩家资金：
          </p>
          <div class="lv-watch-players">
            <span v-for="p in players" :key="p.key" class="lv-watch-player">
              <span class="lv-dot" :style="{ background: colorOfPlayer(p) }"></span>
              <b>{{ p.username }}</b>
              <span class="lv-watch-money">${{ money(p.money) }}（{{ p.banknotes }} 张）</span>
              <span class="lv-watch-dice">剩 {{ p.diceLeft }} 颗</span>
            </span>
          </div>
        </div>

        <!-- 结算结果 -->
        <div v-if="phase === 'FINISHED'" class="lv-card lv-result">
          <h2>🏆 对局结果</h2>
          <ul class="lv-winners">
            <li v-for="w in (state.winners || [])" :key="w.key">
              <span class="lv-dot" :style="{ background: colorOfKey(w.key) }"></span>
              <b>{{ w.username }}</b>
              <span class="lv-win-money">${{ money(w.money) }}（{{ w.banknotes }} 张）</span>
            </li>
          </ul>
          <div class="lv-standings">
            <div v-for="p in players" :key="p.key" class="lv-standing-row">
              <span class="lv-dot" :style="{ background: colorOfPlayer(p) }"></span>
              <span class="lv-name">{{ p.username }}</span>
              <span v-if="p.key === state.you" class="lv-badge me-badge">我</span>
              <span v-if="p.ai" class="lv-badge ai-badge">电脑</span>
              <span v-else-if="p.autopilot" class="lv-badge ai-badge">托管</span>
              <span class="lv-standing-money">${{ money(p.money) }}</span>
              <span class="lv-standing-notes">{{ p.banknotes }} 张</span>
            </div>
          </div>
          <p v-if="state.matchId" class="lv-hint">
            本局战绩已保存（#{{ state.matchId }}），可到「战绩表」查看。
          </p>
          <div v-if="!state.spectator" class="lv-actions">
            <button class="lv-btn primary" @click="restart">再来一局</button>
          </div>
          <p v-else class="lv-hint">观战模式：等房主点「再来一局」后即可加入。</p>
        </div>

        <!-- 上一轮结算 -->
        <details v-if="(state.lastPayout || []).length" class="lv-card lv-payout">
          <summary>上一轮结算明细</summary>
          <div v-for="p in state.lastPayout" :key="p.value" class="lv-payout-row">
            <span class="lv-payout-casino">{{ p.value }} 号赌场</span>
            <span v-if="(p.voided || []).length" class="lv-voided">
              平手作废：{{ p.voided.join('、') }}
            </span>
            <span v-if="Object.keys(p.awards || {}).length" class="lv-awarded">
              得钞：{{ Object.entries(p.awards).map(([u, n]) => `${u} $${money(n)}`).join('，') }}
            </span>
            <span v-if="!(p.voided || []).length && !Object.keys(p.awards || {}).length"
                  class="lv-voided">无人得钞</span>
          </div>
        </details>
      </template>

      <!-- 聊天 -->
      <div class="lv-card lv-chat">
        <h3>
          聊天
          <span v-if="state.spectatorCount > 1" class="lv-chat-sub">
            （{{ state.spectatorCount }} 人围观）
          </span>
        </h3>
        <!-- 新消息自动跟随到底部；用户往上翻时不打断阅读 -->
        <div ref="chatList" class="lv-chat-list" @scroll="onChatScroll">
          <p v-for="(m, i) in (state.chat || [])" :key="i" :class="{ 'lv-chat-spectator': m.spectator }">
            <span v-if="m.color" class="lv-dot tiny" :style="{ background: m.color }"></span>
            <b>{{ m.username }}</b><span v-if="m.spectator" class="lv-chat-tag">观众</span>：{{ m.text }}
          </p>
          <p v-if="!(state.chat || []).length" class="lv-empty">还没有消息</p>
        </div>
        <button v-if="chatUnread" class="lv-chat-jump" @click="scrollChatToBottom">
          有新消息 ↓
        </button>
        <form class="lv-chat-form" @submit.prevent="sendChat">
          <input v-model="chatText" maxlength="200" placeholder="说点什么…" />
          <button class="lv-btn primary" type="submit">发送</button>
        </form>
      </div>
    </section>

    <!-- ===================== 战绩表 ===================== -->
    <section v-else-if="tab === 'record'" class="lv-record">
      <div v-if="!loggedIn" class="lv-card">
        <p class="lv-hint">战绩表仅对参与过对局的登录玩家可见。</p>
        <router-link to="/login" class="lv-btn primary">去登录</router-link>
      </div>

      <template v-else>
        <div class="lv-card lv-stats">
          <div class="lv-stat"><span class="lv-stat-num">{{ stats.games || 0 }}</span><span>参与对局</span></div>
          <div class="lv-stat"><span class="lv-stat-num">{{ stats.wins || 0 }}</span><span>获胜</span></div>
          <div class="lv-stat"><span class="lv-stat-num">${{ money(stats.totalMoney || 0) }}</span><span>累计奖金</span></div>
        </div>

        <div class="lv-card">
          <h2>我的对局记录</h2>
          <p v-if="loadingMatches" class="lv-hint">加载中…</p>
          <p v-else-if="!matches.length" class="lv-hint">
            还没有战绩。回到「对局」标签打一局吧！
          </p>
          <table v-else class="lv-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>人数</th>
                <th>我的名次</th>
                <th>我的奖金</th>
                <th>钞票</th>
                <th>结果</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="m in matches" :key="m.matchId">
                <tr class="lv-row" @click="toggleExpand(m.matchId)">
                  <td>{{ formatTime(m.startedAt) }}</td>
                  <td>{{ m.playerCount }} 人</td>
                  <td>第 {{ m.myRank }} 名</td>
                  <td>${{ money(m.myMoney) }}</td>
                  <td>{{ m.myBanknotes }} 张</td>
                  <td>
                    <span :class="['lv-result-tag', m.iWon ? 'win' : 'lose']">
                      {{ m.iWon ? '🏆 获胜' : '未胜' }}
                    </span>
                  </td>
                </tr>
                <tr v-if="expanded.includes(m.matchId)" class="lv-detail-row">
                  <td colspan="6">
                    <div class="lv-detail">
                      <div v-for="(p, idx) in m.standings" :key="p.seat" class="lv-detail-item">
                        <span class="lv-rank">{{ idx + 1 }}</span>
                        <span class="lv-name">{{ p.username }}</span>
                        <span class="lv-detail-money">${{ money(p.money) }}</span>
                        <span class="lv-detail-notes">{{ p.banknotes }} 张</span>
                        <span v-if="p.winner" class="lv-result-tag win">胜</span>
                      </div>
                    </div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
      </template>
    </section>

    <!-- ===================== 规则 ===================== -->
    <section v-else class="lv-rules">
      <LasVegasRules />
      <p class="lv-rules-link">
        想单独打开这一页分享给别人？<router-link to="/las-vegas/rules">规则页链接</router-link>
      </p>
    </section>
  </div>
</template>

<script>
import { isLoggedIn, getAccessToken, getUsername, authFetch } from '@/utils/auth'
import LasVegasRules from '@/components/LasVegasRules.vue'

const DICE_COLORS = ['#e53935', '#1e88e5', '#43a047', '#fb8c00', '#8e24aa']
const CASINO_NAMES = {
  1: 'Golden Nugget',
  2: "Caesar's Palace",
  3: 'The Mirage',
  4: 'Sahara',
  5: 'Luxor',
  6: 'Circus Circus'
}

export default {
  name: 'LasVegas',
  components: { LasVegasRules },
  data() {
    return {
      ws: null,
      connected: false,
      state: {},
      toast: '',
      toastTimer: null,
      tab: 'game',
      chatText: '',
      guestId: '',
      loggedIn: false,
      matches: [],
      stats: {},
      loadingMatches: false,
      expanded: [],
      reconnectTimer: null,
      aiStyle: 'SOLID',
      chatUnread: false,
      chatAtBottom: true,
      // 主动离开后，重连时自动以观战身份进来，不会又悄悄占回座位
      leftRoom: false
    }
  },
  computed: {
    phase() {
      return this.state.phase || 'LOBBY'
    },
    players() {
      return this.state.players || []
    },
    aiStyles() {
      // 后端下发可选项；未收到时给一份兜底，保证大厅不出现空下拉框
      return this.state.aiStyles || [
        { value: 'SOLID', label: '稳健' },
        { value: 'BOLD', label: '激进' },
        { value: 'WILD', label: '赌徒' }
      ]
    },
    casinos() {
      return this.state.casinos || []
    },
    me() {
      return this.players.find(p => p.key === this.state.you) || null
    },
    mySeat() {
      return this.me ? this.me.seat : -1
    },
    isMyTurn() {
      return !!(this.state.canRoll || this.state.canChoose)
    },
    // 当前投骰玩家投出的点数，所有人（含观战者）都能看到
    currentRoll() {
      return this.state.currentRoll || []
    },
    options() {
      return Object.entries(this.state.options || {})
        .map(([value, count]) => ({ value: Number(value), count }))
        .sort((a, b) => a.value - b.value)
    }
  },
  mounted() {
    this.loggedIn = isLoggedIn()
    let gid = sessionStorage.getItem('lv_guest_id')
    if (!gid) {
      gid = 'g' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
      sessionStorage.setItem('lv_guest_id', gid)
    }
    this.guestId = gid
    this.connect()
    window.addEventListener('beforeunload', this.cleanup)
  },
  beforeUnmount() {
    this.cleanup()
    window.removeEventListener('beforeunload', this.cleanup)
  },
  methods: {
    casinoName(value) {
      return CASINO_NAMES[value] || ''
    },
    diceColor(seat) {
      return DICE_COLORS[seat % DICE_COLORS.length]
    },
    // 优先用后端下发的颜色（加入时固定），没有时按座位兜底
    colorOfPlayer(p) {
      if (p && p.color) return p.color
      return this.diceColor(p ? p.seat : 0)
    },
    colorOfKey(key) {
      const p = this.players.find(x => x.key === key)
      return p ? this.colorOfPlayer(p) : '#999'
    },
    seatOf(key) {
      const p = this.players.find(x => x.key === key)
      return p ? p.seat : 0
    },
    money(value) {
      return Number(value || 0).toLocaleString('en-US')
    },
    shortMoney(value) {
      return '$' + (value / 10000) + '万'
    },
    formatTime(value) {
      if (!value) return '-'
      const d = new Date(value)
      if (isNaN(d.getTime())) return String(value)
      return d.toLocaleString('zh-CN', { hour12: false })
    },
    showToast(message) {
      this.toast = message
      clearTimeout(this.toastTimer)
      this.toastTimer = setTimeout(() => {
        this.toast = ''
      }, 2600)
    },
    connect() {
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const url = `${proto}//${location.host}/ws/lasvegas`
      this.ws = new WebSocket(url)

      this.ws.onopen = () => {
        this.connected = true
        this.send({
          type: 'join',
          token: getAccessToken() || '',
          guestId: this.guestId,
          username: getUsername() || '',
          spectate: this.leftRoom
        })
      }

      this.ws.onmessage = (e) => {
        try {
          this.handleMessage(JSON.parse(e.data))
        } catch (err) {
          console.warn('拉斯维加斯消息解析失败', err)
        }
      }

      this.ws.onclose = () => {
        this.connected = false
        clearTimeout(this.reconnectTimer)
        this.reconnectTimer = setTimeout(() => {
          if (!this.connected) this.connect()
        }, 4000)
      }

      this.ws.onerror = () => {}
    },
    send(obj) {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(obj))
        return true
      }
      return false
    },
    handleMessage(msg) {
      switch (msg.type) {
        case 'room_state':
          this.state = msg
          this.afterState()
          break
        case 'error':
          this.showToast(msg.message)
          break
      }
    },
    // 每次收到新状态后，让聊天记录自动跟随到最新一条
    afterState() {
      this.$nextTick(() => {
        const el = this.$refs.chatList
        if (!el) return
        if (this.chatAtBottom) {
          el.scrollTop = el.scrollHeight
          this.chatUnread = false
        } else if ((this.state.chat || []).length) {
          this.chatUnread = true
        }
      })
    },
    // 用户手动往上翻时，停止自动滚动，避免打断阅读
    onChatScroll() {
      const el = this.$refs.chatList
      if (!el) return
      const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 24
      this.chatAtBottom = atBottom
      if (atBottom) this.chatUnread = false
    },
    scrollChatToBottom() {
      const el = this.$refs.chatList
      if (!el) return
      el.scrollTop = el.scrollHeight
      this.chatAtBottom = true
      this.chatUnread = false
    },
    roll() {
      this.send({ type: 'roll' })
    },
    choose(value) {
      this.send({ type: 'choose', value })
    },
    start() {
      this.send({ type: 'start' })
    },
    restart() {
      this.send({ type: 'restart' })
    },
    // 离开房间：后端会把自己转为观战者并发回新状态，本地也立即清掉「我」的身份，
    // 这样页面立刻反映「已不在房间里」，不必等刷新
    leave() {
      if (this.send({ type: 'leave' })) {
        this.leftRoom = true
        this.state = { ...this.state, you: null, isHost: false, canStart: false, spectator: true }
        this.showToast('已离开房间，正在以观战身份观看')
      }
    },
    // 以观战身份进来的人，在大厅且未满员时可以入座
    joinAsPlayer() {
      if (this.send({
        type: 'join',
        token: getAccessToken() || '',
        guestId: this.guestId,
        username: getUsername() || ''
      })) {
        this.leftRoom = false
      }
    },
    addAi() {
      this.send({ type: 'add_ai', style: this.aiStyle })
    },
    removeAi(key) {
      this.send({ type: 'remove_ai', key })
    },
    sendChat() {
      const text = this.chatText.trim()
      if (!text) return
      if (this.send({ type: 'chat', text })) {
        this.chatText = ''
        // 自己发言后总是跟随到底部
        this.chatAtBottom = true
        this.$nextTick(() => this.scrollChatToBottom())
      }
    },
    switchToRecord() {
      this.tab = 'record'
      this.loadMatches()
    },
    async loadMatches() {
      if (!isLoggedIn()) {
        this.loggedIn = false
        return
      }
      this.loggedIn = true
      this.loadingMatches = true
      try {
        const [matchesRes, statsRes] = await Promise.all([
          authFetch('/api/lasvegas/matches?limit=20'),
          authFetch('/api/lasvegas/stats')
        ])
        if (matchesRes.ok) {
          const data = await matchesRes.json()
          this.matches = data.matches || []
        } else {
          this.matches = []
          this.showToast('战绩加载失败')
        }
        if (statsRes.ok) {
          this.stats = await statsRes.json()
        }
      } catch (err) {
        console.warn('战绩加载失败', err)
        this.showToast('战绩加载失败')
      } finally {
        this.loadingMatches = false
      }
    },
    toggleExpand(matchId) {
      const i = this.expanded.indexOf(matchId)
      if (i >= 0) {
        this.expanded.splice(i, 1)
      } else {
        this.expanded.push(matchId)
      }
    },
    cleanup() {
      clearTimeout(this.reconnectTimer)
      clearTimeout(this.toastTimer)
      if (this.ws) {
        this.ws.onclose = null
        this.ws.close()
        this.ws = null
      }
    }
  }
}
</script>

<style scoped>
.lv-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 16px 60px;
  background: #f7f5f0;
  min-height: 100vh;
}

.lv-header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 14px;
}

.lv-title h1 {
  font-size: 22px;
  margin: 0;
}

.lv-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 4px;
}

.lv-tabs {
  display: flex;
  gap: 8px;
}

.lv-tab {
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: transparent;
  color: #fff;
  border-radius: 8px;
  padding: 6px 14px;
  cursor: pointer;
  font-size: 13px;
}

.lv-tab.active {
  background: #fff;
  color: #667eea;
  font-weight: 600;
}

.lv-conn {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  padding: 8px 12px;
  border-radius: 8px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid #ddd;
  color: #666;
}

.lv-conn .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #bbb;
}

.lv-conn.ok .dot {
  background: #4caf50;
}

.lv-conn.bad .dot {
  background: #f44336;
}

.lv-room {
  margin-left: auto;
  color: #999;
}

.lv-toast {
  position: fixed;
  top: 72px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.78);
  color: #fff;
  padding: 9px 18px;
  border-radius: 8px;
  font-size: 13px;
  z-index: 999;
}

.lv-card {
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 14px;
}

.lv-card h2 {
  font-size: 16px;
  margin: 0 0 12px;
  color: #333;
}

.lv-card h3 {
  font-size: 14px;
  margin: 0 0 8px;
  color: #333;
}

.lv-players {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
}

.lv-players li {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  font-size: 14px;
  color: #333;
}

.lv-players li.me {
  background: #f0f0ff;
}

.lv-players li.ai {
  background: #f3f7ff;
}

.lv-badge.ai-badge {
  background: #e8f0ff;
  color: #3a5bbf;
}

.lv-remove-ai {
  margin-left: auto;
  border: none;
  background: transparent;
  color: #b0b0b0;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  padding: 2px 6px;
  border-radius: 6px;
}

.lv-remove-ai:hover {
  background: #ffecec;
  color: #f44336;
}

.lv-add-ai {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 0 0 14px;
  padding: 10px 12px;
  background: #f3f7ff;
  border: 1px dashed #c9d8f7;
  border-radius: 10px;
}

.lv-add-ai-label {
  font-size: 13px;
  color: #555;
}

.lv-add-ai-full {
  font-size: 13px;
  color: #999;
  margin: 0 0 14px;
}

.lv-select {
  padding: 5px 8px;
  border: 1px solid #d5d9e6;
  border-radius: 8px;
  background: #fff;
  font-size: 13px;
  color: #333;
}

.lv-btn.small {
  padding: 5px 12px;
  font-size: 13px;
}

.lv-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  display: inline-block;
  flex: none;
}

.lv-name {
  font-weight: 500;
}

.lv-badge {
  font-size: 11px;
  background: #eee;
  color: #666;
  border-radius: 6px;
  padding: 1px 7px;
}

.lv-badge.me-badge {
  background: #667eea;
  color: #fff;
}

.lv-offline {
  font-size: 11px;
  color: #f44336;
  margin-left: auto;
}

.lv-hint {
  font-size: 13px;
  color: #777;
  line-height: 1.7;
  margin: 8px 0;
}

.lv-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 12px;
}

.lv-btn {
  border: none;
  border-radius: 8px;
  padding: 9px 18px;
  font-size: 14px;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
}

.lv-btn.primary {
  background: #667eea;
  color: #fff;
}

.lv-btn.primary:disabled {
  background: #c5cae9;
  cursor: not-allowed;
}

.lv-btn.ghost {
  background: #fff;
  color: #666;
  border: 1px solid #ddd;
}

.lv-round {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  font-size: 13px;
  color: #555;
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
}

.lv-round-num {
  font-weight: 600;
  color: #667eea;
}

.lv-your-turn {
  color: #e65100;
  background: #fff3e0;
  border-radius: 6px;
  padding: 1px 8px;
  margin-left: 6px;
  font-weight: 600;
}

.lv-deck {
  margin-left: auto;
  color: #999;
}

.lv-casinos {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.lv-casino {
  background: #fff;
  border: 1px solid #e5e5e5;
  border-radius: 10px;
  padding: 10px;
}

.lv-casino-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.lv-casino-value {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  background: #667eea;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex: none;
}

.lv-casino-name {
  font-size: 11px;
  color: #888;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lv-casino-total {
  font-size: 15px;
  font-weight: 600;
  color: #2e7d32;
  margin: 6px 0 4px;
}

.lv-notes {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
  min-height: 18px;
}

.lv-note {
  font-size: 10px;
  background: #e8f5e9;
  color: #2e7d32;
  border-radius: 4px;
  padding: 1px 4px;
}

.lv-dice-area {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  min-height: 22px;
}

.lv-dice {
  width: 22px;
  height: 22px;
  border-radius: 5px;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.lv-empty {
  font-size: 12px;
  color: #bbb;
}

.lv-me-head {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 14px;
  color: #333;
  margin-bottom: 10px;
}

.lv-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.lv-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  border: 1px solid #667eea;
  background: #fff;
  border-radius: 10px;
  padding: 8px 16px;
  cursor: pointer;
}

.lv-option-value {
  font-size: 18px;
  font-weight: 700;
  color: #667eea;
}

.lv-option-count {
  font-size: 11px;
  color: #888;
}

.lv-winners {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
}

.lv-winners li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  padding: 6px 0;
}

.lv-win-money {
  color: #2e7d32;
  margin-left: auto;
}

.lv-standings {
  border-top: 1px solid #eee;
  padding-top: 10px;
}

.lv-standing-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #555;
  padding: 5px 0;
}

.lv-standing-money {
  margin-left: auto;
  color: #2e7d32;
}

.lv-standing-notes {
  color: #999;
  width: 50px;
  text-align: right;
}

.lv-payout summary {
  cursor: pointer;
  font-size: 14px;
  color: #333;
}

.lv-payout-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  padding: 6px 0;
  border-top: 1px dashed #eee;
  margin-top: 6px;
}

.lv-payout-casino {
  font-weight: 600;
  color: #667eea;
}

.lv-voided {
  color: #f44336;
}

.lv-awarded {
  color: #2e7d32;
}

.lv-chat-list {
  max-height: 160px;
  overflow-y: auto;
  font-size: 13px;
  color: #555;
  margin-bottom: 8px;
}

.lv-chat-list p {
  margin: 3px 0;
}

/* ---------- 颜色名册 / 观战 / 当前点数 ---------- */

.lv-legend {
  padding: 12px 16px;
  margin-bottom: 12px;
}

.lv-legend-me {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.lv-legend-eye {
  font-size: 18px;
}

.lv-legend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  font-size: 13px;
  margin-bottom: 10px;
}

.lv-legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #f4f2ee;
}

.lv-legend-item.me {
  background: #eceafc;
  font-weight: 600;
}

.lv-legend-tag {
  font-size: 11px;
  color: #667eea;
  border: 1px solid #c9c3f5;
  border-radius: 6px;
  padding: 0 4px;
}

.lv-legend-tag.ai {
  color: #8e24aa;
  border-color: #e0c2ec;
}

.lv-dot.big {
  width: 16px;
  height: 16px;
}

.lv-dot.tiny {
  width: 8px;
  height: 8px;
  margin-right: 4px;
}

.lv-current-roll {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  background: #fffdf5;
  border: 1px solid #f0e2b8;
  border-radius: 12px;
  padding: 10px 14px;
  margin-bottom: 12px;
  font-size: 14px;
}

.lv-roll-die {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 26px;
  height: 26px;
  padding: 0 6px;
  border-radius: 6px;
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.2);
}

.lv-current-roll-tip {
  color: #999;
  font-size: 12px;
}

.lv-me-color {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.lv-dice.mine {
  outline: 2px solid #333;
  outline-offset: 1px;
}

.lv-watching {
  background: #fbfaff;
}

.lv-watch-players {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
  font-size: 13px;
}

.lv-watch-player {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.lv-watch-money {
  color: #2e7d32;
}

.lv-watch-dice {
  color: #888;
}

.lv-chat-sub {
  font-size: 12px;
  color: #999;
  font-weight: 400;
}

.lv-chat-tag {
  font-size: 11px;
  color: #999;
  border: 1px solid #ddd;
  border-radius: 5px;
  padding: 0 3px;
  margin: 0 2px;
}

.lv-chat-spectator {
  opacity: 0.8;
}

.lv-chat-jump {
  display: block;
  margin: 0 auto 8px;
  border: none;
  background: #667eea;
  color: #fff;
  border-radius: 999px;
  padding: 4px 14px;
  font-size: 12px;
  cursor: pointer;
}

.lv-chat-form {
  display: flex;
  gap: 8px;
}

.lv-chat-form input {
  flex: 1;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  outline: none;
}

.lv-stats {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.lv-stat {
  flex: 1;
  min-width: 100px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #888;
}

.lv-stat-num {
  font-size: 20px;
  font-weight: 700;
  color: #667eea;
}

.lv-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.lv-table th {
  text-align: left;
  color: #888;
  font-weight: 500;
  font-size: 12px;
  padding: 8px 6px;
  border-bottom: 1px solid #eee;
}

.lv-table td {
  padding: 9px 6px;
  border-bottom: 1px solid #f2f2f2;
  color: #444;
}

.lv-row {
  cursor: pointer;
}

.lv-row:hover {
  background: #fafaff;
}

.lv-result-tag {
  font-size: 11px;
  border-radius: 6px;
  padding: 2px 8px;
}

.lv-result-tag.win {
  background: #e8f5e9;
  color: #2e7d32;
}

.lv-result-tag.lose {
  background: #f5f5f5;
  color: #999;
}

.lv-detail {
  padding: 6px 0 6px 12px;
}

.lv-detail-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #666;
  padding: 3px 0;
}

.lv-rank {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #f0f0ff;
  color: #667eea;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.lv-detail-money {
  margin-left: auto;
  color: #2e7d32;
}

.lv-detail-notes {
  color: #999;
  width: 50px;
  text-align: right;
}

.lv-rules-link {
  text-align: center;
  font-size: 12px;
  color: #999;
  margin: 16px 0 0;
}

.lv-rules-link a {
  color: #667eea;
}

.lv-inline-link {
  color: #667eea;
  text-decoration: none;
  white-space: nowrap;
}

.lv-inline-link:hover {
  text-decoration: underline;
}

.lv-tip {
  display: block;
  font-size: 12px;
  color: #8a6d3b;
  background: #fcf8e3;
  border-radius: 6px;
  padding: 5px 9px;
  margin-top: 6px;
}
</style>
