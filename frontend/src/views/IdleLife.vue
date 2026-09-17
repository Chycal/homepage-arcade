<template>
  <div class="idle-life">
    <!-- ==================== 未登录：游客/登录引导 ==================== -->
    <div v-if="!isLoggedIn" class="guest-prompt">
      <div class="guest-card">
        <div class="guest-icon">🎮</div>
        <h2>挂机生活</h2>
        <p class="guest-desc">
          战斗、种田、交易，一个属于你的挂机世界。<br />
          选择游玩方式开始冒险：
        </p>
        <button class="btn btn-primary btn-big" :disabled="guestLoading" @click="playAsGuest">
          {{ guestLoading ? '正在创建游客账号...' : '🎭 游客账号游玩' }}
        </button>
        <button class="btn btn-outline btn-big" @click="$router.push('/login')">
          🔑 登录账号游玩
        </button>
        <p class="guest-tip">
          游客账号将自动生成用户名和密码并为你保存到服务器，<br />
          请妥善保管，下次可用它登录继续游戏。
        </p>

        <div v-if="guestInfo" class="guest-info">
          <h3>✅ 游客账号已创建</h3>
          <div class="info-row"><span>用户名</span><b>{{ guestInfo.username }}</b></div>
          <div class="info-row"><span>密码</span><b>{{ guestInfo.password }}</b></div>
          <p class="guest-tip">请保存好以上信息！正在进入游戏...</p>
        </div>
      </div>
    </div>

    <!-- ==================== 已登录：游戏主界面 ==================== -->
    <template v-else>
      <!-- 模式切换（种田/交易预留） -->
      <div class="mode-tabs">
        <div class="mode-tab active">⚔️ 战斗</div>
        <div class="mode-tab locked" title="敬请期待">🌾 种田 <span class="soon">即将开放</span></div>
        <div class="mode-tab locked" title="敬请期待">💰 交易 <span class="soon">即将开放</span></div>
      </div>

      <!-- 玩家信息卡 -->
      <div class="player-card">
        <div class="player-head">
          <div class="player-name">{{ save.username }} <span class="lv-badge">Lv.{{ save.level }}</span></div>
          <div class="gold">🪙 {{ save.gold }}</div>
        </div>
        <div class="exp-bar">
          <div class="exp-fill" :style="{ width: expPercent + '%' }"></div>
          <span class="exp-text">{{ save.exp }} / {{ save.expToNext }}</span>
        </div>
        <div class="stats-grid">
          <div class="stat"><span class="stat-label">⚔️ 攻击</span><b>{{ save.stats.attack }}</b><i>({{ save.stats.baseAttack }})</i></div>
          <div class="stat"><span class="stat-label">🛡️ 物防</span><b>{{ save.stats.physDef }}</b><i>({{ save.stats.basePhysDef }})</i></div>
          <div class="stat"><span class="stat-label">🔮 法防</span><b>{{ save.stats.magicDef }}</b><i>({{ save.stats.baseMagicDef }})</i></div>
          <div class="stat"><span class="stat-label">💨 闪避</span><b>{{ save.stats.dodge }}%</b><i>({{ save.stats.baseDodge }}%)</i></div>
          <div class="stat"><span class="stat-label">❤️ 生命</span><b>{{ save.stats.maxHp }}</b><i>({{ save.stats.baseMaxHp }})</i></div>
          <div class="stat"><span class="stat-label">📊 战绩</span><b>{{ save.battlesWon }}胜</b><i>{{ save.bossesKilled }}个首领</i></div>
        </div>
        <!-- 装备栏 -->
        <div class="equip-bar">
          <div v-for="slot in slotList" :key="slot.key" class="equip-slot" :class="{ filled: save.equipment[slot.key] }">
            <div class="slot-name">{{ slot.label }}</div>
            <template v-if="save.equipment[slot.key]">
              <div class="slot-item">{{ save.equipment[slot.key].name }}</div>
              <button class="btn-mini" @click="unequip(slot.key)">卸下</button>
            </template>
            <div v-else class="slot-empty">未装备</div>
          </div>
        </div>
      </div>

      <!-- 主区域：副本战斗 + 背包 -->
      <div class="main-layout">
        <!-- 左：副本与战斗 -->
        <div class="battle-panel">
          <h3>🏰 副本选择</h3>
          <div class="dungeon-list">
            <div v-for="d in dungeons" :key="d.id" class="dungeon-card"
                 :class="{ selected: selectedDungeon === d.id, locked: save.level < d.reqLevel }"
                 @click="selectDungeon(d.id)">
              <div class="dungeon-name">{{ d.name }}</div>
              <div class="dungeon-req">
                {{ d.reqLevel === 0 ? '无等级要求' : '建议 ' + d.reqLevel + ' 级' }}
                <span v-if="save.level < d.reqLevel" class="not-enough">（等级不足）</span>
              </div>
              <div class="dungeon-desc">{{ d.desc }}</div>
              <div class="dungeon-info">
                <span>{{ d.monsters.length }} 种小怪</span>
                <span>Boss：{{ d.boss.name }} ({{ d.boss.level }}级)</span>
              </div>
              <div class="dungeon-mobs">
                <span v-for="m in d.monsters" :key="m.key" class="mob-chip">{{ m.name }}</span>
              </div>
            </div>
          </div>

          <!-- 挂机控制 -->
          <div class="battle-control">
            <button v-if="!isBattling" class="btn btn-primary" :disabled="!selectedDungeon" @click="startBattle">
              ⚔️ 开始挂机{{ selectedDungeon ? '（' + dungeonName + '）' : '' }}
            </button>
            <button v-else class="btn btn-danger" @click="stopBattle">🛑 停止挂机</button>
            <div class="battle-status">
              <template v-if="isBattling">
                <span class="dot-battling">●</span> 正在 {{ dungeonName }} 挂机中...
                <span class="boss-cd" v-if="bossCdText">Boss 战倒计时：{{ bossCdText }}</span>
              </template>
              <template v-else>未在挂机，选择副本后开始</template>
            </div>
          </div>

          <!-- 战斗记录 -->
          <div class="battle-log-wrap">
            <div class="log-head">
              <h3>📜 战斗记录</h3>
              <button class="btn-mini" @click="clearLog">清空</button>
            </div>
            <div class="battle-log" ref="logBox">
              <div v-if="!combatLog.length" class="log-empty">还没有战斗记录，开始挂机吧~</div>
              <div v-for="(line, i) in combatLog" :key="i" class="log-line"
                   :class="{ win: line.type === 'win', lose: line.type === 'lose', boss: line.type === 'boss' }">
                {{ line.text }}
              </div>
            </div>
            <!-- 掉落物 -->
            <div v-if="lastDrops.length" class="drop-panel">
              <div class="drop-title">🎁 本次掉落</div>
              <div class="drop-list">
                <span v-for="(drop, i) in lastDrops" :key="i" class="drop-chip"
                      :class="'drop-' + drop.type.toLowerCase()">
                  {{ drop.type === 'GOLD' ? '🪙' : drop.type === 'EQUIPMENT' ? '🎽' : '🧪' }}
                  {{ drop.name }} ×{{ drop.quantity }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右：背包 -->
        <div class="inventory-panel">
          <div class="inventory-head">
            <h3>🎒 背包</h3>
            <button class="btn-mini" @click="loadInventory">刷新</button>
          </div>
          <div v-if="!inventory.items.length" class="log-empty">背包空空如也，去战斗收集掉落吧~</div>
          <div v-else class="item-list">
            <div v-for="(item, i) in inventory.items" :key="i" class="item-card" :class="'type-' + item.itemType.toLowerCase()">
              <div class="item-head">
                <b>{{ item.name }}</b>
                <span class="item-qty">×{{ item.quantity }}</span>
              </div>
              <div class="item-sub">
                <span v-if="item.itemType === 'EQUIPMENT'" class="tag equip-tag">{{ item.slot }} 装备 Lv.{{ item.level }}</span>
                <span v-if="item.itemType === 'POTION'" class="tag potion-tag">治疗药水 Lv.{{ item.level }}</span>
                <span v-if="item.itemType === 'MATERIAL'" class="tag material-tag">素材</span>
                <span v-if="item.itemType === 'EQUIPMENT'" class="item-attr">{{ itemAttrText(item) }}</span>
                <span v-if="item.itemType === 'POTION'" class="item-attr">回复 {{ item.hp }} 生命</span>
              </div>
              <div class="item-actions">
                <button v-if="item.itemType === 'EQUIPMENT'" class="btn-mini" @click="equipItem(item.itemKey)">装备</button>
                <button v-if="item.itemType === 'POTION'" class="btn-mini"
                        :class="{ active: save.potionItem === item.itemKey }"
                        @click="togglePotion(item.itemKey)">
                  {{ save.potionItem === item.itemKey ? '✅ 战斗已携带' : '设为战斗药水' }}
                </button>
              </div>
            </div>
          </div>
          <div class="potion-tip">💡 战斗中药水会在血量低于50%时自动使用，每次战斗消耗1瓶</div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { isLoggedIn, authFetch } from '@/utils/auth'

export default {
  name: 'IdleLife',
  data() {
    return {
      config: { dungeons: [], potions: [], bossIntervalSec: 600 },
      save: null,
      inventory: { items: [] },
      selectedDungeon: 0,
      guestLoading: false,
      guestInfo: null,
      combatLog: [],
      lastDrops: [],
      battleTimer: null,
      cdTimer: null,
      bossCdText: '',
      slotList: [
        { key: 'helmet', label: '头盔' },
        { key: 'chest', label: '胸甲' },
        { key: 'legs', label: '腿甲' },
        { key: 'boots', label: '靴子' }
      ]
    }
  },
  computed: {
    isLoggedIn() {
      return isLoggedIn()
    },
    isBattling() {
      return !!this.save && this.save.currentDungeon > 0
    },
    dungeonName() {
      const d = this.dungeons.find(x => x.id === this.save.currentDungeon)
      return d ? d.name : ''
    },
    dungeons() {
      return this.config.dungeons || []
    },
    expPercent() {
      if (!this.save) return 0
      const p = Math.round((this.save.exp / this.save.expToNext) * 100)
      return Math.min(100, p)
    }
  },
  watch: {
    isLoggedIn(val) {
      if (val) this.initGame()
      else this.resetGame()
    }
  },
  async mounted() {
    this.loadConfig()
    if (this.isLoggedIn) this.initGame()
    window.addEventListener('auth-change', this.onAuthChange)
  },
  beforeUnmount() {
    window.removeEventListener('auth-change', this.onAuthChange)
    this.clearTimers()
  },
  methods: {
    onAuthChange() {
      if (this.isLoggedIn) this.initGame()
      else this.resetGame()
    },
    resetGame() {
      this.save = null
      this.inventory = { items: [] }
      this.selectedDungeon = 0
      this.clearTimers()
    },
    clearTimers() {
      if (this.battleTimer) { clearInterval(this.battleTimer); this.battleTimer = null }
      if (this.cdTimer) { clearInterval(this.cdTimer); this.cdTimer = null }
    },
    async loadConfig() {
      try {
        const res = await fetch('/api/idle-life/config')
        const data = await res.json()
        if (data.success) this.config = data
      } catch (e) { /* 离线忽略 */ }
    },
    async initGame() {
      this.clearTimers()
      try {
        const [saveRes, invRes] = await Promise.all([
          authFetch('/api/idle-life/save'),
          authFetch('/api/idle-life/inventory')
        ])
        const saveData = await saveRes.json()
        const invData = await invRes.json()
        if (saveData.success) {
          this.save = saveData.save
          this.selectedDungeon = this.save.currentDungeon
          this.syncBossCd()
          // 正在挂机则启动轮询
          if (this.isBattling) this.startBattleLoop()
        }
        if (invData.success) this.inventory = invData
      } catch (e) {
        this.pushLog('加载存档失败：' + e.message, 'lose')
      }
    },
    // ==================== 游客账号 ====================
    async playAsGuest() {
      this.guestLoading = true
      try {
        const res = await fetch('/api/idle-life/guest')
        const data = await res.json()
        if (data.success) {
          this.guestInfo = {
            username: data.username,
            password: data.password
          }
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          localStorage.setItem('username', data.username)
          localStorage.setItem('role', data.role)
          window.dispatchEvent(new CustomEvent('auth-change'))
          setTimeout(() => this.initGame(), 600)
        } else {
          alert(data.message || '游客账号创建失败')
        }
      } catch (e) {
        alert('网络错误，请稍后再试')
      } finally {
        this.guestLoading = false
      }
    },
    // ==================== 副本与挂机 ====================
    selectDungeon(id) {
      if (this.isBattling) return
      this.selectedDungeon = id
    },
    async startBattle() {
      if (!this.selectedDungeon) return
      const res = await authFetch('/api/idle-life/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ dungeonId: this.selectedDungeon })
      })
      const data = await res.json()
      if (data.success) {
        this.save = data.save
        this.pushLog('⚔️ 进入 ' + this.dungeonName + ' 开始挂机', 'win')
        this.startBattleLoop()
      } else {
        alert(data.message || '操作失败')
      }
    },
    async stopBattle() {
      const res = await authFetch('/api/idle-life/stop', { method: 'POST' })
      const data = await res.json()
      if (data.success) {
        this.save = data.save
        this.pushLog('🛑 已停止挂机', 'lose')
        this.clearTimers()
      } else {
        alert(data.message || '操作失败')
      }
    },
    startBattleLoop() {
      this.clearTimers()
      // 每 2.5 秒进行一次战斗（最低限度同步）
      this.battleTimer = setInterval(() => this.doBattle(), 2500)
      this.doBattle()
    },
    async doBattle() {
      if (!this.isBattling) return
      try {
        const res = await authFetch('/api/idle-life/battle', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ dungeonId: this.save.currentDungeon })
        })
        const data = await res.json()
        if (!data.success) {
          if (data.message === '未在挂机中，请先选择副本开始挂机' || data.defeated) {
            this.pushLog(data.message, 'lose')
            this.clearTimers()
          }
          return
        }
        this.save = data.save
        this.lastDrops = data.drops || []
        this.renderBattle(data)
        // 小怪战死亡退出挂机
        if (data.defeated) this.clearTimers()
      } catch (e) { /* 网络抖动忽略，下一轮重试 */ }
    },
    renderBattle(data) {
      const mob = data.monster
      const bossTag = data.isBoss ? ' 👑' : ''
      this.pushLog((data.victory ? '✅ ' : '❌ ') + '战斗' + (data.victory ? '胜利' : '失败') +
        ' vs ' + mob.name + '（Lv.' + mob.level + '）' + bossTag, data.victory ? 'win' : 'lose')
      // 展示回合摘要（取关键回合）
      const rounds = data.rounds || []
      const step = Math.max(1, Math.floor(rounds.length / 6))
      for (let i = 0; i < rounds.length; i += step) {
        const r = rounds[i]
        const text = r.log.map(x => x).join('；')
        this.pushLog('  第' + r.round + '回合：' + text, 'round')
      }
      this.pushLog('  回合数：' + data.rounds.length +
        (data.potionUsed ? '，使用药水 ' + data.potionUsed + ' 瓶' : '') +
        '，经验 +' + data.expGained + '，金币 +' + data.goldGained +
        (data.levelUp ? '，🎉 升级 ' + data.levelUp + ' 级！' : ''), 'info')
      if (data.drops && data.drops.length) {
        data.drops.forEach(d => {
          this.pushLog('  🎁 获得：' + d.name + ' ×' + d.quantity, 'drop')
        })
      }
    },
    pushLog(text, type) {
      this.combatLog.push({ text, type })
      if (this.combatLog.length > 80) this.combatLog.shift()
      this.$nextTick(() => {
        const box = this.$refs.logBox
        if (box) box.scrollTop = box.scrollHeight
      })
    },
    clearLog() {
      this.combatLog = []
      this.lastDrops = []
    },
    // ==================== Boss 倒计时 ====================
    syncBossCd() {
      if (this.cdTimer) clearInterval(this.cdTimer)
      this.updateBossCd()
      this.cdTimer = setInterval(() => this.updateBossCd(), 1000)
    },
    updateBossCd() {
      if (!this.save) return
      let remain = this.save.bossCountdown
      if (this.isBattling && remain > 0) {
        remain -= (Date.now() % 1000)
        if (remain < 0) remain = 0
      }
      const sec = Math.ceil(remain / 1000)
      if (sec <= 0) {
        this.bossCdText = ''
        return
      }
      const m = Math.floor(sec / 60)
      const s = sec % 60
      this.bossCdText = m + ':' + (s < 10 ? '0' : '') + s
    },
    // ==================== 装备与药水 ====================
    async equipItem(itemKey) {
      const res = await authFetch('/api/idle-life/equip', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ itemKey })
      })
      const data = await res.json()
      if (data.success) {
        this.save = data.save
        this.pushLog(data.message, 'win')
      } else {
        alert(data.message || '装备失败')
      }
    },
    async unequip(slot) {
      const res = await authFetch('/api/idle-life/unequip', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ slot })
      })
      const data = await res.json()
      if (data.success) {
        this.save = data.save
        this.pushLog(data.message, 'info')
      } else {
        alert(data.message || '操作失败')
      }
    },
    async togglePotion(potionKey) {
      const res = await authFetch('/api/idle-life/potion', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ potionKey: this.save.potionItem === potionKey ? '' : potionKey })
      })
      const data = await res.json()
      if (data.success) {
        this.save = data.save
      } else {
        alert(data.message || '操作失败')
      }
    },
    async loadInventory() {
      const res = await authFetch('/api/idle-life/inventory')
      const data = await res.json()
      if (data.success) this.inventory = data
    },
    itemAttrText(item) {
      const parts = []
      if (item.attack) parts.push('攻+' + item.attack)
      if (item.physDef) parts.push('物防+' + item.physDef)
      if (item.magicDef) parts.push('法防+' + item.magicDef)
      if (item.dodge) parts.push('闪+' + item.dodge + '%')
      if (item.hp) parts.push('命+' + item.hp)
      return parts.join(' ')
    }
  }
}
</script>

