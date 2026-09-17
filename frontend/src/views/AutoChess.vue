<template>
  <div class="autochess">
    <!-- 加载中 -->
    <div v-if="!ready" class="loading">
      <div class="loading-text">加载中...</div>
    </div>

    <!-- 顶部信息栏 -->
    <div v-if="ready" class="top-bar">
      <div class="info-item hp" :class="{ low: game.hp <= 30 }">
        <span class="info-icon">❤️</span>
        <span class="info-val">{{ game.hp }}</span>
      </div>
      <div class="info-item gold">
        <span class="info-icon">💰</span>
        <span class="info-val">{{ game.gold }}</span>
      </div>
      <div class="info-item level">
        <span class="info-label">Lv</span>
        <span class="info-val">{{ game.level }}</span>
        <span class="info-sub">{{ game.xp }} / {{ xpNeeded }}</span>
        <button @click="doLevelUp" :disabled="game.level >= 8 || game.gold < 4 || game.phase === 'battle'" class="btn-xp">XP</button>
      </div>
      <div class="info-item round">
        <span class="info-label">第</span>
        <span class="info-val">{{ game.round }}</span>
        <span class="info-sub">回合</span>
      </div>
      <div class="info-item field">
        <span class="info-label">上阵</span>
        <span class="info-val">{{ game.board ? game.board.length : 0 }}/{{ game.maxFieldSize }}</span>
      </div>
      <div class="info-item sid-display" :title="'会话ID（可保存恢复游戏）'">
        <span class="info-label">会话</span>
        <span class="info-val sid-text">{{ sid }}</span>
      </div>
      <div v-if="loggedIn" class="info-item user-display">
        <span class="info-icon">👤</span>
        <span class="info-val">{{ username }}</span>
      </div>
    </div>

    <!-- 羁绊栏 -->
    <div v-if="ready" class="synergies-bar">
      <span v-for="s in allSynergies" :key="s.name" class="synergy-tag"
        :class="{ active: isSynergyActive(s.name) }"
        :title="s.desc"
        @click.stop="showSynergyDetail(s)">
        {{ s.name.slice(0,2) }}
        <span class="syn-count">{{ traitCount && traitCount[s.name] || 0 }}/{{ s.required }}</span>
      </span>
      <button class="btn-syn-detail" @click="synergyPanelOpen = !synergyPanelOpen">
        📖 {{ synergyPanelOpen ? '收起' : '详情' }}
      </button>
    </div>

    <!-- 羁绊详情面板 -->
    <div v-if="ready && synergyPanelOpen" class="synergy-panel">
      <div class="syn-panel-header">
        <span>📜 羁绊图鉴</span>
        <button class="btn-close-panel" @click="synergyPanelOpen = false">✕</button>
      </div>
      <div class="syn-list">
        <div v-for="s in allSynergies" :key="'syn-'+s.name" class="syn-item"
          :class="{ active: isSynergyActive(s.name), selected: selectedSynergy === s.name }"
          @click="selectedSynergy = selectedSynergy === s.name ? null : s.name">
          <div class="syn-item-header">
            <span class="syn-item-name">{{ s.name }}</span>
            <span class="syn-item-count">{{ traitCount && traitCount[s.name] || 0 }}/{{ s.required }}</span>
            <span v-if="isSynergyActive(s.name)" class="syn-item-badge">✓</span>
          </div>
          <div v-if="selectedSynergy === s.name" class="syn-item-desc">
            <p>{{ s.desc }}</p>
            <p v-if="isSynergyActive(s.name)" class="syn-active-hint">✅ 此羁绊已激活</p>
            <p v-else class="syn-inactive-hint">
              还需要 <strong>{{ s.required - (traitCount && traitCount[s.name] || 0) }}</strong> 个同羁绊英雄
            </p>
            <div class="syn-heroes">
              <span class="syn-hero-label">拥有此羁绊的英雄：</span>
              <span v-for="h in heroesForSynergy(s.name)" :key="h" class="syn-hero-tag">{{ h }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 战斗区域：策划阶段显示双方棋盘，战斗阶段显示统一战场 -->
    <div v-if="ready" class="battle-area">
      <!-- 策划阶段：分离的棋盘 -->
      <div v-if="!battleActive" :key="'planning'">
        <div class="board enemy-board" :class="{ dimmed: game.phase === 'planning' && enemyBoardEmpty, ghost: game.enemySource === 'ghost' }">
          <div class="board-label">{{ enemySourceLabel(game.enemySource) }}</div>
          <div class="board-grid">
            <div v-for="r in boardRows" :key="'er'+r" class="board-row">
              <div v-for="c in boardCols" :key="'ec'+c" class="cell enemy-cell"
                :class="{ occupied: getEnemyAt(r-1,c-1) }">
                <div v-if="getEnemyAt(r-1,c-1)" class="champ enemy-champ" :class="'star'+getEnemyAt(r-1,c-1).star">
                  <div class="champ-stars"><span v-for="s in getEnemyAt(r-1,c-1).star" :key="s">★</span></div>
                  <div class="champ-name">{{ getEnemyAt(r-1,c-1).name }}</div>
                  <div class="champ-hp"><span class="hp-fill" :style="{ width: enemyHpPct(getEnemyAt(r-1,c-1))+'%' }"></span></div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="board player-board">
          <div class="board-label">我方阵地</div>
          <div class="board-grid">
            <div v-for="r in boardRows" :key="'pr'+r" class="board-row">
              <div v-for="c in boardCols" :key="'pc'+c" class="cell player-cell"
                :class="{ occupied: getPlayerAt(r-1,c-1), highlighted: selectedBenchChamp && !getPlayerAt(r-1,c-1) }"
                @click="handleChampClick(r-1, c-1)">
                <div v-if="getPlayerAt(r-1,c-1)" class="champ player-champ"
                  :class="['star'+getPlayerAt(r-1,c-1).star, 'tier'+getPlayerAt(r-1,c-1).tier, { 'equip-target': selectedEquipmentId }]">
                  <div class="champ-stars"><span v-for="s in getPlayerAt(r-1,c-1).star" :key="s">★</span></div>
                  <div class="champ-name">{{ getPlayerAt(r-1,c-1).name }}</div>
                  <div class="champ-hp"><span class="hp-fill" :style="{ width: playerHpPct(getPlayerAt(r-1,c-1))+'%' }"></span></div>
                  <div class="champ-atk">⚔{{ getPlayerAt(r-1,c-1).atk }}</div>
                  <!-- 装备图标 -->
                  <div class="champ-equip-slots" v-if="getPlayerAt(r-1,c-1).equipment && getPlayerAt(r-1,c-1).equipment.length">
                    <span v-for="eq in getPlayerAt(r-1,c-1).equipment" :key="eq.id" class="mini-equip"
                      :class="'equip-'+eq.type+' tier'+eq.tier"
                      :title="eq.name+': '+eq.desc"
                      @click.stop="doUnequip(getPlayerAt(r-1,c-1).uid, eq.id)">
                      {{ equipIcon(eq) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 战斗阶段：统一战场 9行 (0-3敌,4中线,5-8我) -->
      <div v-else class="battlefield" ref="battlefieldRef">
        <div class="bf-header">
          <span class="bf-label enemy-label">{{ enemySourceLabel(game.enemySource) }}</span>
          <span v-if="!battleResult" class="bf-vs">⚔ 激战</span>
          <span v-else-if="battleResult==='win'" class="bf-vs win">🎉 胜利!</span>
          <span v-else class="bf-vs lose">💔 失败!</span>
          <span class="bf-label player-label">我方 🛡</span>
        </div>
        <div class="bf-debug" v-if="battleProjectile">{{ debugInfo }}</div>
        <div class="bf-grid">
          <div v-for="visRow in 9" :key="'bfr'+visRow" class="bf-row" :class="{ 'bf-center': visRow === 5 }">
            <div v-for="c in boardCols" :key="'bfc'+visRow+'-'+c" class="bf-cell"
              :data-bf-row="visualToBattle(visRow-1)" :data-bf-col="c-1"
              :class="cellClasses(visRow-1, c-1)"
              :style="cellStyle(visRow-1, c-1)">
              <div v-if="getBattleUnit(visRow-1, c-1)" class="bf-unit"
                :class="['weapon-'+getBattleUnit(visRow-1, c-1).weapon, getBattleUnit(visRow-1,c-1).side,
                          { dead: getBattleUnit(visRow-1,c-1).hp <= 0 }]">
                <div class="bf-unit-icon">
                  <span v-if="getBattleUnit(visRow-1,c-1).hp <= 0">💀</span>
                  <span v-else>{{ weaponIcon(getBattleUnit(visRow-1,c-1).weapon) }}</span>
                </div>
                <div class="bf-unit-name">{{ getBattleUnit(visRow-1,c-1).name }}</div>
                <div class="bf-unit-hp-outer"><div class="bf-unit-hp-fill" :style="{ width: getBattleUnit(visRow-1,c-1).hpPct+'%' }"></div></div>
                <div class="bf-unit-mana-outer"><div class="bf-unit-mana-fill" :style="{ width: getBattleUnit(visRow-1,c-1).manaPct+'%' }"></div></div>
              </div>
            </div>
          </div>
        </div>
        <!-- 投射物动画 -->
        <div v-if="battleProjectile" class="bf-projectile"
          :style="projectileStyle"
          :class="'wpn-'+battleProjectile.weapon">
          <span class="proj-icon">{{ weaponIcon(battleProjectile.weapon) }}</span>
        </div>
        <!-- 浮动伤害 -->
        <div v-for="dmg in battleFloatingDmgs" :key="dmg.id"
          class="bf-float-dmg"
          :class="[dmg.action, dmg.damageType === 'magic' ? 'magic-dmg' : 'physical-dmg']"
          :style="{ left: dmg.left+'px', top: dmg.top+'px' }">
          <template v-if="dmg.action === 'skill'">
            <span class="fd-skill-icon">✦</span>
            <span class="fd-skill-name">{{ dmg.skillName }}</span>
          </template>
          <template v-else-if="dmg.action === 'skillhit'">
            <span class="fd-skill-hit">⚡</span>
            -{{ dmg.damage }}
          </template>
          <template v-else>
            <span v-if="dmg.action==='crit'" class="fd-prefix">💥</span>
            <span v-else-if="dmg.action==='double'" class="fd-prefix">⚡</span>
            <span v-if="dmg.damageType === 'magic'" class="fd-icon">✨</span>
            <span v-else class="fd-icon">⚔</span>
            -{{ dmg.damage }}
          </template>
        </div>
        <div v-if="battleResult" class="bf-result-bar">
          <button @click="finishBattle" class="btn-continue">
            {{ battleResult === 'win' ? '✅ 继续征战' : '💔 接受失败' }} (下一回合)
          </button>
        </div>
      </div>
    </div>

    <!-- 备战席 -->
    <div v-if="ready" class="bench-section">
      <div class="section-label">备战席</div>
      <div class="bench-slots">
        <div v-for="i in benchSize" :key="'b'+i" class="bench-slot"
          :class="{ filled: bench[i-1], selected: selectedBenchChamp && selectedBenchChamp.uid === bench[i-1]?.uid }"
          @click="selectBench(i-1)">
          <div v-if="bench[i-1]" class="champ bench-champ" :class="['star'+bench[i-1].star, 'tier'+bench[i-1].tier]">
            <div class="champ-stars"><span v-for="s in bench[i-1].star" :key="s">★</span></div>
            <div class="champ-name">{{ bench[i-1].name }}</div>
            <div class="champ-cost">💰{{ bench[i-1].tier }}</div>
            <!-- 装备图标 -->
            <div class="champ-equip-slots" v-if="bench[i-1].equipment && bench[i-1].equipment.length">
              <span v-for="eq in bench[i-1].equipment" :key="eq.id" class="mini-equip"
                :class="'equip-'+eq.type+' tier'+eq.tier"
                :title="eq.name+': '+eq.desc"
                @click.stop="doUnequip(bench[i-1].uid, eq.id)">
                {{ equipIcon(eq) }}
              </span>
            </div>
            <button class="btn-sell" @click.stop="doSell(bench[i-1].uid)">卖</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 装备背包 -->
    <div v-if="ready && game.equipmentInventory && game.equipmentInventory.length > 0" class="equip-inv">
      <div class="equip-inv-title">🎒 装备背包 ({{ game.equipmentInventory.length }}件)</div>
      <div class="equip-inv-slots">
        <div v-for="eq in game.equipmentInventory" :key="eq.id" class="equip-inv-item"
          :class="['equip-'+eq.type, 'tier'+eq.tier, { selected: selectedEquipmentId === eq.id }]"
          :title="eq.name + ': ' + eq.desc"
          @click.stop="selectEquipmentFromInv(eq.id)">
          <div class="equip-inv-icon">{{ equipIcon(eq) }}</div>
          <div class="equip-inv-name">{{ eq.name }}</div>
          <div class="equip-inv-desc">{{ eq.desc }}</div>
        </div>
      </div>
      <div v-if="selectedEquipmentId" class="equip-hint">
        点击棋盘或备战席上的英雄来佩戴装备（再次点击装备取消选择）
      </div>
    </div>

    <!-- 商店 -->
    <div v-if="ready" class="shop-section">
      <div class="section-label">
        商店
        <button @click="doRefresh" :disabled="game.gold < 2 || game.phase === 'battle'" class="btn-refresh">
          🔄 刷新 (2💰)
        </button>
      </div>
      <div class="shop-slots">
        <div v-for="i in 5" :key="'s'+i" class="shop-slot"
          :class="{ empty: !shop[i-1], hoverable: shop[i-1] && game.gold >= shop[i-1].tier && game.phase === 'planning' }"
          @click="doBuy(i-1)">
          <div v-if="shop[i-1]" class="champ shop-champ" :class="'tier'+shop[i-1].tier">
            <div class="champ-name">{{ shop[i-1].name }}</div>
            <div class="champ-cost">💰{{ shop[i-1].tier }}</div>
            <div class="champ-traits">{{ shop[i-1].traits ? shop[i-1].traits.join(' ') : '' }}</div>
          </div>
          <div v-else class="empty-text">已购买</div>
        </div>
      </div>
    </div>

    <!-- 操作按钮 -->
    <div v-if="ready" class="action-bar">
      <button v-if="game.phase === 'planning'" @click="doBattle" class="btn-action btn-fight" :disabled="!game.board || game.board.length === 0 || battleActive">
        ⚔️ 开始战斗
      </button>
      <button v-if="battleResult" @click="finishBattle" class="btn-action btn-next">
        ➡️ 下一回合
      </button>
      <button v-if="game.phase === 'gameover'" @click="doNewGame" class="btn-action btn-new">
        🔄 重新开始
      </button>
      <button @click="doNewGame" class="btn-action btn-reset" v-if="game.phase !== 'gameover'">
        🔄 新游戏
      </button>
      <button v-if="battleActive && !battleResult" @click="skipBattle" class="btn-action btn-skip">
        ⏩ 跳过动画
      </button>
    </div>

    <!-- 战斗日志 -->
    <div v-if="ready" class="battle-log" v-show="(game.lastRoundLog && game.lastRoundLog.length > 0) || game.battleLog">
      <div class="log-title">📜 战报</div>
      <div v-for="(line, i) in displayLog" :key="i" class="log-line">{{ line }}</div>
    </div>

    <!-- 错误/提示 -->
    <div class="toast" v-if="toastMsg" @click="toastMsg = ''">{{ toastMsg }}</div>
  </div>
</template>

<script>
import { isLoggedIn, getUsername } from '@/utils/auth'

const API = '/api/autochess'

export default {
  name: 'AutoChessView',
  data() {
    return {
      sid: '',
      ready: false,
      game: { hp: 100, gold: 0, level: 1, xp: 0, round: 0, phase: 'planning', maxFieldSize: 1, board: [], bench: [], shop: [], enemyBoard: [], battleLog: '', lastRoundLog: [], equipmentInventory: [] },
      bench: [],
      shop: [],
      boardRows: 4,
      boardCols: 7,
      benchSize: 8,
      traitCount: {},
      activeSynergies: [],
      allSynergies: [],
      synergyPanelOpen: false,
      selectedSynergy: null,
      selectedBenchChamp: null,
      selectedEquipmentId: null,      // 当前选中要佩戴的装备ID
      toastMsg: '',
      // 棋盘战斗
      battleActive: false,
      battleResult: null,       // null | 'win' | 'lose'
      battleMap: {},
      battleEvents: [],
      battleEventIdx: 0,
      battleProjectile: null,  // { x,y,dx,dy,weapon,action }
      battleFloatingDmgs: [],
      battleFloatingId: 0,
      battleAttackerKey: null,
      battleTargetKey: null,
      battleAttackerFlashes: [],   // 并发攻击闪光
      battleTargetFlashes: [],
      battleSkillFlashes: [],       // 技能释放闪光
      pendingGameState: null,
      animTimer: null,
      unitTimers: {},              // { atkKey: timerId }
      activeTimelineUnits: 0,
      completedTimelineUnits: 0
    }
  },
  computed: {
    loggedIn() { return isLoggedIn() },
    username() { return getUsername() },
    xpNeeded() {
      const tbl = [0, 2, 2, 4, 6, 8, 10, 14]
      return tbl[this.game.level] || 14
    },
    enemyBoardEmpty() {
      return !this.game.enemyBoard || this.game.enemyBoard.length === 0
    },
    displayLog() {
      if (this.game.battleLog) return this.game.battleLog.split('\n').filter(l => l.trim())
      return this.game.lastRoundLog || []
    },
    projectileStyle() {
      const p = this.battleProjectile
      if (!p) return { display: 'none' }
      return {
        position: 'fixed',
        left: p.left + 'px',
        top: p.top + 'px',
        '--dx': p.dx + 'px',
        '--dy': p.dy + 'px'
      }
    },
    debugInfo() {
      const p = this.battleProjectile
      if (!p) return 'no projectile'
      return `L:${Math.round(p.left)} T:${Math.round(p.top)} dx:${Math.round(p.dx)} dy:${Math.round(p.dy)}`
    }
  },
  // 路由进入守卫：每次进入本页面都恢复存档（覆盖刷新&SPA跳转）
  beforeRouteEnter(to, from, next) {
    next(vm => {
      vm.tryRecoverGame()
    })
  },
  mounted() {
    // 兼容不使用路由的场景（极少见，保留为兜底）
    if (!this._recovered) {
      this.tryRecoverGame()
    }
  },
  methods: {
    async api(method, path, body) {
      const res = await fetch(API + path, {
        method,
        headers: { 'Content-Type': 'application/json' },
        body: body ? JSON.stringify({ sid: this.sid, ...body }) : undefined
      })
      const data = await res.json()
      if (data.error) {
        this.toastMsg = data.error
        setTimeout(() => this.toastMsg = '', 2500)
        return null
      }
      return data
    },

    updateState(data) {
      if (!data) return
      this.game = data
      this.bench = data.bench || []
      this.shop = data.shop || []
      this.boardRows = data.boardRows || 4
      this.boardCols = data.boardCols || 7
      this.benchSize = data.benchSize || 8
      this.traitCount = data.traitCount || {}
      this.activeSynergies = data.activeSynergies || []
      this.allSynergies = data.allSynergies || []
      // 装备系统 — 仅在 data 包含时才更新，防止某些端点不返回该字段导致清空
      if (data.equipmentInventory !== undefined) {
        this.game.equipmentInventory = data.equipmentInventory || []
      }
    },

    async doNewGame() {
      this._recovered = false
      this._recovering = false
      this.ready = false
      // 清理战斗状态
      this.cleanupUnitTimers()
      if (this.animTimer) clearTimeout(this.animTimer)
      this.battleActive = false; this.battleResult = null;
      this.battleMap = {}; this.battleEvents = []; this.battleEventIdx = 0;
      this.battleProjectile = null; this.battleFloatingDmgs = [];
      this.battleAttackerFlashes = []; this.battleTargetFlashes = []; this.battleSkillFlashes = [];
      this.pendingGameState = null;
      const data = await this.api('GET', '/new')
      if (data) {
        this.sid = data.sessionId
        localStorage.setItem('autochess_sid', this.sid)
        this.updateState(data)
        this.selectedBenchChamp = null
        this.ready = true
      }
    },

    async tryRecoverGame() {
      // 防止 beforeRouteEnter 和 mounted 重复调用
      if (this._recovering) return
      this._recovering = true
      const savedSid = localStorage.getItem('autochess_sid')
      if (!savedSid) {
        this._recovering = false
        this.doNewGame()
        return
      }
      try {
        // 尝试恢复，直接发 GET /recover
        const res = await fetch(API + '/recover?sid=' + savedSid)
        const data = await res.json()
        if (data.error) {
          // 恢复失败，清理并开始新游戏
          this._recovering = false
          localStorage.removeItem('autochess_sid')
          this.doNewGame()
          return
        }
        // 恢复成功
        this.sid = data.sessionId || savedSid
        localStorage.setItem('autochess_sid', this.sid)
        this.updateState(data)
        this.ready = true
        this._recovered = true
        this._recovering = false
        this.toastMsg = '欢迎回来！已恢复游戏进度'
        setTimeout(() => this.toastMsg = '', 2500)
      } catch (e) {
        this._recovering = false
        localStorage.removeItem('autochess_sid')
        this.doNewGame()
      }
    },

    async doRefresh() {
      const data = await this.api('POST', '/refresh', {})
      this.updateState(data)
    },

    async doBuy(slot) {
      if (this.game.phase !== 'planning') return
      const c = this.shop[slot]
      if (!c || this.game.gold < c.tier) {
        this.toastMsg = '金币不足!'
        setTimeout(() => this.toastMsg = '', 2000)
        return
      }
      const data = await this.api('POST', '/buy', { slot })
      this.updateState(data)
      this.selectedBenchChamp = null
    },

    async doSell(uid) {
      if (this.game.phase !== 'planning') return
      const data = await this.api('POST', '/sell', { uid })
      this.updateState(data)
      if (this.selectedBenchChamp && this.selectedBenchChamp.uid === uid) {
        this.selectedBenchChamp = null
      }
    },

    selectBench(index) {
      if (this.game.phase !== 'planning') return
      const c = this.bench[index]
      if (!c) {
        this.selectedBenchChamp = null
        return
      }
      // 如果有选中的装备，佩戴到该英雄
      if (this.selectedEquipmentId) {
        this.doEquip(c.uid, this.selectedEquipmentId)
        return
      }
      if (this.selectedBenchChamp && this.selectedBenchChamp.uid === c.uid) {
        this.selectedBenchChamp = null
      } else {
        this.selectedBenchChamp = c
      }
    },

    async handleCellClick(row, col) {
      if (this.game.phase !== 'planning') return

      const existing = this.getPlayerAt(row, col)

      if (this.selectedBenchChamp && !existing) {
        // 放置选中英雄到棋盘
        const data = await this.api('POST', '/place', { uid: this.selectedBenchChamp.uid, row, col })
        this.updateState(data)
        this.selectedBenchChamp = null
      } else if (existing && !this.selectedBenchChamp) {
        // 点击棋盘上的英雄：下阵
        const data = await this.api('POST', '/tobench', { uid: existing.uid })
        this.updateState(data)
      } else if (existing && this.selectedBenchChamp) {
        // 已有英雄 + 选中bench -> 换位：先下阵旧英雄，再放新英雄
        const data1 = await this.api('POST', '/tobench', { uid: existing.uid })
        this.updateState(data1)
        const data2 = await this.api('POST', '/place', { uid: this.selectedBenchChamp.uid, row, col })
        this.updateState(data2)
        this.selectedBenchChamp = null
      }
    },

    async doLevelUp() {
      if (this.game.level >= 8 || this.game.gold < 4) return
      const data = await this.api('POST', '/levelup', {})
      this.updateState(data)
    },

    // ============ 统一棋盘战斗系统 ============

    weaponRules(champName) {
      if (!champName) return 'melee'
      // 特殊高费棋子优先匹配
      if (champName.includes('冰霜巨龙') || champName.includes('深渊恶魔')) return 'magic'
      if (champName.includes('龙骑')) return 'sword'
      if (champName.includes('牛头')) return 'axe'
      if (champName.includes('虚空行者')) return 'dagger'
      if (champName.includes('女巫')) return 'magic'
      // 通用匹配
      if (champName.includes('射')) return 'bow'
      if (champName.includes('巫') || champName.includes('法') || champName.includes('妖')) return 'magic'
      if (champName.includes('狂')) return 'axe'
      if (champName.includes('剑')) return 'sword'
      if (champName.includes('骑') || champName.includes('将军')) return 'sword'
      if (champName.includes('圣')) return 'sword'
      if (champName.includes('刺')) return 'dagger'
      if (champName.includes('兽')) return 'claw'
      return 'melee'
    },

    weaponIcon(weapon) {
      const map = { bow: '🏹', sword: '⚔️', axe: '🪓', magic: '🔥', dagger: '🗡️', claw: '👊', melee: '⚔️' }
      return map[weapon] || '⚔️'
    },

    // 9行视觉战场 → battleRow 映射
    // visRow 0-3=敌区, 4=中线, 5-8=我区
    visualToBattle(visRow) {
      if (visRow <= 3) return 8 - visRow    // 敌后排vis0→b8, 敌前排vis3→b5
      if (visRow >= 5) return 8 - visRow    // 我前排vis5→b3, 我后排vis8→b0
      return -1
    },

    getBattleUnit(visRow, visCol) {
      const bRow = this.visualToBattle(visRow)
      if (bRow < 0) return null
      return this.battleMap[bRow + ',' + visCol] || null
    },

    cellClasses(visRow, visCol) {
      const cls = []
      if (visRow === 4) cls.push('bf-center-cell')
      if (visRow <= 3) cls.push('bf-enemy-zone')
      if (visRow >= 5) cls.push('bf-player-zone')
      const unit = this.getBattleUnit(visRow, visCol)
      if (unit) {
        cls.push('has-unit')
        if (unit.hp <= 0) cls.push('dead')
        const key = this.visualToBattle(visRow) + ',' + visCol
        if (this.battleAttackerKey === key || this.battleAttackerFlashes.includes(key)) cls.push('flash-atk')
        if (this.battleTargetKey === key || this.battleTargetFlashes.includes(key)) cls.push('flash-hit')
        if (this.battleSkillFlashes.includes(key)) cls.push('flash-skill')
      }
      return cls.join(' ')
    },

    cellStyle(visRow, visCol) {
      return {}
    },

    async doBattle() {
      const data = await this.api('POST', '/battle', {})
      if (!data) return
      const events = data.battleEvents || []
      if (events.length <= 1) {
        // No real battle — still need to advance round & process equipment
        this.updateState(data)
        this.battleActive = false
        this.battleResult = data.playerWon ? 'win' : 'lose'
        return
      }
      this.pendingGameState = data
      this.battleEvents = events
      this.battleFloatingDmgs = []
      this.battleFloatingId = 0
      this.battleResult = null
      this.battleAttackerKey = null
      this.battleTargetKey = null
      this.battleAttackerFlashes = []
      this.battleTargetFlashes = []
      this.battleProjectile = null
      this.battleMap = {}

      // 清理旧的独立计时器
      this.cleanupUnitTimers()

      const initEvt = events[0]
      if (initEvt) {
        const addUnits = (list, side) => {
          if (!list) return
          for (const u of list) {
            const key = u.row + ',' + u.col
            this.battleMap[key] = {
              name: u.name,
              hp: u.hp || u.maxHp || 1,
              maxHp: u.maxHp || 1,
              hpPct: Math.max(0, Math.round((u.hp || u.maxHp || 0) / (u.maxHp || 1) * 100)),
              maxMana: u.maxMana || 100,
              currentMana: u.currentMana || 0,
              manaPct: 0,
              side: side,
              weapon: this.weaponRules(u.name),
              uid: u.uid
            }
          }
        }
        addUnits(initEvt.playerUnits, 'player')
        addUnits(initEvt.enemyUnits, 'enemy')
      }

      this.battleActive = true
      await this.$nextTick()
      await this.delay(400)

      // 启动独立时间轴（每个棋子有自己的攻击 CD）
      this.startBattleTimeline()
    },

    // ===== 独立攻击时间轴系统 =====
    startBattleTimeline() {
      // 按攻击棋子分组事件
      const unitQueues = {}
      for (const evt of this.battleEvents) {
        if (evt.action === 'init') continue
        const key = evt.attackerRow + ',' + evt.attackerCol
        if (!unitQueues[key]) unitQueues[key] = []
        unitQueues[key].push(evt)
      }

      this.activeTimelineUnits = Object.keys(unitQueues).length
      this.completedTimelineUnits = 0

      for (const [atkKey, queue] of Object.entries(unitQueues)) {
        this.scheduleUnitAttack(atkKey, queue, 0)
      }
    },

    scheduleUnitAttack(atkKey, queue, idx) {
      if (this.battleResult) return

      // 队列耗尽：这个棋子完成了它全部攻击事件
      if (idx >= queue.length) {
        this.completedTimelineUnits++
        if (this.completedTimelineUnits >= this.activeTimelineUnits) {
          this.onTimelineComplete()
        }
        return
      }

      const unit = this.battleMap[atkKey]
      // 棋子从地图消失（不应发生）→ 标记完成
      if (!unit) {
        this.completedTimelineUnits++
        if (this.completedTimelineUnits >= this.activeTimelineUnits) {
          this.onTimelineComplete()
        }
        return
      }

      // 根据不同武器类型决定攻击间隔（CD，毫秒）
      const cd = this.weaponCD(unit.weapon)
      // 首次攻击用短延迟并随机错开，避免所有单位同时出手
      const initialDelay = idx === 0 ? 200 + Math.random() * 400 : cd

      this.unitTimers[atkKey] = setTimeout(() => {
        const u = this.battleMap[atkKey]
        if (!u) return

        const evt = queue[idx]

        // ========== 技能事件处理 ==========
        if (evt.action === 'skill') {
          // 闪光：释放技能
          this.flashSkill(atkKey)
          // 技能名称弹字
          const posSkill = this.getCellViewportCenter(evt.attackerRow, evt.attackerCol)
          if (posSkill) {
            this.battleFloatingDmgs.push({
              id: ++this.battleFloatingId,
              damage: 0,
              action: 'skill',
              skillName: evt.skillName || '',
              left: posSkill.left,
              top: posSkill.top
            })
          }

          if (evt.aoeTargets) {
            // AOE技能：命中多个目标
            for (const t of evt.aoeTargets) {
              const tgtKey = t.row + ',' + t.col
              const tgtUnit = this.battleMap[tgtKey]
              if (tgtUnit) {
                if (tgtUnit.hp > 0) this.flashTarget(tgtKey)
                const dmg = t.damage || 0
                if (dmg > 0) {
                  tgtUnit.hp = Math.max(0, (t.hpAfter !== undefined ? t.hpAfter : tgtUnit.hp - dmg))
                  tgtUnit.hpPct = Math.max(0, Math.round(tgtUnit.hp / (tgtUnit.maxHp || 1) * 100))
                  this.battleMap[tgtKey] = { ...tgtUnit }
                }
                const pos = this.getCellViewportCenter(t.row, t.col)
                if (pos && dmg > 0) {
                  this.battleFloatingDmgs.push({
                    id: ++this.battleFloatingId,
                    damage: dmg,
                    action: 'skillhit',
                    left: pos.left,
                    top: pos.top
                  })
                }
              }
            }
          } else if (evt.targetRow !== undefined) {
            // 单目标技能
            const tgtKey = evt.targetRow + ',' + evt.targetCol
            const tgtUnit = this.battleMap[tgtKey]
            if (tgtUnit && tgtUnit.hp > 0) {
              this.flashTarget(tgtKey)
              const dmg = evt.damage || 0
              if (dmg > 0) {
                tgtUnit.hp = Math.max(0, (evt.targetHpAfter !== undefined ? evt.targetHpAfter : tgtUnit.hp - dmg))
                tgtUnit.hpPct = Math.max(0, Math.round(tgtUnit.hp / (tgtUnit.maxHp || 1) * 100))
                this.battleMap[tgtKey] = { ...tgtUnit }
              }
              const pos = this.getCellViewportCenter(evt.targetRow, evt.targetCol)
              if (pos && dmg > 0) {
                this.battleFloatingDmgs.push({
                  id: ++this.battleFloatingId,
                  damage: dmg,
                  action: 'skillhit',
                  left: pos.left,
                  top: pos.top
                })
              }
            }
          }

          // 更新攻击者法力条（重新读取防止竞态覆盖血条）
          const skillAttacker = this.battleMap[atkKey]
          if (skillAttacker) {
            skillAttacker.currentMana = 0
            skillAttacker.manaPct = 0
            this.battleMap[atkKey] = { ...skillAttacker }
          }

          // 清理旧浮动伤害
          this.battleFloatingDmgs = this.battleFloatingDmgs.filter(ft => this.battleFloatingId - ft.id < 6)

          this.scheduleUnitAttack(atkKey, queue, idx + 1)
          return
        }

        const tgtKey = evt.targetRow + ',' + evt.targetCol
        const tgtUnit = this.battleMap[tgtKey]

        // 目标已死亡或消失 → 跳到下一次攻击
        if (!tgtUnit || tgtUnit.hp <= 0) {
          this.scheduleUnitAttack(atkKey, queue, idx + 1)
          return
        }

        // 闪光：攻击方（仅活着时闪光）
        if (u.hp > 0) this.flashAttacker(atkKey)

        // 发射投射物（无论攻击方死活，后端已预计算好事件队列）
        this.fireProjectile(atkKey, tgtKey, u.weapon, evt.action)
        const flyTime = this.battleProjectile ? 380 : 0

        // 投射物到达后显示伤害
        const dmgId = ++this.battleFloatingId
        const capturedTgtKey = tgtKey
        const capturedDamage = evt.damage || 0
        const capturedAction = evt.action || 'hit'
        const capturedDamageType = evt.damageType || 'physical'
        const capturedTargetRow = evt.targetRow
        const capturedTargetCol = evt.targetCol
        const capturedTargetHpAfter = evt.targetHpAfter

        setTimeout(() => {
          // 始终从 battleMap 新鲜读取，避免多投射物命中同一目标时的竞态
          const freshUnit = this.battleMap[capturedTgtKey]
          if (freshUnit && freshUnit.hp > 0) this.flashTarget(capturedTgtKey)

          // 计算伤害显示坐标
          const pos = this.getCellViewportCenter(capturedTargetRow, capturedTargetCol)
          if (pos) {
            this.battleFloatingDmgs.push({
              id: dmgId,
              damage: capturedDamage,
              action: capturedAction,
              damageType: capturedDamageType,
              left: pos.left,
              top: pos.top
            })
          }

          // 更新血量 — 优先使用后端 targetHpAfter 保持数据一致
          if (freshUnit && freshUnit.hp > 0) {
            freshUnit.hp = capturedTargetHpAfter !== undefined
              ? Math.max(0, capturedTargetHpAfter)
              : Math.max(0, freshUnit.hp - capturedDamage)
            freshUnit.hpPct = Math.max(0, Math.round(freshUnit.hp / (freshUnit.maxHp || 1) * 100))
            this.battleMap[capturedTgtKey] = { ...freshUnit }
          }

          this.battleProjectile = null

          // 攻击者回蓝（重新读取防止竞态覆盖血条）
          const attacker = this.battleMap[atkKey]
          if (attacker && attacker.hp > 0) {
            attacker.currentMana = Math.min(attacker.maxMana, (attacker.currentMana || 0) + Math.ceil(attacker.maxMana * 0.15))
            attacker.manaPct = Math.round(attacker.currentMana / (attacker.maxMana || 1) * 100)
            this.battleMap[atkKey] = { ...attacker }
          }

          // 清理旧浮动伤害
          this.battleFloatingDmgs = this.battleFloatingDmgs.filter(ft => this.battleFloatingId - ft.id < 6)

          // 安排下一次攻击
          this.scheduleUnitAttack(atkKey, queue, idx + 1)
        }, flyTime + 50)
      }, initialDelay)
    },

    flashAttacker(key) {
      this.battleAttackerFlashes.push(key)
      setTimeout(() => {
        const i = this.battleAttackerFlashes.indexOf(key)
        if (i >= 0) this.battleAttackerFlashes.splice(i, 1)
      }, 350)
    },

    flashSkill(key) {
      this.battleSkillFlashes.push(key)
      setTimeout(() => {
        const i = this.battleSkillFlashes.indexOf(key)
        if (i >= 0) this.battleSkillFlashes.splice(i, 1)
      }, 600)
    },

    flashTarget(key) {
      this.battleTargetFlashes.push(key)
      setTimeout(() => {
        const i = this.battleTargetFlashes.indexOf(key)
        if (i >= 0) this.battleTargetFlashes.splice(i, 1)
      }, 400)
    },

    weaponCD(weapon) {
      const cds = {
        dagger: 700,   // 匕首 — 最快
        bow: 880,      // 弓 — 快
        sword: 1120,   // 剑 — 中等
        claw: 1280,    // 爪 — 中等偏慢
        axe: 1520,     // 斧 — 慢
        magic: 1760,   // 法术 — 最慢
        melee: 1200    // 默认
      }
      return cds[weapon] || 1200
    },

    fireProjectile(fromKey, toKey, weapon, action) {
      const [fr, fc] = fromKey.split(',').map(Number)
      const [tr, tc] = toKey.split(',').map(Number)
      const fromPos = this.getCellViewportCenter(fr, fc)
      const toPos = this.getCellViewportCenter(tr, tc)
      if (!fromPos || !toPos) {
        this.battleProjectile = null
        return
      }
      this.battleProjectile = {
        left: fromPos.left,
        top: fromPos.top,
        dx: toPos.left - fromPos.left,
        dy: toPos.top - fromPos.top,
        weapon,
        action
      }
    },

    /** 取得战场格子在视口内的真实中心坐标（用 fixed 定位避免容器偏移问题） */
    getCellViewportCenter(battleRow, battleCol) {
      const cell = this.$el.querySelector(`[data-bf-row="${battleRow}"][data-bf-col="${battleCol}"]`)
      if (!cell) return null
      const cRect = cell.getBoundingClientRect()
      return {
        left: cRect.left + cRect.width / 2,
        top: cRect.top + cRect.height / 2
      }
    },

    onTimelineComplete() {
      // 等待最后一枚投射物落地
      setTimeout(() => {
        this.battleProjectile = null
        this.battleAttackerFlashes = []
        this.battleTargetFlashes = []
        const state = this.pendingGameState
        const enemyAlive = state && state.enemyBoard && state.enemyBoard.length > 0
        this.battleResult = enemyAlive ? 'lose' : 'win'
      }, 600)
    },

    cleanupUnitTimers() {
      for (const key of Object.keys(this.unitTimers)) {
        clearTimeout(this.unitTimers[key])
      }
      this.unitTimers = {}
    },

    /* ---- 投射物计算属性 ---- */


    skipBattle() {
      this.cleanupUnitTimers()
      if (this.animTimer) clearTimeout(this.animTimer)
      this.battleProjectile = null
      this.battleFloatingDmgs = []
      this.battleAttackerKey = null
      this.battleTargetKey = null
      this.battleAttackerFlashes = []
      this.battleTargetFlashes = []
      const state = this.pendingGameState
      const enemyAlive = state && state.enemyBoard && state.enemyBoard.length > 0
      this.battleResult = enemyAlive ? 'lose' : 'win'
    },

    finishBattle() {
      this.cleanupUnitTimers()
      if (this.animTimer) clearTimeout(this.animTimer)
      this.battleActive = false
      this.battleResult = null
      this.battleAttackerKey = null
      this.battleTargetKey = null
      this.battleAttackerFlashes = []
      this.battleTargetFlashes = []
      this.battleFloatingDmgs = []
      this.battleEvents = []
      this.battleMap = {}
      this.battleProjectile = null
      if (this.pendingGameState) {
        this.updateState(this.pendingGameState)
        this.pendingGameState = null
        this.selectedBenchChamp = null
      }
      this.doNextRound()
    },

    delay(ms) {
      return new Promise(resolve => setTimeout(resolve, ms))
    },

    async updateAfterBattle() {
      if (this.game.phase === 'gameover') return
      const data = await this.api('POST', '/next', {})
      if (!data) return
      this.updateState(data)
      if (data.msg) {
        this.toastMsg = data.msg
        setTimeout(() => this.toastMsg = '', 2500)
      }
    },

    async doEquip(championUid, equipmentId) {
      if (this.game.phase !== 'planning') {
        this.toastMsg = '只能在准备阶段佩戴装备'
        setTimeout(() => this.toastMsg = '', 2000)
        return
      }
      const data = await this.api('POST', '/equip', { championUid, equipmentId })
      if (data) {
        this.updateState(data)
        this.selectedEquipmentId = null
        this.toastMsg = '装备已佩戴'
        setTimeout(() => this.toastMsg = '', 1500)
      }
    },

    async doUnequip(championUid, equipmentId) {
      if (this.game.phase !== 'planning') {
        this.toastMsg = '只能在准备阶段卸下装备'
        setTimeout(() => this.toastMsg = '', 2000)
        return
      }
      const data = await this.api('POST', '/unequip', { championUid, equipmentId })
      if (data) {
        this.updateState(data)
        this.toastMsg = '装备已卸下'
        setTimeout(() => this.toastMsg = '', 1500)
      }
    },

    selectEquipmentFromInv(eqId) {
      if (this.selectedEquipmentId === eqId) {
        this.selectedEquipmentId = null
      } else {
        this.selectedEquipmentId = eqId
      }
      this.selectedBenchChamp = null
    },

    handleChampClick(row, col) {
      const champ = this.getPlayerAt(row, col)
      if (champ && this.selectedEquipmentId) {
        // 有选中装备，佩戴到该英雄
        this.doEquip(champ.uid, this.selectedEquipmentId)
        return
      }
      this.handleCellClick(row, col)
    },

    equipIcon(eq) {
      const icons = {
        weapon: { 'longsword': '🗡', 'stormsowrd': '⚔️', 'arpenblade': '🔪', 'arcanestaff': '🪄', 'ultstaff': '🔮', 'infinity': '💎' },
        armor: { 'chainmail': '🛡', 'magerobe': '👘', 'thornmail': '🦔', 'mrcloak': '🧥', 'guardplate': '💠', 'aegis': '🔰' },
        accessory: { 'boots': '👢', 'zerkerboots': '👟', 'sagestone': '💎', 'vampmask': '🎭', 'energyorb': '🔵', 'luckycoin': '🪙' }
      }
      const map = icons[eq.type] || {}
      return map[eq.id] || '📦'
    },

    typeLabel(type) {
      const map = { weapon: '武器', armor: '护甲', accessory: '辅助' }
      return map[type] || type
    },

    async doNextRound() {
      this.updateAfterBattle()
    },

    getPlayerAt(row, col) {
      if (!this.game.board) return null
      return this.game.board.find(c => c.row === row && c.col === col) || null
    },

    getEnemyAt(row, col) {
      if (!this.game.enemyBoard) return null
      return this.game.enemyBoard.find(c => c.row === row && c.col === col) || null
    },

    isTargeted(row, col) {
      return false // simplified
    },

    playerHpPct(c) {
      if (!c || c.maxHp <= 0) return 0
      return Math.max(0, c.hp / c.maxHp * 100)
    },

    enemyHpPct(c) {
      if (!c || c.maxHp <= 0) return 0
      return Math.max(0, c.hp / c.maxHp * 100)
    },

    enemySourceLabel(src) {
      switch (src) {
        case 'ghost': return '👤 幽灵阵容'
        case 'wild': return '🐗 野怪'
        case 'fallback': return '🃏 影子军团'
        default: return '🛡 敌方'
      }
    },

    isSynergyActive(name) {
      return this.activeSynergies.some(s => s.name === name)
    },

    heroesForSynergy(name) {
      const map = {
        '贵族': ['圣骑士', '神射手', '光明游侠', '龙骑士', '神圣天使'],
        '骑士团': ['圣骑士', '帝国将军', '冰霜骑士', '龙骑士', '帝国皇帝'],
        '游侠': ['神射手', '光明游侠', '冰霜射手'],
        '帝国': ['帝国将军', '暗影刺客', '剑圣', '帝国皇帝'],
        '野兽': ['狂战士', '九尾妖狐', '牛头人酋长', '冰霜巨龙'],
        '法师': ['九尾妖狐', '火焰巫师', '冰霜女巫', '深渊恶魔'],
        '极冰': ['冰霜射手', '冰霜骑士', '冰霜女巫', '冰霜巨龙'],
        '格斗家': ['狂战士', '虚空巨兽', '牛头人酋长'],
        '刺客': ['暗影刺客', '虚空行者'],
        '剑客': ['剑圣', '神圣天使'],
        '虚空': ['虚空巨兽', '虚空行者'],
        '恶魔': ['火焰巫师', '深渊恶魔']
      }
      return map[name] || []
    },

    showSynergyDetail(s) {
      this.synergyPanelOpen = true
      this.selectedSynergy = s.name
    }
  }
}
</script>

<style scoped>
* { box-sizing: border-box; }

.autochess {
  max-width: 820px;
  margin: 0 auto;
  padding: 10px;
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  background: #1a1a2e;
  min-height: 100vh;
  color: #e0e0e0;
  user-select: none;
}

.loading {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
}
.loading-text {
  font-size: 20px;
  color: #888;
  animation: pulse 1.5s infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 1; }
}

/* ===== 顶部信息栏 ===== */
.top-bar {
  display: flex;
  gap: 10px;
  padding: 10px 0;
  flex-wrap: wrap;
  justify-content: center;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #16213e;
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
}
.info-item.hp { color: #4caf50; }
.info-item.hp.low { color: #f44336; animation: blink 0.8s infinite; }
.info-item.gold { color: #ffc107; }
.info-item.level { color: #03a9f4; }
.info-item.round { color: #e0e0e0; }
.info-item.field { color: #9c27b0; }
.info-item.sid-display { color: #666; font-size: 10px; }
.info-item.sid-display .info-val { font-size: 10px; font-family: monospace; }
.info-item.sid-display .info-label { font-size: 9px; }
.info-item.user-display { color: #4ade80; }
.info-icon { font-size: 16px; }
.info-val { font-size: 18px; }
.info-label { font-size: 11px; opacity: 0.7; }
.info-sub { font-size: 11px; opacity: 0.6; }
.btn-xp {
  background: #03a9f4;
  color: #fff;
  border: none;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
  cursor: pointer;
  font-weight: 700;
}
.btn-xp:disabled { opacity: 0.4; cursor: not-allowed; }

@keyframes blink {
  50% { opacity: 0.5; }
}

/* ===== 羁绊栏 ===== */
.synergies-bar {
  display: flex;
  gap: 6px;
  padding: 6px 0;
  flex-wrap: wrap;
  justify-content: center;
}
.synergy-tag {
  background: #16213e;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  opacity: 0.5;
  border: 1px solid transparent;
  cursor: pointer;
}
.synergy-tag.active {
  opacity: 1;
  border-color: #ffc107;
  color: #ffc107;
  background: #1a1a2e;
}
.syn-count { font-weight: 700; }
.btn-syn-detail {
  background: #1a1a2e;
  border: 1px solid #333;
  color: #aaa;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  cursor: pointer;
}
.btn-syn-detail:hover { border-color: #ffc107; color: #ffc107; }

/* ===== 羁绊详情面板 ===== */
.synergy-panel {
  background: #0f0f23;
  border: 1px solid #2a2a4a;
  border-radius: 8px;
  padding: 10px;
  margin-bottom: 8px;
  max-height: 300px;
  overflow-y: auto;
}
.syn-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #ffc107;
  font-size: 14px;
  font-weight: 700;
  margin-bottom: 8px;
}
.btn-close-panel {
  background: none;
  border: none;
  color: #888;
  font-size: 16px;
  cursor: pointer;
}
.btn-close-panel:hover { color: #f44336; }
.syn-list { display: flex; flex-direction: column; gap: 4px; }
.syn-item {
  background: #16213e;
  border-radius: 6px;
  padding: 6px 10px;
  cursor: pointer;
  opacity: 0.6;
  transition: opacity 0.2s;
}
.syn-item.active { opacity: 1; border-left: 3px solid #ffc107; }
.syn-item.selected { opacity: 1; border-left: 3px solid #ffc107; }
.syn-item-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.syn-item-name { font-weight: 700; color: #e0e0e0; }
.syn-item-count { font-size: 12px; color: #888; }
.syn-item-badge { color: #4caf50; font-weight: 700; }
.syn-item-desc {
  margin-top: 6px;
  padding: 8px;
  background: #1a1a2e;
  border-radius: 4px;
  font-size: 12px;
  color: #ccc;
  line-height: 1.5;
}
.syn-item-desc p { margin: 4px 0; }
.syn-active-hint { color: #4caf50; }
.syn-inactive-hint { color: #ff9800; }
.syn-heroes { margin-top: 6px; display: flex; flex-wrap: wrap; gap: 4px; align-items: center; }
.syn-hero-label { font-size: 11px; color: #888; }
.syn-hero-tag {
  background: #0f3460;
  color: #64b5f6;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
}

/* ===== 战斗区域 ===== */
.battle-area {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin: 10px 0;
}
.board-label {
  font-size: 12px;
  color: #888;
  margin-bottom: 4px;
  text-align: center;
}
.board.enemy-board.dimmed { opacity: 0.4; }
.board.enemy-board.ghost { border-color: #7c3aed; }
.board.enemy-board.ghost .board-label { color: #a78bfa; }
.board.enemy-board.ghost .enemy-cell { background: rgba(124,58,237,0.06); }
.bf-label.enemy-label:has-text("👤") { color: #a78bfa; }
.board-grid {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.board-row {
  display: flex;
  gap: 3px;
  justify-content: center;
}
.cell {
  width: 52px;
  height: 56px;
  background: #0f3460;
  border-radius: 4px;
  border: 1px solid #1a1a4e;
  position: relative;
  transition: all 0.2s;
}
@media (max-width: 600px) {
  .cell { width: 40px; height: 44px; }
}
.enemy-cell {
  background: #2d1b3d;
}
.player-cell.highlighted {
  border-color: #4caf50;
  border-style: dashed;
  cursor: pointer;
}
.player-cell.highlighted:hover {
  background: #1a4a2e;
}
.cell.occupied {
  border-color: #3a3a6a;
}

/* ===== 英雄卡片 ===== */
.champ {
  width: 100%;
  height: 100%;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  position: relative;
  overflow: hidden;
  border: 2px solid #555;
}
.player-champ { cursor: pointer; }
.enemy-champ { cursor: default; opacity: 0.9; }
.tier1 { background: linear-gradient(180deg, #78909c, #546e7a); border-color: #90a4ae; }
.tier2 { background: linear-gradient(180deg, #66bb6a, #388e3c); border-color: #81c784; }
.tier3 { background: linear-gradient(180deg, #42a5f5, #1565c0); border-color: #64b5f6; }
.tier4 { background: linear-gradient(180deg, #ab47bc, #6a1b9a); border-color: #ce93d8; animation: glowPurple 2s infinite; }
.tier5 { background: linear-gradient(180deg, #ffd54f, #f57f17); border-color: #ffe082; animation: glowGold 2s infinite; }
.star1 { box-shadow: none; }
.star2 { box-shadow: 0 0 6px rgba(255,215,0,0.4); }
.star3 { box-shadow: 0 0 12px rgba(255,215,0,0.8); animation: glow 1.5s infinite; }

@keyframes glow {
  50% { box-shadow: 0 0 20px rgba(255,215,0,1); }
}
@keyframes glowPurple {
  50% { box-shadow: 0 0 16px rgba(171,71,188,0.8); }
}
@keyframes glowGold {
  50% { box-shadow: 0 0 16px rgba(255,213,79,1); }
}

.champ-name {
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  text-shadow: 0 1px 2px rgba(0,0,0,0.6);
  line-height: 1.1;
}
.champ-stars {
  color: #ffd700;
  font-size: 9px;
  line-height: 1;
}
.champ-hp {
  width: 80%;
  height: 3px;
  background: #333;
  border-radius: 2px;
  margin-top: 2px;
}
.hp-fill {
  display: block;
  height: 100%;
  background: #4caf50;
  border-radius: 2px;
  transition: width 0.3s;
}
.champ-atk {
  font-size: 8px;
  color: #ff9800;
  margin-top: 1px;
}
.champ-cost {
  font-size: 8px;
  color: #ffc107;
}
.champ-traits {
  font-size: 8px;
  color: #aaa;
  margin-top: 2px;
}

/* ===== 备战席 ===== */
.bench-section { margin: 10px 0; }
.section-label {
  font-size: 13px;
  color: #aaa;
  margin-bottom: 6px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.bench-slots {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.bench-slot {
  width: 68px;
  height: 68px;
  background: #16213e;
  border-radius: 6px;
  border: 2px solid #1a1a4e;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
}
.bench-slot:hover { border-color: #4a4a8a; }
.bench-slot.selected { border-color: #4caf50; box-shadow: 0 0 8px rgba(76,175,80,0.4); }
.bench-slot.filled { border-color: #3a3a6a; }
.bench-champ { cursor: pointer; }
.btn-sell {
  position: absolute;
  top: 1px;
  right: 1px;
  background: #f44336;
  color: #fff;
  border: none;
  border-radius: 2px;
  font-size: 8px;
  padding: 1px 3px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s;
}
.bench-champ:hover .btn-sell { opacity: 1; }

/* ===== 商店 ===== */
.shop-section { margin: 10px 0; }
.btn-refresh {
  background: #ffc107;
  color: #1a1a2e;
  border: none;
  border-radius: 5px;
  padding: 4px 12px;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}
.btn-refresh:disabled { opacity: 0.4; cursor: not-allowed; }
.shop-slots {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.shop-slot {
  flex: 1;
  min-width: 100px;
  height: 64px;
  background: #16213e;
  border-radius: 6px;
  border: 2px solid #1a1a4e;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.shop-slot.hoverable:hover {
  border-color: #ffc107;
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(255,193,7,0.2);
}
.shop-slot.empty { opacity: 0.4; cursor: default; }
.empty-text {
  font-size: 12px;
  color: #666;
}
.shop-champ {
  cursor: pointer;
  border-radius: 4px;
  padding: 4px;
}

/* ===== 操作按钮 ===== */
.action-bar {
  display: flex;
  gap: 10px;
  justify-content: center;
  padding: 12px 0;
}
.btn-action {
  padding: 10px 28px;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-fight {
  background: linear-gradient(135deg, #e53935, #c62828);
  color: #fff;
  box-shadow: 0 4px 16px rgba(229,57,53,0.4);
}
.btn-fight:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(229,57,53,0.6); }
.btn-next {
  background: #ff9800;
  color: #fff;
}
.btn-new, .btn-reset {
  background: #555;
  color: #fff;
  font-size: 13px;
  padding: 8px 18px;
}
.btn-action:disabled { opacity: 0.5; cursor: not-allowed; }

/* ===== 战斗日志 ===== */
.battle-log {
  background: #111;
  border-radius: 8px;
  padding: 10px 14px;
  margin-top: 10px;
  max-height: 150px;
  overflow-y: auto;
}
.log-title {
  font-size: 13px;
  color: #ffc107;
  margin-bottom: 4px;
}
.log-line {
  font-size: 11px;
  color: #999;
  line-height: 1.5;
}

/* ===== Toast ===== */
.toast {
  position: fixed;
  bottom: 30px;
  left: 50%;
  transform: translateX(-50%);
  background: #e53935;
  color: #fff;
  padding: 10px 24px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  z-index: 999;
  cursor: pointer;
  animation: fadeInUp 0.3s;
}
@keyframes fadeInUp {
  from { opacity: 0; transform: translateX(-50%) translateY(20px); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}

/* ===== 统一棋盘战场 ===== */
.battlefield {
  position: relative;
  width: 100%;
  background: linear-gradient(180deg, #1c0a0a 0%, #2a1010 30%, #3a1a0a 50%, #0a1a1c 70%, #0a0a1c 100%);
  border: 2px solid #e8b830;
  border-radius: 16px;
  padding: 0 8px 12px 8px;
  overflow: hidden;
  animation: bfFadeIn 0.5s;
}
@keyframes bfFadeIn { from { opacity: 0; transform: scaleY(0.92); } to { opacity: 1; transform: scaleY(1); } }

.bf-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 4px 4px 4px;
}
.bf-label {
  font-size: 11px; color: #8892b0; letter-spacing: 2px; text-transform: uppercase;
}
.bf-vs {
  font-size: 18px; font-weight: 700; color: #e8b830;
  text-shadow: 0 0 12px rgba(232, 184, 48, 0.5);
  letter-spacing: 3px;
}
.bf-vs.win { color: #4caf50; text-shadow: 0 0 16px rgba(76, 175, 80, 0.6); }
.bf-vs.lose { color: #f44336; text-shadow: 0 0 16px rgba(244, 67, 54, 0.6); }

.bf-grid {
  position: relative;
  display: flex; flex-direction: column; gap: 3px;
}

.bf-row {
  display: flex; gap: 3px; justify-content: center;
  padding: 1px 0;
  transition: background 0.3s;
}
.bf-center {
  border-top: 2px dashed rgba(232, 184, 48, 0.3);
  border-bottom: 2px dashed rgba(232, 184, 48, 0.3);
  min-height: 8px;
  margin: 4px 0;
}
.bf-center-cell {
  background: transparent !important; border: none !important; box-shadow: none !important;
}

.bf-cell {
  width: 52px; height: 56px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 6px;
  position: relative;
  transition: all 0.25s;
}
.bf-enemy-zone .bf-cell:not(.bf-center-cell) { background: rgba(200, 60, 60, 0.04); border-color: rgba(200, 60, 60, 0.1); }
.bf-player-zone .bf-cell:not(.bf-center-cell) { background: rgba(60, 120, 200, 0.04); border-color: rgba(60, 120, 200, 0.1); }
.bf-cell.has-unit { border-color: rgba(232, 184, 48, 0.25); }

/* 棋盘单位 */
.bf-unit {
  position: absolute; top: 2px; left: 2px; right: 2px; bottom: 2px;
  background: linear-gradient(180deg, #2a2a3e 0%, #1a1a2e 100%);
  border: 2px solid #5a5a7a;
  border-radius: 6px;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  overflow: hidden;
  transition: all 0.3s;
}
.bf-unit.dead {
  opacity: 0.5;
  filter: grayscale(1);
  transform: scale(0.85);
  border-color: #666;
  background: linear-gradient(180deg, #1a1a1a 0%, #0a0a0a 100%);
}
.bf-unit.dead .bf-unit-icon { font-size: 18px; opacity: 1; filter: none; }
.bf-unit.dead .bf-unit-name { text-decoration: line-through; color: #666; }
.bf-unit.dead .bf-unit-hp-outer { background: #333; }
.bf-unit.dead .bf-unit-hp-fill { background: #555; width: 0% !important; transition: width 0.5s; }
.bf-unit.flash-atk {
  border-color: #ffd700;
  box-shadow: 0 0 14px rgba(255, 215, 0, 0.7);
  animation: pulseGold 0.35s;
  z-index: 10;
}
.bf-unit.flash-hit {
  border-color: #ff4444;
  box-shadow: 0 0 12px rgba(255, 68, 68, 0.7);
  animation: shake 0.35s;
  z-index: 10;
}

/* 武器图标 */
.bf-unit-icon {
  font-size: 16px; line-height: 1;
}
/* 武器颜色区分 */
.bf-unit.weapon-bow .bf-unit-icon { filter: drop-shadow(0 0 3px #4caf50); }
.bf-unit.weapon-sword .bf-unit-icon { filter: drop-shadow(0 0 3px #ffd700); }
.bf-unit.weapon-axe .bf-unit-icon { filter: drop-shadow(0 0 3px #ff5722); }
.bf-unit.weapon-magic .bf-unit-icon { filter: drop-shadow(0 0 3px #e040fb); }
.bf-unit.weapon-dagger .bf-unit-icon { filter: drop-shadow(0 0 3px #00bcd4); }
.bf-unit.weapon-claw .bf-unit-icon { filter: drop-shadow(0 0 3px #ff9800); }

/* 单位边框 - 区分敌我 */
.bf-unit.player { border-color: #4a90d9; }
.bf-unit.enemy { border-color: #d94a4a; }

.bf-unit-name {
  font-size: 9px; font-weight: 600; color: #ddd;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  max-width: 100%;
}
.bf-unit-hp-outer {
  width: 85%; height: 4px; border-radius: 2px; background: #333;
  margin-top: 1px; overflow: hidden;
}
.bf-unit-hp-fill {
  height: 100%; border-radius: 2px;
  background: linear-gradient(90deg, #f44336, #4caf50);
  transition: width 0.35s;
}

/* 法力条 */
.bf-unit-mana-outer {
  width: 100%; height: 4px; background: #1a1a2e; border-radius: 2px; margin-top: 1px;
}
.bf-unit-mana-fill {
  height: 100%; border-radius: 2px;
  background: linear-gradient(90deg, #2196f3, #64b5f6);
  transition: width 0.35s;
  box-shadow: 0 0 3px rgba(33,150,243,0.5);
}

/* 技能释放闪光 */
.bf-unit.flash-skill {
  border-color: #00e5ff !important;
  box-shadow: 0 0 20px rgba(0, 229, 255, 0.9), 0 0 40px rgba(0, 229, 255, 0.4) !important;
  animation: pulseSkill 0.6s;
  z-index: 11;
}
@keyframes pulseSkill {
  0% { transform: scale(1); filter: brightness(1); }
  50% { transform: scale(1.2); filter: brightness(1.8); }
  100% { transform: scale(1); filter: brightness(1); }
}

/* 技能名称浮动文字 */
.bf-float-dmg.skill {
  font-size: 15px; font-weight: 900; color: #00e5ff;
  text-shadow: 0 0 10px rgba(0, 229, 255, 0.8), 0 0 20px rgba(0, 229, 255, 0.4);
  animation: skillFloat 2s ease-out forwards;
  pointer-events: none;
  white-space: nowrap;
}
.fd-skill-icon { margin-right: 4px; }
.fd-skill-name { letter-spacing: 2px; }

/* 技能伤害浮动文字 */
.bf-float-dmg.skillhit {
  font-size: 16px; font-weight: 900; color: #ffab00;
  text-shadow: 0 0 8px rgba(255, 171, 0, 0.8);
  animation: floatUp 1.5s ease-out forwards;
}
.fd-skill-hit { margin-right: 2px; }

@keyframes skillFloat {
  0% { opacity: 0; transform: translateY(0) scale(0.5); }
  20% { opacity: 1; transform: translateY(-20px) scale(1.2); }
  100% { opacity: 0; transform: translateY(-60px) scale(0.8); }
}

/* 快捷键 */
@keyframes pulseGold {
  0%, 100% { box-shadow: 0 0 8px rgba(255, 215, 0, 0.4); }
  50% { box-shadow: 0 0 22px rgba(255, 215, 0, 0.9); }
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-4px); }
  40% { transform: translateX(4px); }
  60% { transform: translateX(-3px); }
  80% { transform: translateX(2px); }
}

/* 投射物动画 */
.bf-projectile {
  position: fixed;
  z-index: 9999;
  pointer-events: none;
  animation: projFly 0.4s ease-out forwards;
}
.bf-debug {
  position: absolute;
  top: 4px; left: 4px;
  z-index: 100;
  background: rgba(0,0,0,0.6);
  color: #ffeb3b;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  pointer-events: none;
}
.proj-icon {
  font-size: 20px;
  display: block;
  filter: drop-shadow(0 0 6px rgba(255, 255, 255, 0.5));
}
/* 投射物武器特效 */
.wpn-bow .proj-icon { filter: drop-shadow(0 0 6px #4caf50); animation: projSpin 0.2s linear infinite; }
.wpn-sword .proj-icon { filter: drop-shadow(0 0 6px #ffd700); }
.wpn-axe .proj-icon { filter: drop-shadow(0 0 6px #ff5722); animation: projSpin 0.3s linear infinite; }
.wpn-magic .proj-icon { filter: drop-shadow(0 0 8px #e040fb); animation: projPulse 0.3s ease-in-out infinite; }
.wpn-dagger .proj-icon { filter: drop-shadow(0 0 6px #00bcd4); }
.wpn-claw .proj-icon { filter: drop-shadow(0 0 6px #ff9800); }

@keyframes projFly {
  0% { transform: translate(0, 0) scale(0.8); opacity: 0; }
  15% { transform: translate(0, 0) scale(1.2); opacity: 1; }
  100% { transform: translate(var(--dx), var(--dy)) scale(1.1); opacity: 1; }
}
@keyframes projSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes projPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.3); }
}

/* 浮动伤害(棋盘上) */
.bf-float-dmg {
  position: absolute;
  z-index: 60;
  font-size: 15px; font-weight: 900; color: #ff5252;
  text-shadow: 0 0 8px rgba(255, 82, 82, 0.6);
  pointer-events: none;
  white-space: nowrap;
  animation: bfFloatUp 1s ease-out forwards;
}
.bf-float-dmg.physical-dmg { color: #ff6d3a; text-shadow: 0 0 8px rgba(255,109,58,0.6); }
.bf-float-dmg.magic-dmg { color: #c084fc; text-shadow: 0 0 8px rgba(192,132,252,0.6); }
.bf-float-dmg.crit { font-size: 20px; color: #ff9100; text-shadow: 0 0 14px rgba(255,145,0,0.8); }
.bf-float-dmg.crit.magic-dmg { color: #e879f9; text-shadow: 0 0 14px rgba(232,121,249,0.8); }
.bf-float-dmg.double { font-size: 16px; color: #00e5ff; text-shadow: 0 0 10px rgba(0,229,255,0.7); }
.bf-float-dmg.double.magic-dmg { color: #a78bfa; text-shadow: 0 0 10px rgba(167,139,250,0.7); }
.fd-prefix { margin-right: 2px; }
.fd-icon { font-size: 10px; margin-right: 1px; opacity: 0.8; }

@keyframes bfFloatUp {
  0% { opacity: 1; transform: translate(-50%, 0); }
  30% { opacity: 1; transform: translate(-50%, -14px); }
  100% { opacity: 0; transform: translate(-50%, -36px); }
}

/* 结果栏 */
.bf-result-bar {
  text-align: center; padding: 12px 0 4px 0;
}
.btn-continue {
  padding: 10px 40px; font-size: 16px; font-weight: 700;
  background: linear-gradient(135deg, #e8b830, #f0c040);
  color: #1a1a2e; border: none; border-radius: 25px; cursor: pointer;
  box-shadow: 0 4px 20px rgba(232, 184, 48, 0.35);
  transition: all 0.25s; letter-spacing: 2px;
}
.btn-continue:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 28px rgba(232, 184, 48, 0.55);
}

/* 跳过按钮 */
.btn-skip {
  background: rgba(255,255,255,0.1); color: #aaa; border: 1px solid rgba(255,255,255,0.2);
}
.btn-skip:hover { background: rgba(255,255,255,0.2); color: #fff; }

/* ===== 移动端战场适配 ===== */
@media (max-width: 600px) {
  .battlefield {
    border-radius: 10px;
    padding: 0 4px 8px 4px;
  }
  .bf-header {
    padding: 4px 2px 2px 2px;
  }
  .bf-vs {
    font-size: 13px;
    letter-spacing: 1px;
  }
  .bf-label {
    font-size: 9px;
    letter-spacing: 1px;
  }
  .bf-grid { gap: 2px; }
  .bf-row { gap: 2px; padding: 0; }
  .bf-center { margin: 2px 0; min-height: 4px; }
  .bf-cell {
    width: 36px; height: 38px;
    border-radius: 4px;
  }
  .bf-unit {
    border-radius: 4px;
    top: 1px; left: 1px; right: 1px; bottom: 1px;
  }
  .bf-unit-icon { font-size: 12px; }
  .bf-unit-name { font-size: 7px; }
  .bf-unit-hp-outer { height: 3px; width: 90%; }
  .bf-result-bar { padding: 8px 0 2px 0; }
  .btn-continue {
    padding: 8px 24px; font-size: 13px;
    letter-spacing: 1px;
  }
  .bf-float-dmg { font-size: 11px; }
  .bf-float-dmg.crit { font-size: 15px; }
  .bf-float-dmg.double { font-size: 12px; }
  .bf-projectile .proj-icon { font-size: 14px; }
}

/* ===== 装备系统 ===== */

/* 装备背包 */
.equip-inv {
  background: linear-gradient(180deg, rgba(60,40,20,0.95), rgba(40,25,10,0.97));
  border: 2px solid rgba(220,180,80,0.6);
  border-radius: 14px;
  padding: 10px 14px;
  margin: 8px 0;
  box-shadow: 0 0 15px rgba(200,150,50,0.2);
}
.equip-inv-title {
  font-size: 14px;
  font-weight: 700;
  color: #f0c040;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.equip-inv-slots {
  display: flex; gap: 10px; flex-wrap: wrap;
}
.equip-inv-item {
  position: relative;
  padding: 8px 12px;
  border-radius: 10px;
  border: 2px solid transparent;
  cursor: pointer;
  min-width: 80px;
  text-align: center;
  transition: all 0.2s;
  background: rgba(255,255,255,0.06);
}
.equip-inv-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.3);
}
.equip-inv-item.selected {
  border-color: #f0c040 !important;
  box-shadow: 0 0 16px rgba(240,192,64,0.5);
  background: rgba(240,192,64,0.15);
}
.equip-inv-item.equip-weapon { border-color: rgba(220,100,80,0.5); }
.equip-inv-item.equip-armor { border-color: rgba(100,160,220,0.5); }
.equip-inv-item.equip-accessory { border-color: rgba(160,200,80,0.5); }
.equip-inv-item.tier2 { background: rgba(100,80,220,0.1); }
.equip-inv-item.tier3 { background: rgba(220,150,40,0.1); }
.equip-inv-icon { font-size: 22px; margin-bottom: 2px; }
.equip-inv-name { font-size: 11px; color: #ccc; font-weight: 600; }
.equip-inv-desc { font-size: 9px; color: #999; margin-top: 2px; }
.equip-hint {
  text-align: center; margin-top: 8px;
  color: #f0c040; font-size: 12px; font-weight: 600;
  animation: hintPulse 1.5s ease-in-out infinite;
}
@keyframes hintPulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}

/* 装备小图标（英雄身上） */
.champ-equip-slots {
  display: flex; gap: 3px;
  margin-top: 2px; justify-content: center;
}
.mini-equip {
  display: inline-flex; align-items: center; justify-content: center;
  width: 18px; height: 18px;
  font-size: 11px;
  border-radius: 50%;
  cursor: pointer;
  border: 1px solid rgba(255,255,255,0.25);
  transition: all 0.2s;
}
.mini-equip:hover {
  transform: scale(1.25);
  box-shadow: 0 0 8px rgba(255,200,100,0.5);
}
.mini-equip.equip-weapon { background: rgba(220,80,60,0.3); }
.mini-equip.equip-armor { background: rgba(60,120,200,0.3); }
.mini-equip.equip-accessory { background: rgba(100,180,60,0.3); }

/* 英雄高亮（有选中装备时） */
.player-champ.equip-target {
  cursor: pointer !important;
}
.player-cell.highlighted:hover {
  border-color: #f0c040 !important;
  box-shadow: 0 0 12px rgba(240,192,64,0.4);
  background: rgba(240,192,64,0.08);
}

/* 装备选择弹窗 */
.equip-choice-overlay {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.8);
  display: flex; align-items: center; justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.3s ease;
}
@keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
.equip-choice-modal {
  background: linear-gradient(135deg, #1a1a3e, #252560);
  border: 2px solid rgba(200,180,100,0.4);
  border-radius: 20px;
  padding: 24px;
  max-width: 620px; width: 90%;
  box-shadow: 0 20px 60px rgba(0,0,0,0.5);
}
.equip-choice-title {
  font-size: 22px; font-weight: 800;
  text-align: center;
  background: linear-gradient(135deg, #f0c040, #f8d060);
  -webkit-background-clip: text; -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: 4px;
}
.equip-choice-subtitle {
  text-align: center; color: #aaa; font-size: 13px; margin-bottom: 16px;
}
.equip-choice-list {
  display: flex; gap: 14px; justify-content: center; flex-wrap: wrap;
}
.equip-choice-card {
  flex: 1; min-width: 140px; max-width: 180px;
  padding: 16px 12px;
  border-radius: 14px;
  border: 2px solid rgba(255,255,255,0.15);
  cursor: pointer;
  text-align: center;
  transition: all 0.25s;
  background: rgba(255,255,255,0.05);
}
.equip-choice-card:hover {
  transform: translateY(-6px);
  border-color: #f0c040;
  box-shadow: 0 12px 32px rgba(240,192,64,0.3);
  background: rgba(255,255,255,0.1);
}
.equip-choice-card.equip-weapon { border-color: rgba(220,100,70,0.4); }
.equip-choice-card.equip-weapon:hover { border-color: #e07050; box-shadow: 0 12px 32px rgba(220,100,70,0.35); }
.equip-choice-card.equip-armor { border-color: rgba(70,140,220,0.4); }
.equip-choice-card.equip-armor:hover { border-color: #5090e0; box-shadow: 0 12px 32px rgba(70,140,220,0.35); }
.equip-choice-card.equip-accessory { border-color: rgba(120,200,70,0.4); }
.equip-choice-card.equip-accessory:hover { border-color: #80d050; box-shadow: 0 12px 32px rgba(120,200,70,0.35); }
.equip-choice-icon { font-size: 32px; margin-bottom: 6px; }
.equip-choice-name { font-size: 14px; color: #fff; font-weight: 700; margin-bottom: 4px; }
.equip-choice-type-badge {
  display: inline-block; padding: 2px 10px; border-radius: 10px;
  font-size: 10px; font-weight: 600; margin-bottom: 6px;
  background: rgba(255,255,255,0.1); color: #ccc;
}
.equip-choice-desc { font-size: 11px; color: #bbb; margin-bottom: 6px; line-height: 1.4; }
.equip-choice-stats {
  font-size: 10px; color: #aaa;
  display: flex; flex-wrap: wrap; gap: 3px; justify-content: center;
}
</style>
