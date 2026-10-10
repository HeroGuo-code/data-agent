<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
// 响应式跟随当前路由：刷新时初始导航未完成（path 为 /），若用一次性取值菜单会错误高亮首页
const activeMenu = computed(() => route.path)
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="layout__aside">
      <div class="layout__brand">
        <span class="layout__logo">智</span>
        <span class="layout__brand-text">电商经营数据智能问答</span>
      </div>
      <el-menu :default-active="activeMenu" router class="layout__menu">
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon><span>首页</span>
        </el-menu-item>
        <el-menu-item index="/chat">
          <el-icon><ChatDotRound /></el-icon><span>数据问答</span>
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><Tickets /></el-icon><span>订单管理</span>
        </el-menu-item>
        <el-menu-item index="/products">
          <el-icon><Goods /></el-icon><span>商品管理</span>
        </el-menu-item>
        <el-menu-item index="/shops">
          <el-icon><OfficeBuilding /></el-icon><span>店铺管理</span>
        </el-menu-item>
        <el-menu-item index="/inventory">
          <el-icon><Box /></el-icon><span>库存管理</span>
        </el-menu-item>
        <el-menu-item index="/refunds">
          <el-icon><RefreshLeft /></el-icon><span>退款管理</span>
        </el-menu-item>
        <el-menu-item index="/debug">
          <el-icon><Search /></el-icon><span>调试管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon><span>用户管理</span>
        </el-menu-item>
        <el-sub-menu index="asset-group">
          <template #title>
            <el-icon><DataBoard /></el-icon><span>数据资产中心</span>
          </template>
          <el-menu-item index="/asset">
            <el-icon><DataAnalysis /></el-icon><span>资产总览</span>
          </el-menu-item>
          <el-menu-item index="/asset/business">
            <el-icon><Files /></el-icon><span>业务表</span>
          </el-menu-item>
          <el-menu-item index="/asset/schema">
            <el-icon><Grid /></el-icon><span>Schema 字段</span>
          </el-menu-item>
          <el-menu-item index="/asset/relation">
            <el-icon><Connection /></el-icon><span>表关系</span>
          </el-menu-item>
        </el-sub-menu>
        <el-sub-menu index="knowledge-group">
          <template #title>
            <el-icon><Reading /></el-icon><span>知识库管理</span>
          </template>
          <el-menu-item index="/knowledge">
            <el-icon><Collection /></el-icon><span>知识库内容</span>
          </el-menu-item>
          <el-menu-item index="/knowledge/tags">
            <el-icon><PriceTag /></el-icon><span>标签管理</span>
          </el-menu-item>
          <el-menu-item index="/knowledge/feedback">
            <el-icon><RefreshRight /></el-icon><span>反馈闭环</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-main class="layout__main">
      <router-view />
    </el-main>
  </el-container>
</template>

<style>
body { margin: 0; background: var(--bg); overflow-x: auto; }
.layout { min-height: 100vh; min-width: 1180px; }
.layout__aside {
  background: var(--card);
  border-right: 1px solid var(--line);
}
.layout__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px;
}
.layout__logo {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  line-height: 20px;
  text-align: center;
  flex-shrink: 0;
}
.layout__brand-text {
  font-weight: 700;
  font-size: 15px;
  color: var(--ink);
  letter-spacing: 0.01em;
  white-space: nowrap;
}
.layout__menu { border-right: none; padding: 0 6px; }
.layout__main { padding: 24px; overflow: visible; }
</style>
