<script setup lang="ts">
import MallCrud from '@/views/MallCrud.vue'
const columns = [
  { field: 'user_id', label: '用户ID', width: 90 },
  { field: 'username', label: '账号', width: 140, editable: true },
  { field: 'phone', label: '手机号', width: 130, editable: true },
  { field: 'role', label: '角色', width: 110, editable: true, formType: 'select', options: ['管理员','运营','客服坐席'] },
  { field: 'shop_id', label: '所属店铺ID', width: 110, editable: true, formType: 'number' },
  { field: 'status', label: '状态', width: 90, editable: true, formType: 'select', options: ['启用','禁用'] },
  { field: 'created_at', label: '创建时间', width: 130 },
]
const searchFields = [
  { field: 'username', label: '账号', type: 'text' },
  { field: 'role', label: '角色', type: 'select', options: ['管理员','运营','客服坐席'] },
  { field: 'status', label: '状态', type: 'select', options: ['启用','禁用'] },
]
</script>
<template>
  <MallCrud title="用户管理（管理员/运营/客服坐席）" api-path="/api/mall/users" :columns="columns" :search-fields="searchFields" id-field="user_id" />
</template>
