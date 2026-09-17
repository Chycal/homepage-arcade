<template>
  <div id="app">
    <nav class="navbar">
      <div class="nav-container">
        <router-link to="/" class="nav-brand">🏠 chycal</router-link>
        <button class="nav-toggle" @click="menuOpen = !menuOpen" aria-label="菜单">
          <span :class="{ bar: true, open: menuOpen }"></span>
          <span :class="{ bar: true, open: menuOpen }"></span>
          <span :class="{ bar: true, open: menuOpen }"></span>
        </button>
        <div class="nav-links" :class="{ open: menuOpen }">
          <router-link to="/" @click="menuOpen = false">首页</router-link>
          <router-link to="/snake" @click="menuOpen = false">🐍 贪吃蛇</router-link>
          <router-link to="/gomoku" @click="menuOpen = false">♟ 五子棋</router-link>
          <router-link to="/game24" @click="menuOpen = false">🃏 24点</router-link>
          <router-link to="/autochess" @click="menuOpen = false">♟ 自走棋</router-link>
          <router-link to="/idle-life" @click="menuOpen = false">⚔️ 挂机生活</router-link>
          <template v-if="loggedIn">
            <router-link v-if="admin" to="/admin" @click="menuOpen = false" class="nav-admin">管理</router-link>
            <span class="nav-user">{{ username }}</span>
            <a href="#" class="nav-logout" @click.prevent="doLogout">退出</a>
          </template>
          <router-link v-else to="/login" @click="menuOpen = false">登录</router-link>
        </div>
      </div>
    </nav>
    <main class="main-content">
      <router-view />
    </main>
    <footer class="footer">
      <p>&copy; {{ currentYear }} chycal. Powered by Spring Boot &amp; Vue 3.</p>
      <p class="visitor-counter" v-if="visitorStats">
        <span title="总访问量">👀 {{ visitorStats.totalViews }}</span>
        <span class="sep">|</span>
        <span title="独立访客">👤 {{ visitorStats.uniqueVisitors }}</span>
        <span class="sep">|</span>
        <span title="今日访问">📅 {{ visitorStats.todayVisitors }}</span>
      </p>
    </footer>
  </div>
</template>

<script>
import { isLoggedIn, getUsername, isAdmin, logout } from '@/utils/auth'

const VISITED_KEY = 'chycal_visited'

export default {
  name: 'App',
  data() {
    return {
      visitorStats: null,
      menuOpen: false,
      loggedIn: isLoggedIn(),
      username: getUsername(),
      admin: isAdmin()
    }
  },
  computed: {
    currentYear() {
      return new Date().getFullYear()
    }
  },
  async mounted() {
    window.addEventListener('auth-change', this.onAuthChange)
    const hasVisited = sessionStorage.getItem(VISITED_KEY)
    try {
      if (!hasVisited) {
        const res = await fetch('/api/visitor/ping', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ page: window.location.pathname })
        })
        if (res.ok) {
          this.visitorStats = await res.json()
          sessionStorage.setItem(VISITED_KEY, '1')
        }
      } else {
        const res = await fetch('/api/visitor/count')
        if (res.ok) {
          this.visitorStats = await res.json()
        }
      }
    } catch (e) {
      console.warn('访问统计加载失败:', e.message)
    }
  },
  beforeUnmount() {
    window.removeEventListener('auth-change', this.onAuthChange)
  },
  methods: {
    onAuthChange() {
      this.loggedIn = isLoggedIn()
      this.username = getUsername()
      this.admin = isAdmin()
    },
    doLogout() {
      logout()
      this.$router.push('/')
      this.menuOpen = false
    }
  }
}
</script>

<style scoped>
.navbar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  padding: 0 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-container {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  height: 56px;
  position: relative;
}

.nav-brand {
  color: #fff;
  text-decoration: none;
  font-size: 18px;
  font-weight: 700;
  white-space: nowrap;
  flex-shrink: 0;
}

/* 汉堡菜单按钮 */
.nav-toggle {
  display: none;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 5px;
  width: 36px;
  height: 36px;
  background: none;
  border: none;
  cursor: pointer;
  padding: 6px;
  z-index: 101;
}

.nav-toggle .bar {
  display: block;
  width: 22px;
  height: 2px;
  background: #fff;
  border-radius: 2px;
  transition: transform 0.3s, opacity 0.3s;
}

.nav-toggle .bar.open:nth-child(1) {
  transform: translateY(7px) rotate(45deg);
}

.nav-toggle .bar.open:nth-child(2) {
  opacity: 0;
}

.nav-toggle .bar.open:nth-child(3) {
  transform: translateY(-7px) rotate(-45deg);
}

.nav-links {
  display: flex;
  align-items: center;
  gap: 4px;
}

.nav-links a {
  color: rgba(255, 255, 255, 0.9);
  text-decoration: none;
  padding: 6px 12px;
  font-size: 14px;
  border-radius: 6px;
  transition: background 0.3s, color 0.3s;
  white-space: nowrap;
}

.nav-links a:hover {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
}

.nav-links a.router-link-exact-active {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}

.nav-user {
  color: rgba(255, 255, 255, 0.75);
  font-size: 14px;
  padding: 6px 8px;
  border-left: 1px solid rgba(255, 255, 255, 0.2);
  margin-left: 8px;
}

.nav-admin {
  color: #ffd54f !important;
  text-decoration: none;
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 6px;
  transition: background 0.3s, color 0.3s;
  font-weight: 600;
}

.nav-admin:hover {
  background: rgba(255, 215, 0, 0.2) !important;
  color: #ffeb3b !important;
}

.nav-logout {
  color: rgba(255, 255, 255, 0.7);
  text-decoration: none;
  padding: 6px 12px;
  font-size: 13px;
  border-radius: 6px;
  transition: background 0.3s, color 0.3s;
  cursor: pointer;
}

.nav-logout:hover {
  background: rgba(255, 80, 80, 0.3);
  color: #fff;
}

/* 移动端适配 */
@media (max-width: 640px) {
  .nav-toggle {
    display: flex;
  }

  .nav-links {
    display: none;
    position: absolute;
    top: 56px;
    left: 0;
    right: 0;
    flex-direction: column;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    padding: 8px 16px 16px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    border-radius: 0 0 12px 12px;
    gap: 2px;
  }

  .nav-links.open {
    display: flex;
  }

  .nav-links a {
    width: 100%;
    padding: 10px 12px;
    font-size: 15px;
  }
}

.main-content {
  min-height: calc(100vh - 56px - 48px);
}

.footer {
  text-align: center;
  padding: 14px 16px;
  background: #f5f5f5;
  color: #888;
  font-size: 12px;
}

.visitor-counter {
  margin-top: 6px;
  font-size: 12px;
  color: #aaa;
}

.visitor-counter .sep {
  margin: 0 8px;
  color: #ddd;
}
</style>
