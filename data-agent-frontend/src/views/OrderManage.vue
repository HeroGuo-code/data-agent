<script setup lang="ts">
import MallCrud from '@/views/MallCrud.vue'
const columns = [
  { field: 'order_id', label: '订单ID', width: 90 },
  { field: 'order_no', label: '订单号', width: 130 },
  { field: 'shop_id', label: '店铺ID', width: 90 },
  { field: 'buyer_id', label: '买家ID', width: 90 },
  { field: 'order_date', label: '下单日期', width: 120 },
  { field: 'status', label: '状态', width: 90, editable: true, formType: 'select', options: ['待付款','待发货','已完成','已退款','已关闭'] },
  { field: 'total_amount', label: '订单金额', width: 100, editable: true, formType: 'number' },
  { field: 'pay_amount', label: '实付金额', width: 100, editable: true, formType: 'number' },
  { field: 'discount_amount', label: '优惠金额', width: 100, editable: true, formType: 'number' },
]
const searchFields = [
  { field: 'status', label: '状态', type: 'select', options: ['待付款','待发货','已完成','已退款','已关闭'] },
  { field: 'shopId', label: '店铺ID', type: 'text' },
  { field: 'orderNo', label: '订单号', type: 'text' },
]
</script>
<template>
  <MallCrud title="订单管理" api-path="/api/mall/orders" :columns="columns" :search-fields="searchFields" id-field="order_id" />
</template>