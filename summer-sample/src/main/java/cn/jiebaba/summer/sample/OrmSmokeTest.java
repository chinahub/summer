package cn.jiebaba.summer.sample;

import cn.jiebaba.summer.sample.mapper.ProductMapper;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.conditions.QueryWrapper;
import cn.jiebaba.summer.data.mapper.MapperProxyFactory;
import cn.jiebaba.summer.data.metadata.MetadataParser;
import cn.jiebaba.summer.data.metadata.TableInfo;
import cn.jiebaba.summer.data.page.Page;
import cn.jiebaba.summer.data.support.SqlBuilder;
import cn.jiebaba.summer.sample.entity.Product;

/**
 * Verifies the ORM layer's pure logic without a database: entity metadata
 * parsing, SQL generation for every CRUD path, wrapper conditions, lambda
 * resolution and pagination SQL.
 */
public class OrmSmokeTest {

    private static int passed = 0;

    public static void main(String[] args) {
        TableInfo table = MetadataParser.parse(Product.class);
        SqlBuilder builder = new SqlBuilder(table);

        header("metadata");
        expect("table name", "summer_product", table.tableName());
        expect("id column", "id", table.idField().column());
        expect("stock column mapped", "stock_qty", table.field("stock").column());
        expect("transientFlag excluded", false, table.fieldMap().containsKey("transientFlag"));
        expect("id type", "ASSIGN_ID", table.idType().name());

        header("insert SQL");
        Product p = new Product("phone", 4999, 100);
        SqlBuilder.Sql insert = builder.insert(p);
        System.out.println("  " + insert.sql());
        System.out.println("  params=" + insert.params());
        expect("insert into product", true, insert.sql().startsWith("INSERT INTO summer_product"));
        expect("insert has stock_qty column", true, insert.sql().contains("stock_qty"));
        expect("insert excludes transientFlag", false, insert.sql().contains("transient_flag"));
        expect("insert params count", 3, insert.params().size());

        header("updateById SQL");
        p.setId(42L);
        SqlBuilder.Sql update = builder.updateById(p);
        System.out.println("  " + update.sql());
        expect("update table", true, update.sql().startsWith("UPDATE summer_product SET"));
        expect("update where id", true, update.sql().endsWith("WHERE id = ?"));
        expect("update params end with id", 42L, update.params().get(update.params().size() - 1));

        header("deleteById SQL");
        SqlBuilder.Sql del = builder.deleteById(42L);
        System.out.println("  " + del.sql());
        expect("delete from product", true, del.sql().startsWith("DELETE FROM summer_product"));
        expect("delete params", 42L, del.params().get(0));

        header("selectById SQL");
        SqlBuilder.Sql sel = builder.selectById(42L);
        System.out.println("  " + sel.sql());
        expect("select all columns", true, sel.sql().contains("id, name, price, stock_qty"));
        expect("select where id", true, sel.sql().endsWith("WHERE id = ?"));

        header("QueryWrapper conditions");
        QueryWrapper<Product> qw = new QueryWrapper<Product>()
                .eq("price", 100).like("name", "phone").ge("stock_qty", 10)
                .orderByDesc("price").last("LIMIT 5");
        SqlBuilder.Sql list = builder.selectList(qw);
        System.out.println("  " + list.sql());
        System.out.println("  params=" + list.params());
        expect("list has WHERE", true, list.sql().contains("WHERE"));
        expect("list has ORDER BY", true, list.sql().contains("ORDER BY price DESC"));
        expect("list has LIKE", true, list.sql().contains("LIKE"));
        expect("list params", 3, list.params().size());

        header("LambdaQueryWrapper");
        LambdaQueryWrapper<Product> lw = new LambdaQueryWrapper<Product>()
                .eq(Product::getName, "phone").gt(Product::getPrice, 100)
                .orderByDesc(Product::getPrice);
        SqlBuilder.Sql lambdaList = builder.selectList(lw);
        System.out.println("  " + lambdaList.sql());
        System.out.println("  params=" + lambdaList.params());
        expect("lambda resolved name->name column", true, lambdaList.sql().contains("name = ?"));
        expect("lambda resolved price->price column", true, lambdaList.sql().contains("price > ?"));
        expect("lambda orderBy price DESC", true, lambdaList.sql().contains("ORDER BY price DESC"));

        header("count SQL");
        SqlBuilder.Sql count = builder.selectCount(lw);
        System.out.println("  " + count.sql());
        expect("count select count(*)", true, count.sql().startsWith("SELECT COUNT(*) FROM summer_product"));

        header("pagination SQL");
        Page<Product> page = new Page<>(2, 20);
        SqlBuilder.Sql paged = builder.selectList(lw, page);
        System.out.println("  " + paged.sql());
        expect("page has LIMIT", true, paged.sql().contains("LIMIT ? OFFSET ?"));
        expect("page offset 20", 20L, page.offset());
        expect("page params has size+offset", true, paged.params().size() >= lw.params().size() + 2);

        header("mapper proxy type resolution");
        Class<?> entity = MapperProxyFactory.resolveEntityType(ProductMapper.class);
        expect("mapper resolves Product", Product.class, entity);

        System.out.println();
        System.out.println("ORM smoke test: " + passed + " assertions passed");
    }

    static void header(String name) { System.out.println("== " + name + " =="); }

    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) { passed++; }
        else { System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }
}