<style scoped>
.idle-life {
  max-width: 1100px;
  margin: 0 auto;
  padding: 20px 16px 60px;
}

/* ============ 游客引导 ============ */
.guest-prompt {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
.guest-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px;
  box-shadow: 0 6px 24px rgba(0,0,0,.08);
  text-align: center;
  max-width: 460px;
  width: 100%;
}
.guest-icon { font-size: 56px; }
.guest-card h2 { margin: 12px 0 8px; color: #1f2937; }
.guest-desc { color: #6b7280; font-size: 14px; line-height: 1.8; margin-bottom: 24px; }
.guest-tip { color: #9ca3af; font-size: 12px; line-height: 1.7; margin-top: 16px; }
.btn-big {
  width: 100%;
  padding: 14px;
  font-size: 16px;
  margin-bottom: 12px;
  border-radius: 10px;
}
.guest-info {
  margin-top: 20px;
  background: #ecfdf5;
  border: 1px solid #6ee7b7;
  border-radius: 12px;
  padding: 16px;
  text-align: left;
}
.guest-info h3 { color: #047857; font-size: 15px; margin-bottom: 10px; }
.info-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px dashed #d1fae5;
  font-size: 14px;
  color: #374151;
}
.info-row:last-of-type { border-bottom: none; }

/* ============ 模式切换 ============ */
.mode-tabs {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.mode-tab {
  background: #fff;
  border: 2px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 22px;
  font-weight: 600;
  cursor: pointer;
  color: #374151;
  font-size: 15px;
}
.mode-tab.active {
  border-color: #6366f1;
  color: #6366f1;
  background: #eef2ff;
}
.mode-tab.locked {
  cursor: not-allowed;
  opacity: .7;
  color: #9ca3af;
  display: flex;
  align-items: center;
  gap: 6px;
}
.soon {
  font-size: 11px;
  background: #fef3c7;
  color: #b45309;
  border-radius: 4px;
  padding: 2px 6px;
}

/* ============ 玩家信息卡 ============ */
.player-card {
  background: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,.06);
  margin-bottom: 16px;
}
.player-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.player-name {
  font-size: 18px;
  font-weight: 700;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}
.lv-badge {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 12px;
  border-radius: 999px;
  padding: 3px 10px;
}
.gold { font-size: 17px; font-weight: 700; color: #d97706; }
.exp-bar {
  height: 12px;
  background: #f3f4f6;
  border-radius: 999px;
  overflow: hidden;
  position: relative;
  margin-bottom: 14px;
}
.exp-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #a855f7);
  border-radius: 999px;
  transition: width .4s;
}
.exp-text {
  position: absolute;
  right: 8px;
  top: -1px;
  font-size: 10px;
  color: #6b7280;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}
.stat {
  background: #f9fafb;
  border-radius: 8px;
  padding: 8px 12px;
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.stat-label { font-size: 12px; color: #6b7280; }
.stat b { font-size: 16px; color: #1f2937; }
.stat i { font-size: 11px; color: #9ca3af; font-style: normal; }
.equip-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.equip-slot {
  border: 2px dashed #e5e7eb;
  border-radius: 10px;
  padding: 8px;
  text-align: center;
  min-height: 62px;
}
.equip-slot.filled {
  border-style: solid;
  border-color: #a5b4fc;
  background: #eef2ff;
}
.slot-name { font-size: 11px; color: #9ca3af; margin-bottom: 4px; }
.slot-item { font-size: 13px; font-weight: 600; color: #4338ca; margin-bottom: 4px; }
.slot-empty { font-size: 12px; color: #c4c7cd; padding-top: 6px; }

/* ============ 主布局 ============ */
.main-layout {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}
.battle-panel, .inventory-panel {
  background: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  box-shadow: 0 4px 18px rgba(0,0,0,.06);
}
.battle-panel h3, .inventory-panel h3 {
  font-size: 16px;
  color: #1f2937;
  margin: 0 0 12px;
}

/* ============ 副本 ============ */
.dungeon-list { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; }
.dungeon-card {
  border: 2px solid #e5e7eb;
  border-radius: 12px;
  padding: 12px 14px;
  cursor: pointer;
  transition: all .2s;
}
.dungeon-card:hover { border-color: #a5b4fc; }
.dungeon-card.selected { border-color: #6366f1; background: #eef2ff; }
.dungeon-card.locked { opacity: .55; }
.dungeon-name { font-weight: 700; color: #1f2937; margin-bottom: 4px; }
.dungeon-req { font-size: 12px; color: #6366f1; margin-bottom: 4px; }
.not-enough { color: #ef4444; }
.dungeon-desc { font-size: 12px; color: #6b7280; margin-bottom: 6px; }
.dungeon-info { font-size: 12px; color: #b45309; display: flex; gap: 14px; margin-bottom: 6px; }
.dungeon-mobs { display: flex; flex-wrap: wrap; gap: 6px; }
.mob-chip {
  background: #f3f4f6;
  border-radius: 999px;
  padding: 2px 10px;
  font-size: 11px;
  color: #4b5563;
}

/* ============ 挂机控制 ============ */
.battle-control { margin-bottom: 16px; }
.btn {
  border: none;
  border-radius: 10px;
  padding: 12px 22px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all .2s;
}
.btn:disabled { opacity: .5; cursor: not-allowed; }
.btn-primary { background: linear-gradient(135deg, #6366f1, #8b5cf6); color: #fff; }
.btn-primary:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(99,102,241,.35); }
.btn-danger { background: linear-gradient(135deg, #ef4444, #f97316); color: #fff; }
.btn-danger:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(239,68,68,.35); }
.btn-outline {
  background: #fff;
  border: 2px solid #6366f1;
  color: #6366f1;
}
.btn-outline:hover { background: #eef2ff; }
.btn-mini {
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  color: #374151;
}
.btn-mini:hover { border-color: #6366f1; color: #6366f1; }
.btn-mini.active { background: #10b981; border-color: #10b981; color: #fff; }
.battle-status {
  margin-top: 10px;
  font-size: 13px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dot-battling { color: #10b981; animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: .3; } }
.boss-cd {
  background: #fef2f2;
  color: #dc2626;
  border-radius: 6px;
  padding: 2px 10px;
  font-size: 12px;
}

/* ============ 战斗日志 ============ */
.log-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.battle-log {
  background: #0f172a;
  border-radius: 10px;
  padding: 12px;
  height: 260px;
  overflow-y: auto;
  font-family: monospace;
  font-size: 12.5px;
  line-height: 1.7;
}
.log-empty { color: #64748b; text-align: center; padding-top: 80px; }
.log-line { color: #cbd5e1; white-space: pre-wrap; word-break: break-all; }
.log-line.win { color: #4ade80; font-weight: 700; }
.log-line.lose { color: #f87171; font-weight: 700; }
.log-line.boss { color: #fbbf24; }
.log-line.round { color: #94a3b8; }
.log-line.info { color: #a5b4fc; }
.log-line.drop { color: #fcd34d; }
.drop-panel {
  margin-top: 10px;
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 10px;
  padding: 10px 12px;
}
.drop-title { font-size: 12px; color: #b45309; font-weight: 600; margin-bottom: 6px; }
.drop-list { display: flex; flex-wrap: wrap; gap: 8px; }
.drop-chip {
  font-size: 12px;
  border-radius: 999px;
  padding: 3px 12px;
}
.drop-gold { background: #fef3c7; color: #b45309; }
.drop-equipment { background: #ede9fe; color: #6d28d9; }
.drop-material { background: #dbeafe; color: #1d4ed8; }

/* ============ 背包 ============ */
.inventory-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.item-list { display: flex; flex-direction: column; gap: 8px; max-height: 520px; overflow-y: auto; }
.item-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 10px 12px;
}
.type-equipment { border-left: 4px solid #8b5cf6; }
.type-potion { border-left: 4px solid #f59e0b; }
.type-material { border-left: 4px solid #3b82f6; }
.item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.item-head b { font-size: 14px; color: #1f2937; }
.item-qty { font-size: 12px; color: #6b7280; }
.item-sub {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.tag { font-size: 11px; border-radius: 4px; padding: 2px 6px; }
.equip-tag { background: #ede9fe; color: #6d28d9; }
.potion-tag { background: #fef3c7; color: #b45309; }
.material-tag { background: #dbeafe; color: #1d4ed8; }
.item-attr { font-size: 12px; color: #6b7280; }
.item-actions { display: flex; gap: 8px; }
.potion-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #9ca3af;
  background: #f9fafb;
  border-radius: 8px;
  padding: 8px 10px;
}

/* ============ 移动端适配 ============ */
@media (max-width: 768px) {
  .main-layout { grid-template-columns: 1fr; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .equip-bar { grid-template-columns: repeat(2, 1fr); }
  .guest-prompt { padding: 30px 0; }
  .guest-card { padding: 28px 20px; }
}
</style>
