import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import { isLoggedIn } from '@/utils/auth'

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
    path: '/gomoku',
    name: 'Gomoku',
    component: () => import('@/views/Gomoku.vue'),
    meta: { title: '五子棋 - chycal' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫：认证检查
router.beforeEach((to, from, next) => {
  document.title = to.meta.title || '个人首页'

  // 已登录用户访问登录页，重定向到首页
  if (to.meta.guest && isLoggedIn()) {
    next({ name: 'Home' })
    return
  }

  next()
})

export default router
