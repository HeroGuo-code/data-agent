package io.github.qifan777.server.dataset.scheme;

import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.shared.datasource.DataFileLocator;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 电商演示库 schema 自动入库（幂等）：把 ecommerce_ops 表/列/外键写入 PG 元数据表，
 * 并向量化到 pgvector，供样例召回与 SQL 生成使用。首次启动执行。
 */
@Component
public class EcommerceSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommerceSchemaInitializer.class);
    private static final String DB = "ecommerce_ops";

    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;
    private final DataFileLocator locator;

    public EcommerceSchemaInitializer(JdbcTemplate jdbc, VectorStore vectorStore, DataFileLocator locator) {
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
        this.locator = locator;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Long existing = jdbc.queryForObject(
            "SELECT count(*) FROM db_table WHERE database_id = ?", Long.class, DB);
        if (existing != null && existing > 0) {
            log.info("[schema-init] ecommerce_ops 已初始化，跳过.");
            return;
        }
        importEcommerceSchema();
    }

    /** 强制重建 ecommerce_ops 的表/列/外键/知识并重新向量化，供“重新入库召回”使用。 */
    public synchronized void reindex() throws Exception {
        log.info("[schema-init] 触发 ecommerce_ops 重新入库召回...");
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'databaseId' = ?"
            + " AND metadata->>'vectorType' IN ('table','column','questionKnowledge','glossaryKnowledge')", DB);
        jdbc.update("DELETE FROM db_foreign_key WHERE source_column_id IN "
            + "(SELECT id FROM db_column WHERE table_id IN (SELECT id FROM db_table WHERE database_id = ?))", DB);
        jdbc.update("DELETE FROM db_column WHERE table_id IN (SELECT id FROM db_table WHERE database_id = ?)", DB);
        jdbc.update("DELETE FROM db_table WHERE database_id = ?", DB);
        jdbc.update("DELETE FROM question_knowledge WHERE database_id = ?", DB);
        jdbc.update("DELETE FROM glossary_knowledge WHERE database_id = ?", DB);
        importEcommerceSchema();
        log.info("[schema-init] ecommerce_ops 重新入库完成.");
    }

    private void importEcommerceSchema() throws Exception {
        log.info("[schema-init] 开始初始化电商演示库 schema 与向量...");

        Map<String, String> tableDesc = tableDescriptions();
        Path file = locator.locate("dev_20240627/dev_databases/ecommerce_ops", "ecommerce_ops.sqlite");
        String url = "jdbc:sqlite:" + file.toString();

        Map<String, DbRef> tables = new LinkedHashMap<>();
        List<Document> docs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(url);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            List<String> tableNames = new ArrayList<>();
            while (rs.next()) tableNames.add(rs.getString(1));
            for (String tname : tableNames) {
                String tid = UUID.randomUUID().toString();
                jdbc.update("INSERT INTO db_table(id,name,description,database_id) VALUES(?::uuid,?,?,?)",
                    tid, tname, tableDesc.getOrDefault(tname, "表 " + tname), DB);
                DbRef tref = new DbRef(tid, tname);
                tables.put(tname, tref);
                docs.add(new Document(tableDesc.getOrDefault(tname, "表 " + tname), Map.of(
                    DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.TABLE,
                    DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, DB,
                    DataAgentSpec.Retrieval.DocumentMetadataKey.TABLE_ID, UUID.fromString(tid))));
                try (ResultSet crs = st.executeQuery("PRAGMA table_info(`" + tname + "`)")) {
                    while (crs.next()) {
                        String cname = crs.getString("name");
                        String ctype = crs.getString("type");
                        boolean pk = crs.getInt("pk") == 1;
                        String cid = UUID.randomUUID().toString();
                        jdbc.update("INSERT INTO db_column(id,name,type,description,is_primary_key,table_id) VALUES(?::uuid,?,?,?,?,?::uuid)",
                            cid, cname, ctype, columnDescription(tname, cname), pk, tid);
                        tref.columns.put(cname, cid);
                        docs.add(new Document(columnDescription(tname, cname), Map.of(
                            DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.COLUMN,
                            DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, DB,
                            DataAgentSpec.Retrieval.DocumentMetadataKey.TABLE_ID, UUID.fromString(tid),
                            DataAgentSpec.Retrieval.DocumentMetadataKey.COLUMN_ID, UUID.fromString(cid))));
                    }
                }
            }
        }

        insertForeignKeys(tables);

        for (int i = 0; i < docs.size(); i += 10) {
            vectorStore.add(docs.subList(i, Math.min(i + 10, docs.size())));
        }

        List<Document> kbdocs = new ArrayList<>();
        addGlossary("GMV", "销售额/GMV = 已支付订单 pay_amount 之和，排除待付款与已关闭。", "销售额,成交额,流水", kbdocs);
        addGlossary("客单价", "客单价 = GMV / 支付订单数。", "人均,客单", kbdocs);
        addGlossary("退款率", "退款率 = 退款金额 / GMV，衡量售后质量。", "售后,退款", kbdocs);
        addGlossary("转化率", "转化率 = 支付订单数 / 访客数，无流量表时仅订单侧口径。", "转化", kbdocs);
        addGlossary("商品名称", "商品名称指 products.name，用于展示商品名；订单明细 order_items 通过 product_id 关联 products 取名称。", "商品名,产品名", kbdocs);
        addGlossary("数据权限", "运营账号仅可访问其所属店铺(users.shop_id 与 shops.owner_id)的数据，客服坐席仅可查询指定单个买家；敏感字段需脱敏。", "权限,越权,隔离", kbdocs);
        addQuestion("查询6月销售额最高的店铺，给出GMV排名", kbdocs);
        addQuestion("查询买家ID为5的历史订单与消费汇总", kbdocs);
        addQuestion("统计各商品类别的销售件数与GMV", kbdocs);
        addQuestion("统计最近30天各店铺的退款率", kbdocs);
        addQuestion("查询店铺1上架中销量最高的商品名称与销售额", kbdocs);
        for (int i = 0; i < kbdocs.size(); i += 10) {
            vectorStore.add(kbdocs.subList(i, Math.min(i + 10, kbdocs.size())));
        }

        log.info("[schema-init] 完成. 表={}, 向量文档={} table + {} knowledge", tables.size(), docs.size(), kbdocs.size());
    }

    private void addGlossary(String term, String desc, String syn, List<Document> docs) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO glossary_knowledge(id,database_id,term,description,synonyms) VALUES(?::uuid,?,?,?,?)",
            id, DB, term, desc, syn);
        docs.add(new Document("业务名词: " + term + ", 说明: " + desc + ", 同义词: " + syn, Map.of(
            DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.GLOSSARY_KNOWLEDGE,
            DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, DB,
            DataAgentSpec.Retrieval.DocumentMetadataKey.BUSINESS_TERM_ID, UUID.fromString(id))));
    }

    private void addQuestion(String q, List<Document> docs) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO question_knowledge(id,database_id,question,answer) VALUES(?::uuid,?,?,?)", id, DB, q, q);
        docs.add(new Document(q, Map.of(
            DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.QUESTION_KNOWLEDGE,
            DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, DB,
            DataAgentSpec.Retrieval.DocumentMetadataKey.KNOWLEDGE_ID, UUID.fromString(id))));
    }

    private void insertForeignKeys(Map<String, DbRef> tables) {
        addFk(tables, "products", "shop_id", "shops", "shop_id");
        addFk(tables, "orders", "shop_id", "shops", "shop_id");
        addFk(tables, "inventory", "shop_id", "shops", "shop_id");
        addFk(tables, "refunds", "shop_id", "shops", "shop_id");
        addFk(tables, "activities", "shop_id", "shops", "shop_id");
        addFk(tables, "users", "shop_id", "shops", "shop_id");
        addFk(tables, "orders", "buyer_id", "buyers", "buyer_id");
        addFk(tables, "refunds", "buyer_id", "buyers", "buyer_id");
        addFk(tables, "order_items", "product_id", "products", "product_id");
        addFk(tables, "inventory", "product_id", "products", "product_id");
        addFk(tables, "order_items", "order_id", "orders", "order_id");
        addFk(tables, "refunds", "order_id", "orders", "order_id");
    }

    private void addFk(Map<String, DbRef> tables, String srcTable, String srcCol, String tgtTable, String tgtCol) {
        DbRef src = tables.get(srcTable);
        DbRef tgt = tables.get(tgtTable);
        if (src == null || tgt == null) return;
        String sc = src.columns.get(srcCol);
        String tc = tgt.columns.get(tgtCol);
        if (sc == null || tc == null) return;
        jdbc.update("INSERT INTO db_foreign_key(id,source_column_id,target_column_id) VALUES(?::uuid,?::uuid,?::uuid)",
            UUID.randomUUID().toString(), sc, tc);
    }

    private static final class DbRef {
        final String id;
        final String name;
        final Map<String, String> columns = new LinkedHashMap<>();
        DbRef(String id, String name) { this.id = id; this.name = name; }
    }

    private Map<String, String> tableDescriptions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("shops", "店铺表 shops：电商平台各店铺，字段 shop_id 店铺ID、shop_name 店铺名、owner_id 店主ID、category 主营类目、created_at 创建时间。与 orders/products/inventory/refunds/activities/users 通过 shop_id 关联。");
        m.put("buyers", "买家表 buyers：下单买家，字段 buyer_id 买家ID、name 姓名、phone 手机号(敏感)、province/city 省市、address 收货地址(敏感)、vip_level 会员等级、register_time 注册时间。与 orders/refunds 通过 buyer_id 关联。");
        m.put("users", "用户表 users：平台账号，字段 user_id 用户ID、username 登录名、phone 手机号、role 角色(管理员/运营/客服坐席)、shop_id 所属店铺(运营账号做数据权限隔离用，关联 shops.shop_id)、status 状态、created_at 创建时间。运营仅可访问所属店铺数据，客服坐席仅可查询指定单个买家。");
        m.put("products", "商品表 products：在售商品，字段 product_id 商品ID、shop_id 所属店铺、name 商品名称、category 类目(服装/数码/食品/家居/美妆/运动)、price 售价、cost 成本、status 状态(上架/下架/缺货)、sale_count 销量。与 order_items/inventory 关联。");
        m.put("orders", "订单表 orders：买家订单，字段 order_id 订单ID、order_no 订单号、shop_id 店铺、buyer_id 买家、order_date 下单日期、status 状态(待付款/待发货/已完成/已退款/已关闭)、total_amount 订单金额、pay_amount 实付金额(GMV口径)、discount_amount 优惠金额。关联 shops/buyers，1:N 到 order_items。");
        m.put("order_items", "订单明细表 order_items：订单内商品行，字段 item_id 明细ID、order_id 订单、product_id 商品、quantity 数量、unit_price 单价、subtotal 小计。由 orders 聚合销量与销售额明细。");
        m.put("inventory", "库存表 inventory：商品库存，字段 inventory_id 库存ID、product_id 商品、shop_id 店铺、quantity 库存数量、updated_at 更新时间。");
        m.put("refunds", "退款表 refunds：售后退款，字段 refund_id 退款ID、order_id 订单、buyer_id 买家、shop_id 店铺、reason 原因、amount 金额、status 状态(处理中/已退款/已拒绝)、apply_time 申请时间。");
        m.put("activities", "活动表 activities：营销活动，字段 activity_id 活动ID、shop_id 店铺、name 活动名、type 类型(满减/折扣/秒杀/优惠券)、start_time/end_time、budget 预算。");
        m.put("activity_records", "活动核销表 activity_records：活动订单核销记录，字段 record_id、activity_id 活动、activity_name 活动名、order_id 订单、shop_id 店铺、verify_time 核销时间、amount 核销金额。用于大促/活动复盘。");
        return m;
    }

    private String columnDescription(String table, String col) {
        switch (table) {
            case "users":
                return switch (col) {
                    case "user_id" -> "用户主键ID";
                    case "username" -> "登录用户名/账号";
                    case "phone" -> "手机号，敏感信息需脱敏";
                    case "role" -> "账号角色：管理员/运营/客服坐席（数据权限依据）";
                    case "shop_id" -> "所属店铺ID，关联 shops.shop_id；运营仅可访问该店铺数据";
                    case "status" -> "账号状态：启用/禁用";
                    case "created_at" -> "创建时间";
                    default -> col;
                };
            case "products":
                return switch (col) {
                    case "product_id" -> "商品主键ID";
                    case "shop_id" -> "所属店铺ID，关联 shops.shop_id";
                    case "name" -> "商品名称";
                    case "category" -> "商品类目：服装/数码/食品/家居/美妆/运动";
                    case "price" -> "商品售价";
                    case "cost" -> "商品成本";
                    case "status" -> "商品状态：上架/下架/缺货";
                    case "sale_count" -> "累计销量";
                    default -> col;
                };
            case "orders":
                return switch (col) {
                    case "order_id" -> "订单主键ID";
                    case "order_no" -> "订单号，全局唯一";
                    case "shop_id" -> "所属店铺ID，关联 shops.shop_id";
                    case "buyer_id" -> "下单买家ID，关联 buyers.buyer_id";
                    case "order_date" -> "下单日期，如 2024-06-12";
                    case "status" -> "订单状态：待付款/待发货/已完成/已退款/已关闭";
                    case "total_amount" -> "订单总金额";
                    case "pay_amount" -> "实付金额，销售额GMV统计口径用此字段";
                    case "discount_amount" -> "优惠金额";
                    default -> col;
                };
            case "order_items":
                return switch (col) {
                    case "item_id" -> "明细主键ID";
                    case "order_id" -> "所属订单ID，关联 orders.order_id";
                    case "product_id" -> "商品ID，关联 products.product_id";
                    case "quantity" -> "购买数量（销量统计用，取商品名需 JOIN products.name）";
                    case "unit_price" -> "成交单价";
                    case "subtotal" -> "小计金额";
                    default -> col;
                };
            case "buyers":
                return switch (col) {
                    case "buyer_id" -> "买家主键ID";
                    case "name" -> "买家姓名";
                    case "phone" -> "手机号，敏感信息需脱敏";
                    case "address" -> "收货地址，敏感信息需脱敏";
                    case "vip_level" -> "会员等级：普通/黄金/铂金/钻石";
                    default -> col;
                };
            case "shops":
                return switch (col) {
                    case "shop_id" -> "店铺主键ID";
                    case "shop_name" -> "店铺名称";
                    case "owner_id" -> "店主ID（运营人员归属，与 users.shop_id 配套做数据权限）";
                    case "category" -> "店铺主营类目";
                    default -> col;
                };
            case "inventory":
                return switch (col) {
                    case "inventory_id" -> "库存主键ID";
                    case "product_id" -> "商品ID，关联 products.product_id";
                    case "shop_id" -> "店铺ID，关联 shops.shop_id";
                    case "quantity" -> "当前库存数量";
                    case "updated_at" -> "最近更新时间";
                    default -> col;
                };
            case "refunds":
                return switch (col) {
                    case "refund_id" -> "退款主键ID";
                    case "order_id" -> "关联订单ID，关联 orders.order_id";
                    case "buyer_id" -> "买家ID，关联 buyers.buyer_id";
                    case "shop_id" -> "店铺ID，关联 shops.shop_id";
                    case "reason" -> "退款原因";
                    case "amount" -> "退款金额";
                    case "status" -> "退款状态：处理中/已退款/已拒绝";
                    case "apply_time" -> "申请退款时间";
                    default -> col;
                };
            case "activities":
                return switch (col) {
                    case "activity_id" -> "活动主键ID";
                    case "shop_id" -> "店铺ID，关联 shops.shop_id";
                    case "name" -> "活动名称";
                    case "type" -> "活动类型：满减/折扣/秒杀/优惠券";
                    case "budget" -> "活动预算";
                    default -> col;
                };
            case "activity_records":
                return switch (col) {
                    case "record_id" -> "核销记录主键ID";
                    case "activity_id" -> "活动ID，关联 activities.activity_id";
                    case "activity_name" -> "活动名称";
                    case "order_id" -> "参与订单ID";
                    case "shop_id" -> "店铺ID";
                    case "verify_time" -> "核销时间";
                    case "amount" -> "核销金额";
                    default -> col;
                };
            default:
                return col;
        }
    }
}
