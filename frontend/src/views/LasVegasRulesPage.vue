<template>
  <div class="rlp-page">
    <header class="rlp-header">
      <div>
        <h1>📖 拉斯维加斯 · 游戏规则</h1>
        <p class="rlp-sub">Las Vegas · 2-5 人 · 共 4 轮 · 钞票最多者胜</p>
      </div>
      <router-link to="/las-vegas" class="rlp-btn">🎲 去对局</router-link>
    </header>

    <LasVegasRules />
  </div>
</template>

<script>
import LasVegasRules from '@/components/LasVegasRules.vue'

/**
 * 独立的规则页，路由 /las-vegas/rules。
 *
 * 与对局页里的「规则」Tab 复用同一个 LasVegasRules 组件，本页只负责套一层
 * 带标题和「去对局」按钮的外壳。保留独立路由是为了能直接把规则链接分享给别人
 * （不用先进房间），所以这里不是重复实现，不要删掉其中一个。
 */
export default {
  name: 'LasVegasRulesPage',
  components: { LasVegasRules }
}
</script>

<style scoped>
.rlp-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 20px 16px 60px;
  background: #f7f5f0;
  min-height: 100vh;
}

.rlp-header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
  border-radius: 14px;
  padding: 18px 20px;
  margin-bottom: 14px;
}

.rlp-header h1 {
  font-size: 22px;
  margin: 0;
}

.rlp-sub {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 4px;
}

.rlp-btn {
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.5);
  color: #fff;
  text-decoration: none;
  border-radius: 8px;
  padding: 8px 16px;
  font-size: 13px;
  white-space: nowrap;
  transition: background 0.3s;
}

.rlp-btn:hover {
  background: rgba(255, 255, 255, 0.3);
}
</style>
