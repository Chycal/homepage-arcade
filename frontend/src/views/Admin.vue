<template>
  <div class="admin-wrapper">
    <div class="admin-header">
      <h1>管理面板</h1>
      <p class="admin-subtitle">管理贪吃蛇排行榜，封禁作弊玩家</p>
    </div>

    <!-- 统计栏 -->
    <div class="stats-bar" v-if="stats">
      <div class="stat-item"><span class="stat-num">{{ stats.total }}</span> 总成绩</div>
      <div class="stat-item"><span class="stat-num">{{ stats.banned }}</span> 已封禁</div>
      <div class="stat-item"><span class="stat-num">{{ stats.clean }}</span> 有效成绩</div>
    </div>

    <!-- 操作反馈 -->
    <div class="toast" v-if="toastMsg" :class="toastType">{{ toastMsg }}</div>

    <div class="admin-content">
      <!-- 左侧：排行榜管理 -->
      <div class="section">
        <h2>排行榜记录</h2>
        <div class="table-wrap" v-if="scores.length">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>玩家</th>
                <th>分数</th>
                <th>用时</th>
                <th>开始时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(s, i) in scores" :key="s.id" :class="{ banned: bannedSet.has(s.playerName) }">
                <td>{{ i + 1 }}</td>
                <td>
                  <span class="player-name">{{ s.playerName }}</span>
                  <span v-if="bannedSet.has(s.playerName)" class="badge-banned">已封禁</span>
                </td>
                <td class="score-col">{{ s.score }}</td>
                <td>{{ formatDuration(s.durationSeconds) }}</td>
                <td class="time-col">{{ formatTime(s.createdAt) || formatTime(s.startTime) }}</td>
                <td class="action-col">
                  <button
                    v-if="!bannedSet.has(s.playerName)"
                    class="btn btn-ban"
                    @click="doBan(s.playerName)"
                    :disabled="banning === s.playerName"
                  >封禁</button>
                  <button
                    v-else
                    class="btn btn-unban"
                    @click="doUnban(s.playerName)"
                    :disabled="banning === s.playerName"
                  >解封</button>
                  <button class="btn btn-delete" @click="doDelete(s.id)" :disabled="deleting === s.id">删除</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty">暂无记录</div>
      </div>

      <!-- 右侧：封禁列表 -->
      <div class="section">
        <h2>封禁列表</h2>
        <div class="table-wrap" v-if="bannedPlayers.length">
          <table>
            <thead>
              <tr>
                <th>玩家</th>
                <th>封禁时间</th>
                <th>操作者</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="bp in bannedPlayers" :key="bp.id">
                <td>{{ bp.playerName }}</td>
                <td>{{ formatTime(bp.bannedAt) }}</td>
                <td>{{ bp.bannedBy || '-' }}</td>
                <td>
                  <button class="btn btn-unban" @click="doUnban(bp.playerName)" :disabled="banning === bp.playerName">解封</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="empty">暂无封禁</div>
      </div>
    </div>

    <div class="loading-mask" v-if="loading">加载中...</div>
  </div>
</template>

<script>
import { authFetch, isAdmin } from '@/utils/auth'

