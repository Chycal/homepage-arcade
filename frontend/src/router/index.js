import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import { isLoggedIn, isAdmin } from '@/utils/auth'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home,
    meta: { title: 'chycal 的个人首页' }
  },
  {
    path: '/login',
    name: 'Auth',
    component: () => import('@/views/Auth.vue'),
    meta: { title: '登录 - chycal', guest: true }
  },
  {
    path: '/snake',
    name: 'Snake',
    component: () => import('@/views/Snake.vue'),
    meta: { title: '贪吃蛇 - chycal' }
  },
  {
    path: '/gomoku',
    name: 'Gomoku',
    component: () => import('@/views/Gomoku.vue'),
    meta: { title: '五子棋 - chycal' }
  },
  {
    path: '/game24',
    name: 'Game24',
    component: () => import('@/views/TwentyFour.vue'),
    meta: { title: '24点 - chycal' }
  },
  {
    path: '/autochess',
    name: 'AutoChess',
    component: () => import('@/views/AutoChess.vue'),
    meta: { title: '自走棋 - chycal' }
  },
  {
    path: '/idle-life',
    name: 'IdleLife',
    component: () => import('@/views/IdleLife.vue'),
    meta: { title: '挂机生活 - chycal' }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    meta: { title: '管理面板 - chycal', requiresAuth: true, requiresAdmin: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：认证检查
router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '个人首页'

  // 需要登录的页面
  if (to.meta.requiresAuth && !isLoggedIn()) {
    next({ name: 'Auth', query: { redirect: to.fullPath } })
    return
  }

  // 需要管理员权限的页面
  if (to.meta.requiresAdmin && !isAdmin()) {
    next({ name: 'Home' })
    return
  }

  // 已登录用户访问登录页，重定向到首页
  if (to.meta.guest && isLoggedIn()) {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router
