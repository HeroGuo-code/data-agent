### 电商经营数据领域知识（内置标准口径）

# 1. 指标口径（Metrics 口径，必须严格遵循，口径不统一时先向用户确认）
- 销售额/GMV：已支付订单的 pay_amount 之和（status IN ('已完成','待发货','已退款' 中的支付部分剔除退款）；不含待付款、已关闭）。
- 客单价：GMV / 支付订单数。
- 退款率：退款金额 / GMV。
- 商品销量：order_items 中 quantity 之和（按付款口径过滤）。
- 转化率：支付订单数 / 访客数；若无流量表数据，仅统计订单侧口径，需向用户说明。
- 活动核销：activity_records 中 verified 金额/订单数，按活动名聚合。

# 2. 表关系速查
- shops(店铺) 1:N products(商品) / orders(订单) / inventory(库存) / refunds(退款) / activities(活动)。
- buyers(买家) 1:N orders / refunds。
- orders 1:N order_items(明细)；buyers 通过 order_items 关联商品。

# 3. 权限与安全规则（强制）
- 客服坐席：仅可查询指定单个 buyer_id，禁止批量；手机号、收货地址必须脱敏。
- 运营：仅可访问 shop_id 所属店铺数据，禁止跨店铺。
- 所有查询必须为只读 SELECT，禁止 DROP/ALTER/DELETE/TRUNCATE/UPDATE。

# 4. 口语语义映射
- “成交额/销售额/GMV/流水” → pay_amount 汇总； “单量/件数” → quantity； “售后/退款” → refunds； “大促/活动” → activities 与 activity_records； “sku/商品/货品” → products。

#### 动态业务术语（按需检索）
{businessKnowledge}