export default {
  name: 'AdminPanel',
  data() {
    return {
      scores: [],
      bannedPlayers: [],
      loading: false,
      banning: null,
      deleting: null,
      toastMsg: '',
      toastType: 'success'
    }
  },
  computed: {
    bannedSet() {
      return new Set(this.bannedPlayers.map(b => b.playerName))
    },
    stats() {
      if (!this.scores.length && !this.bannedPlayers.length) return null
      return {
        total: this.scores.length + this.bannedPlayers.length,
        banned: this.bannedPlayers.length,
        clean: this.scores.length
      }
    }
  },
  beforeRouteEnter(to, from, next) {
    if (!isAdmin()) {
      next('/')
    } else {
      next()
    }
  },
  created() {
    this.fetchAll()
  },
  methods: {
    async fetchAll() {
      this.loading = true
      try {
        const [scoresRes, bannedRes] = await Promise.all([
          authFetch('/api/admin/scores'),
          authFetch('/api/admin/banned')
        ])
        const scoresData = await scoresRes.json()
        const bannedData = await bannedRes.json()
        this.scores = scoresData.scores || []
        this.bannedPlayers = bannedData.banned || []
      } catch (e) {
        this.toast('加载失败: ' + e.message, 'error')
      } finally {
        this.loading = false
      }
    },

    async doBan(playerName) {
      if (!confirm(`确定要封禁玩家 "${playerName}" 吗？`)) return
      this.banning = playerName
      try {
        const res = await authFetch('/api/admin/ban', {
          method: 'POST',
          body: JSON.stringify({ playerName })
        })
        if (res.ok) {
          this.toast(`已封禁玩家: ${playerName}`)
          await this.fetchAll()
        } else {
          const data = await res.json()
          this.toast(data.error || '操作失败', 'error')
        }
      } catch (e) {
        this.toast('网络错误: ' + e.message, 'error')
      } finally {
        this.banning = null
      }
    },

    async doUnban(playerName) {
      if (!confirm(`确定要解封玩家 "${playerName}" 吗？`)) return
      this.banning = playerName
      try {
        const res = await authFetch('/api/admin/unban', {
          method: 'POST',
          body: JSON.stringify({ playerName })
        })
        if (res.ok) {
          this.toast(`已解封玩家: ${playerName}`)
          await this.fetchAll()
        } else {
          const data = await res.json()
          this.toast(data.error || '操作失败', 'error')
        }
      } catch (e) {
        this.toast('网络错误: ' + e.message, 'error')
      } finally {
        this.banning = null
      }
    },

    async doDelete(id) {
      if (!confirm(`确定要删除分数记录 #${id} 吗？`)) return
      this.deleting = id
      try {
        const res = await authFetch(`/api/admin/scores/${id}`, { method: 'DELETE' })
        if (res.ok) {
          this.scores = this.scores.filter(s => s.id !== id)
          this.toast('已删除记录')
        } else {
          const data = await res.json()
          this.toast(data.error || '操作失败', 'error')
        }
      } catch (e) {
        this.toast('网络错误: ' + e.message, 'error')
      } finally {
        this.deleting = null
      }
    },

    formatDuration(seconds) {
      if (seconds == null || seconds < 0) return '-'
      const m = Math.floor(seconds / 60)
      const s = seconds % 60
      return m > 0 ? `${m}分${s}秒` : `${s}秒`
    },

    formatTime(t) {
      if (!t) return '-'
      const d = new Date(t)
      const pad = n => String(n).padStart(2, '0')
      return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },

    toast(msg, type = 'success') {
      this.toastMsg = msg
      this.toastType = type
      setTimeout(() => { this.toastMsg = '' }, 3000)
    }
  }
}
</script>

<style scoped>
.admin-wrapper {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 16px;
  min-height: calc(100vh - 56px - 48px);
}

.admin-header {
  text-align: center;
  margin-bottom: 24px;
}

.admin-header h1 {
  margin: 0;
  font-size: 28px;
  color: #333;
}

.admin-subtitle {
  color: #999;
  margin: 8px 0 0;
  font-size: 14px;
}

.stats-bar {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-bottom: 24px;
  padding: 16px;
  background: #f8f9ff;
  border-radius: 12px;
}

.stat-item {
  font-size: 14px;
  color: #666;
}

.stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #667eea;
  margin-right: 4px;
}

.toast {
  text-align: center;
  padding: 10px 20px;
  border-radius: 8px;
  margin-bottom: 16px;
  font-size: 14px;
  animation: fadeIn 0.3s;
}

.toast.success {
  background: #e8f5e9;
  color: #2e7d32;
}

.toast.error {
  background: #ffebee;
  color: #c62828;
}

.admin-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

@media (max-width: 800px) {
  .admin-content {
    grid-template-columns: 1fr;
  }
}

.section {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
}

.section h2 {
  margin: 0 0 16px;
  font-size: 18px;
  color: #444;
  padding-bottom: 10px;
  border-bottom: 2px solid #667eea22;
}

.table-wrap {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

th, td {
  padding: 10px 8px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

th {
  color: #999;
  font-weight: 600;
  white-space: nowrap;
}

.score-col {
  font-weight: 700;
  color: #667eea;
}

.time-col {
  font-size: 12px;
  color: #888;
}

.banned td {
  background: #fff5f5;
}

.player-name {
  font-weight: 500;
}

.badge-banned {
  display: inline-block;
  font-size: 11px;
  background: #ff5252;
  color: #fff;
  border-radius: 4px;
  padding: 1px 6px;
  margin-left: 6px;
  vertical-align: middle;
}

.action-col {
  display: flex;
  gap: 4px;
  flex-wrap: nowrap;
}

.btn {
  border: none;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.2s;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-ban {
  background: #ff5252;
  color: #fff;
}

.btn-ban:hover:not(:disabled) {
  background: #e04848;
}

.btn-unban {
  background: #4caf50;
  color: #fff;
}

.btn-unban:hover:not(:disabled) {
  background: #43a047;
}

.btn-delete {
  background: #f0f0f0;
  color: #666;
}

.btn-delete:hover:not(:disabled) {
  background: #e0e0e0;
}

.empty {
  text-align: center;
  padding: 32px;
  color: #ccc;
  font-size: 14px;
}

.loading-mask {
  position: fixed;
  inset: 0;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  color: #667eea;
  z-index: 200;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(-4px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
