package cn.jiebaba.summer.test.data;

import cn.jiebaba.summer.data.annotation.IdType;
import cn.jiebaba.summer.data.annotation.TableId;
import cn.jiebaba.summer.data.annotation.TableName;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.conditions.QueryWrapper;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.support.SqlBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * or() 连接符语义的回归测试（issue：or() 曾被当作独立条件段按 AND 拼接，
 * 生成 "a AND OR AND b" 非法 SQL）。修复后 or() 只切换其后一个条件的连接符，
 * and(Consumer)/or(Consumer) 生成带括号的嵌套条件，优先级依赖 SQL 的 AND > OR。
 */
public class WrapperOrConditionTest {

    @TableName("t_product")
    public static class SearchProduct {
        @TableId(type = IdType.ASSIGN_ID)
        private Long id;
        private String name;
        private String skuId;
        public Long getId() { return id; }
        public String getName() { return name; }
        public String getSkuId() { return skuId; }
    }

    @Test
    public void topLevelOrJoinsNextConditionWithOr() {
        QueryWrapper<SearchProduct> w = new QueryWrapper<SearchProduct>()
                .eq("name", "a").or().eq("sku_id", "b");
        Assertions.assertEquals("name = ? OR sku_id = ?", w.sqlSegment());
        Assertions.assertEquals(List.of("a", "b"), w.params());
    }

    /** 复现 issue 场景：and(Consumer) 内 like().or().like() 应生成带括号的 OR 条件。 */
    @Test
    public void nestedAndWithOrInsideGeneratesParenthesizedOr() {
        String kw = "x";
        LambdaQueryWrapper<SearchProduct> w = new LambdaQueryWrapper<SearchProduct>()
                .and(x -> x.like(SearchProduct::getName, kw).or().like(SearchProduct::getSkuId, kw));
        Assertions.assertEquals("(name LIKE ? OR skuId LIKE ?)", w.sqlSegment());

        TableInfo table = MetadataParser.parse(SearchProduct.class);
        SqlBuilder.Sql count = new SqlBuilder(table).selectCount(w);
        Assertions.assertEquals(
                "SELECT COUNT(*) FROM t_product WHERE (name LIKE ? OR sku_id LIKE ?)", count.sql());
        Assertions.assertEquals(List.of("%x%", "%x%"), count.params());
    }

    @Test
    public void orConsumerNestsParenthesizedGroup() {
        QueryWrapper<SearchProduct> w = new QueryWrapper<SearchProduct>()
                .eq("name", "a").or(x -> x.eq("sku_id", "b").eq("name", "c"));
        Assertions.assertEquals("name = ? OR (sku_id = ? AND name = ?)", w.sqlSegment());
        Assertions.assertEquals(List.of("a", "b", "c"), w.params());
    }

    /** 顶层链式 and/or 混合：AND 优先级高于 OR，等价 (a AND b) OR (c AND d)。 */
    @Test
    public void mixedAndOrReliesOnSqlPrecedence() {
        QueryWrapper<SearchProduct> w = new QueryWrapper<SearchProduct>()
                .eq("name", "a").eq("sku_id", "b").or().eq("name", "c").eq("sku_id", "d");
        Assertions.assertEquals("name = ? AND sku_id = ? OR name = ? AND sku_id = ?", w.sqlSegment());
    }

    @Test
    public void orEdgeCasesAreNormalized() {
        // 开头 or() 忽略；连续 or() 去重；末尾 or() 忽略
        QueryWrapper<SearchProduct> w = new QueryWrapper<SearchProduct>()
                .or().eq("name", "a").or().or().eq("sku_id", "b").or();
        Assertions.assertEquals("name = ? OR sku_id = ?", w.sqlSegment());
    }

    @Test
    public void pureAndNestedUnchanged() {
        QueryWrapper<SearchProduct> w = new QueryWrapper<SearchProduct>()
                .eq("name", "a").and(x -> x.eq("sku_id", "b").eq("name", "c"));
        Assertions.assertEquals("name = ? AND (sku_id = ? AND name = ?)", w.sqlSegment());
    }

    /** whereClause 与 sqlSegment 走同一拼接逻辑，AND/OR 连接符一致。 */
    @Test
    public void whereClauseMatchesSqlSegmentConnectors() {
        QueryWrapper<SearchProduct> w = new QueryWrapper<SearchProduct>()
                .eq("name", "a").or().eq("sku_id", "b");
        TableInfo table = MetadataParser.parse(SearchProduct.class);
        String where = new SqlBuilder(table).whereClause(w, new ArrayList<>());
        Assertions.assertEquals("name = ? OR sku_id = ?", where);
    }
}
