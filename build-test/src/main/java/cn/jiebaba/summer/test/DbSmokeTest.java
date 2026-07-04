package cn.jiebaba.summer.test;

import cn.jiebaba.summer.sample.Application;
import cn.jiebaba.summer.web.validation.Validator;
import cn.jiebaba.summer.boot.SummerApplication;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.Page;
import cn.jiebaba.summer.sample.entity.Product;
import cn.jiebaba.summer.sample.mapper.ProductMapper;
import cn.jiebaba.summer.sample.repository.ProductService;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * 针对 Supabase PostgreSQL 数据库的端到端测试：建表、CRUD、分页（多方言）、
 * lambda wrapper、@Transactional 提交/回滚（经 AOP 代理）以及 Bean 校验。
 */
public class DbSmokeTest {

    private static int passed = 0;

    /**
     * 端到端数据库冒烟测试入口：启动应用，覆盖建表、CRUD、分页、lambda wrapper、
     * 事务提交/回滚与 Bean 校验等流程，并在结束时清理临时表。
     */
    public static void main(String[] args) throws Exception {
        SummerApplication app = SummerApplication.run(Application.class, args);
        try {
            DataSource ds = app.context().getBean(DataSource.class);
            ProductMapper mapper = app.context().getBean(ProductMapper.class);
            ProductService service = app.context().getBean(ProductService.class);

            header("DDL");
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS summer_product");
                s.execute("CREATE TABLE summer_product (id BIGINT PRIMARY KEY, name VARCHAR(255), price INT, stock_qty INT)");
            }
            ok("table created");

            header("insert / selectById");
            Product p1 = new Product("phone", 4999, 100);
            service.save(p1);
            expect("id assigned", true, p1.getId() != null);
            Product found = mapper.selectById(p1.getId());
            expect("selectById name", "phone", found.getName());
            expect("selectById stock_qty mapped", 100, found.getStock());

            header("update / delete");
            p1.setPrice(4599);
            mapper.updateById(p1);
            expect("update price", 4599, mapper.selectById(p1.getId()).getPrice());
            Product p2 = new Product("laptop", 8999, 50);
            service.save(p2);
            Product p3 = new Product("tablet", 2999, 200);
            service.save(p3);

            header("list / count");
            List<Product> all = mapper.selectList();
            expect("list size 3", 3, all.size());
            long count = mapper.selectCount(null);
            expect("count 3", 3L, count);

            header("lambda wrapper");
            LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                    .ge(Product::getPrice, 4000)
                    .orderByDesc(Product::getPrice);
            List<Product> pricey = mapper.selectList(w);
            expect("wrapper size 2", 2, pricey.size());
            expect("wrapper ordered desc", "laptop", pricey.get(0).getName());

            header("pagination (postgresql dialect)");
            Page<Product> page = (Page<Product>) service.searchByName("a", 1, 2);
            expect("page total >=2", true, page.total() >= 2);
            expect("page records <=2", true, page.records().size() <= 2);
            System.out.println("  page total=" + page.total() + " pages=" + page.pages() + " records=" + page.records().size());

            header("@Transactional commit");
            long before = mapper.selectCount(null);
            service.batchInsert("txA", "txB", false);
            long afterCommit = mapper.selectCount(null);
            expect("commit added 2", before + 2, afterCommit);

            header("@Transactional rollback");
            long beforeRollback = mapper.selectCount(null);
            boolean rolled = false;
            try {
                service.batchInsert("txC", "txD", true);
            } catch (RuntimeException e) {
                rolled = true;
                System.out.println("  caught (expected): " + e.getMessage());
            }
            expect("transaction threw", true, rolled);
            long afterRollback = mapper.selectCount(null);
            expect("rollback kept count", beforeRollback, afterRollback);

            header("validation");
            Product bad = new Product(null, -5, 1);
            var violations = Validator.validate(bad);
            System.out.println("  violations=" + violations);
            expect("2 violations (name blank, price min)", 2, violations.size());

            header("cleanup");
            try (Connection c = ds.getConnection(); Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS summer_product");
            }
            ok("table dropped");
        } finally {
            app.webServer().stop();
            app.context().close();
        }
        System.out.println();
        System.out.println("DB smoke test: " + passed + " assertions passed");
    }

    static void header(String name) { System.out.println("== " + name + " =="); }
    static void ok(String label) { passed++; System.out.println("  OK " + label); }
    static void expect(String label, Object expected, Object actual) {
        boolean ok = java.util.Objects.equals(expected, actual);
        if (ok) { passed++; }
        else { System.out.println("  FAIL " + label + ": expected=" + expected + " actual=" + actual); }
    }
}
