<template>
  <div class="home">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="hero-content">
        <div class="avatar">{{ initials }}</div>
        <h1>{{ profile.name }}</h1>
        <p class="title">{{ profile.title }}</p>
        <p class="bio">{{ profile.bio }}</p>
        <div class="contact-links">
          <router-link to="/snake" class="btn-contact btn-game">
            🎮 贪吃蛇
          </router-link>
          <router-link to="/gomoku" class="btn-contact btn-game">
            ♟ 五子棋
          </router-link>
          <router-link to="/game24" class="btn-contact btn-game">
            🃏 24点
          </router-link>
          <router-link to="/autochess" class="btn-contact btn-game">
            ♟ 自走棋
          </router-link>
          <router-link to="/idle-life" class="btn-contact btn-game">
            ⚔️ 挂机生活
          </router-link>
          <router-link v-if="isAdmin" to="/admin" class="btn-contact btn-admin">
            ⚙ 管理
          </router-link>
          <a v-if="profile.email" :href="'mailto:' + profile.email" class="btn-contact">
            📧 Email
          </a>
          <a v-if="profile.github" :href="profile.github" target="_blank" class="btn-contact">
            💻 GitHub
          </a>
        </div>
      </div>
    </section>

    <!-- 技能区域 -->
    <section class="section skills-section">
      <h2 class="section-title">🚀 技术栈</h2>
      <div class="skill-categories">
        <div class="skill-category" v-if="skills.languages">
          <h3>编程语言</h3>
          <div class="skill-tags">
            <span v-for="item in skills.languages" :key="item" class="skill-tag">{{ item }}</span>
          </div>
        </div>
        <div class="skill-category" v-if="skills.frameworks">
          <h3>框架 & 框架</h3>
          <div class="skill-tags">
            <span v-for="item in skills.frameworks" :key="item" class="skill-tag">{{ item }}</span>
          </div>
        </div>
        <div class="skill-category" v-if="skills.tools">
          <h3>工具</h3>
          <div class="skill-tags">
            <span v-for="item in skills.tools" :key="item" class="skill-tag">{{ item }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- 项目区域 -->
    <section class="section projects-section">
      <h2 class="section-title">📁 项目</h2>
      <div class="project-grid">
        <div v-for="(project, idx) in projectList" :key="idx" class="project-card">
          <h3>{{ project.name }}</h3>
          <p>{{ project.desc }}</p>
          <span class="project-tech">{{ project.tech }}</span>
        </div>
      </div>
    </section>

    <!-- 服务器状态 -->
    <section class="section status-section">
      <h2 class="section-title">🟢 服务状态</h2>
      <div class="status-card" :class="{ 'status-ok': serverOk, 'status-err': !serverOk }">
        <span class="status-dot"></span>
        <span>{{ serverOk ? '服务运行正常' : '服务连接失败' }}</span>
        <span class="status-time" v-if="serverTime">{{ serverTime }}</span>
      </div>
    </section>
  </div>
</template>

<script>
import { isAdmin } from '@/utils/auth'

export default {
  name: 'HomeView',
  data() {
    return {
      profile: {},
      skills: {},
      projectList: [],
      serverOk: false,
      serverTime: ''
    }
  },
  computed: {
    initials() {
      const name = this.profile.name || '?'
      return name.charAt(0).toUpperCase()
    },
    isAdmin() {
      return isAdmin()
    }
  },
  mounted() {
    this.fetchData()
  },
  methods: {
    async fetchData() {
      try {
        // 并行请求所有接口
        const [profileRes, skillsRes, projectsRes, healthRes] = await Promise.all([
          fetch('/api/profile'),
          fetch('/api/skills'),
          fetch('/api/projects'),
          fetch('/api/health')
        ])

        if (profileRes.ok) this.profile = await profileRes.json()
        if (skillsRes.ok) this.skills = await skillsRes.json()
        if (projectsRes.ok) {
          const data = await projectsRes.json()
          this.projectList = data.projects || []
        }
        if (healthRes.ok) {
          const data = await healthRes.json()
          this.serverOk = data.status === 'ok'
          this.serverTime = data.timestamp
        }
      } catch (err) {
        console.error('API 请求失败:', err)
        this.serverOk = false
      }
    }
  }
}
</script>

<style scoped>
.hero {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  text-align: center;
  padding: 60px 16px 48px;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34px;
  font-weight: 700;
  margin: 0 auto 16px;
  border: 3px solid rgba(255, 255, 255, 0.4);
}

.hero h1 {
  font-size: 28px;
  margin: 0 0 6px;
}

.title {
  font-size: 15px;
  opacity: 0.9;
  margin: 0 0 12px;
}

.bio {
  max-width: 500px;
  margin: 0 auto 20px;
  font-size: 14px;
  opacity: 0.85;
  line-height: 1.6;
  padding: 0 8px;
}

.contact-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  padding: 0 8px;
}

