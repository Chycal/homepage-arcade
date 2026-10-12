const API_BASE = '/api/auth'

/**
 * 获取当前 Access Token
 */
export function getAccessToken() {
  return localStorage.getItem('accessToken')
}

/**
 * 获取当前 Refresh Token
 */
export function getRefreshToken() {
  return localStorage.getItem('refreshToken')
}

/**
 * 是否已登录
 */
export function isLoggedIn() {
  return !!getAccessToken()
}

/**
 * 获取当前用户名
 */
export function getUsername() {
  return localStorage.getItem('username') || ''
}

/**
 * 获取当前角色
 */
export function getRole() {
  return localStorage.getItem('role') || ''
}

/**
 * 带自动刷新 Token 的 fetch 封装
 *
 * 注意：本项目的 Spring Security 未配置 AuthenticationEntryPoint，
 * 因此「未认证 / Token 过期」返回的是 403 而不是 401。
 * 这里必须把 403 一并视为「可能过期」，否则 Access Token（15 分钟）过期后
 * 永远不会触发刷新，接口会一直失败。
 */
export async function authFetch(url, options = {}) {
  const headers = options.headers || {}
  const token = getAccessToken()
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }
  options.headers = headers

  let res = await fetch(url, options)

  // 401/403 且存在 Refresh Token 时，尝试刷新一次
  if ((res.status === 401 || res.status === 403) && getRefreshToken()) {
    let refreshed = false
    try {
      const refreshRes = await fetch(API_BASE + '/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: getRefreshToken() })
      })

      if (refreshRes.ok) {
        const data = await refreshRes.json()
        if (data.success && data.accessToken) {
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          refreshed = true
        }
      }
    } catch (err) {
      console.warn('刷新 Token 失败', err)
    }

    if (refreshed) {
      // 用新 Token 重试（只重试一次，避免死循环）
      headers['Authorization'] = 'Bearer ' + getAccessToken()
      options.headers = headers
      res = await fetch(url, options)
    } else {
      // Refresh Token 也失效了，清除登录状态
      clearAuth()
    }
  }

  return res
}

/** 清除本地登录状态并广播 */
function clearAuth() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
  window.dispatchEvent(new CustomEvent('auth-change'))
}

/**
 * 登出
 */
export function logout() {
  clearAuth()
}
