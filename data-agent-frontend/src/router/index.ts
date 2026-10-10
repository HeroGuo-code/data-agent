import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/Home.vue') },
    { path: '/chat', name: 'data-chat', component: () => import('@/views/DataChat.vue') },
    { path: '/orders', name: 'orders', component: () => import('@/views/OrderManage.vue') },
    { path: '/products', name: 'products', component: () => import('@/views/ProductManage.vue') },
    { path: '/shops', name: 'shops', component: () => import('@/views/ShopManage.vue') },
    { path: '/inventory', name: 'inventory', component: () => import('@/views/InventoryManage.vue') },
    { path: '/refunds', name: 'refunds', component: () => import('@/views/RefundManage.vue') },
    { path: '/debug', name: 'debug', component: () => import('@/views/DebugManage.vue') },
    { path: '/users', name: 'users', component: () => import('@/views/UserManage.vue') },
    { path: '/asset', name: 'asset', component: () => import('@/views/DataAsset.vue') },
    { path: '/asset/business', name: 'asset-business', component: () => import('@/views/asset/BusinessTables.vue') },
    { path: '/asset/schema', name: 'asset-schema', component: () => import('@/views/asset/SchemaFields.vue') },
    { path: '/asset/relation', name: 'asset-relation', component: () => import('@/views/asset/TableRelations.vue') },
    { path: '/knowledge', name: 'knowledge', component: () => import('@/views/KnowledgeBase.vue') },
    { path: '/knowledge/tags', name: 'knowledge-tags', component: () => import('@/views/TagManage.vue') },
    { path: '/knowledge/feedback', name: 'knowledge-feedback', component: () => import('@/views/FeedbackManage.vue') },
    { path: '/agent-config', name: 'agent-config', component: () => import('@/views/AgentConfigManage.vue') },
  ],
})

export default router