.btn-contact {
  display: inline-block;
  padding: 8px 18px;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  text-decoration: none;
  border-radius: 20px;
  font-size: 13px;
  transition: background 0.3s;
  border: 1px solid rgba(255, 255, 255, 0.3);
  white-space: nowrap;
}

.btn-contact:hover {
  background: rgba(255, 255, 255, 0.3);
}

.btn-game {
  background: rgba(255, 255, 255, 0.25);
  border-color: rgba(255, 255, 255, 0.5);
  font-weight: 600;
  animation: pulse-btn 2s infinite;
}

.btn-admin {
  background: rgba(255, 215, 0, 0.3);
  border-color: rgba(255, 215, 0, 0.6);
  font-weight: 600;
  color: #ffd54f;
}

.btn-admin:hover {
  background: rgba(255, 215, 0, 0.5);
}

@keyframes pulse-btn {
  0%, 100% { box-shadow: 0 0 0 0 rgba(255,255,255,0.3); }
  50% { box-shadow: 0 0 0 8px rgba(255,255,255,0); }
}

@media (min-width: 641px) {
  .hero {
    padding: 80px 24px 60px;
  }
  .avatar {
    width: 96px;
    height: 96px;
    font-size: 40px;
    margin-bottom: 20px;
  }
  .hero h1 {
    font-size: 36px;
  }
  .title {
    font-size: 18px;
    margin-bottom: 16px;
  }
  .bio {
    font-size: 15px;
    margin-bottom: 24px;
  }
  .btn-contact {
    padding: 10px 24px;
    font-size: 14px;
  }
}

.section {
  max-width: 900px;
  margin: 0 auto;
  padding: 40px 16px;
}

.section-title {
  font-size: 22px;
  color: #333;
  text-align: center;
  margin: 0 0 24px;
}

.skill-categories {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20px;
}

@media (min-width: 641px) {
  .section {
    padding: 48px 24px;
  }
  .section-title {
    font-size: 24px;
    margin-bottom: 32px;
  }
  .skill-categories {
    grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
    gap: 24px;
  }
}

.skill-category h3 {
  font-size: 16px;
  color: #555;
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #667eea;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.skill-tag {
  display: inline-block;
  padding: 5px 14px;
  background: #eef0ff;
  color: #667eea;
  border-radius: 16px;
  font-size: 13px;
  font-weight: 500;
}

.project-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16px;
}

.project-card {
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: transform 0.3s, box-shadow 0.3s;
}

@media (min-width: 641px) {
  .project-grid {
    grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
    gap: 20px;
  }
  .project-card {
    padding: 24px;
  }
}

.project-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.project-card h3 {
  margin: 0 0 8px;
  font-size: 18px;
  color: #333;
}

.project-card p {
  color: #666;
  font-size: 14px;
  margin: 0 0 12px;
  line-height: 1.6;
}

.project-tech {
  display: inline-block;
  padding: 3px 10px;
  background: #f0f0f0;
  border-radius: 10px;
  font-size: 12px;
  color: #888;
}

.status-section {
  padding-bottom: 60px;
}

.status-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 24px;
  border-radius: 10px;
  font-size: 15px;
}

.status-ok {
  background: #f0faf0;
  color: #2e7d32;
}

.status-err {
  background: #fff0f0;
  color: #c62828;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: currentColor;
}

.status-time {
  margin-left: auto;
  font-size: 13px;
  opacity: 0.7;
}
</style>
