<template>
  <div class="auth-wrapper">
    <div class="auth-card">
      <h2 class="auth-title">{{ isLogin ? '登录' : '注册' }}</h2>

      <div class="auth-form">
        <div class="form-group">
          <label>用户名</label>
          <input v-model="username" type="text" placeholder="请输入用户名" @keyup.enter="submit"
            :disabled="loading" autocomplete="username" />
        </div>

        <div class="form-group">
          <label>密码</label>
          <input v-model="password" type="password" placeholder="请输入密码" @keyup.enter="submit"
            :disabled="loading" autocomplete="current-password" />
        </div>

        <div v-if="!isLogin" class="form-group">
          <label>确认密码</label>
          <input v-model="confirmPassword" type="password" placeholder="请再次输入密码" @keyup.enter="submit"
            :disabled="loading" autocomplete="new-password" />
        </div>

        <div v-if="errorMsg" class="auth-error">{{ errorMsg }}</div>

        <button class="auth-btn" @click="submit" :disabled="loading">
          {{ loading ? '处理中...' : (isLogin ? '登录' : '注册') }}
        </button>

        <p class="auth-switch">
          {{ isLogin ? '还没有账号？' : '已有账号？' }}
          <a href="#" @click.prevent="toggleMode">{{ isLogin ? '立即注册' : '去登录' }}</a>
        </p>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AuthPage',
  data() {
    return {
      isLogin: true,
      username: '',
      password: '',
      confirmPassword: '',
      errorMsg: '',
      loading: false
    }
  },
  methods: {
    toggleMode() {
      this.isLogin = !this.isLogin
      this.errorMsg = ''
      this.username = ''
      this.password = ''
      this.confirmPassword = ''
    },
    async submit() {
      this.errorMsg = ''
      if (!this.username.trim()) {
        this.errorMsg = '请输入用户名'
        return
      }
      if (!this.password) {
        this.errorMsg = '请输入密码'
        return
      }
      if (!this.isLogin && this.password !== this.confirmPassword) {
        this.errorMsg = '两次输入的密码不一致'
        return
      }

      this.loading = true
      try {
        const url = this.isLogin ? '/api/auth/login' : '/api/auth/register'
        const res = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ username: this.username.trim(), password: this.password })
        })
        const data = await res.json()

        if (res.ok && data.success) {
          // 保存 Token
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          localStorage.setItem('username', data.username)
          localStorage.setItem('role', data.role)
          // 触发全局状态更新
          window.dispatchEvent(new CustomEvent('auth-change'))
          // 跳转首页
          this.$router.push('/')
        } else {
          this.errorMsg = data.message || '操作失败'
        }
      } catch (e) {
        this.errorMsg = '网络错误，请稍后再试'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.auth-wrapper {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: 40px 16px;
  background: linear-gradient(135deg, #667eea33 0%, #764ba233 100%);
}

.auth-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);
  padding: 40px 32px;
  width: 100%;
  max-width: 400px;
}

.auth-title {
  text-align: center;
  margin: 0 0 32px;
  font-size: 24px;
  color: #333;
}

.form-group {
  margin-bottom: 20px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.form-group input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid #ddd;
  border-radius: 8px;
  font-size: 15px;
  transition: border-color 0.3s;
  box-sizing: border-box;
}

.form-group input:focus {
  outline: none;
  border-color: #667eea;
  box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
}

.form-group input:disabled {
  background: #f5f5f5;
  cursor: not-allowed;
}

.auth-error {
  background: #fff2f0;
  border: 1px solid #ffccc7;
  color: #cf1322;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 16px;
}

.auth-btn {
  width: 100%;
  padding: 12px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.3s;
}

.auth-btn:hover {
  opacity: 0.9;
}

.auth-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.auth-switch {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #999;
}

.auth-switch a {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
}

.auth-switch a:hover {
  text-decoration: underline;
}
</style>
