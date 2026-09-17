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
 * 是否管理员
 */
export function isAdmin() {
  return getRole() === 'ADMIN'
}

/**
 * 带自动刷新 Token 的 fetch 封装
 */
export async function authFetch(url, options = {}) {
  const headers = options.headers || {}
  const token = getAccessToken()
  if (token) {
    headers['Authorization'] = 'Bearer ' + token
  }
  options.headers = headers

  let res = await fetch(url, options)

  // 如果返回 401，尝试刷新 Token
  if (res.status === 401 && getRefreshToken()) {
    const refreshRes = await fetch(API_BASE + '/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: getRefreshToken() })
    })

    if (refreshRes.ok) {
      const data = await refreshRes.json()
      if (data.success) {
        localStorage.setItem('accessToken', data.accessToken)
        localStorage.setItem('refreshToken', data.refreshToken)
        // 用新 Token 重试
        headers['Authorization'] = 'Bearer ' + data.accessToken
        options.headers = headers
        res = await fetch(url, options)
      }
    } else {
      // Refresh Token 也失效了，清除登录状态
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
      window.dispatchEvent(new CustomEvent('auth-change'))
    }
  }

  return res
}

/**
 * 登出
 */
export function logout() {
  localStorage.removeItem('accessToken')
  localStorage.removeItem('refreshToken')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
  window.dispatchEvent(new CustomEvent('auth-change'))
}
