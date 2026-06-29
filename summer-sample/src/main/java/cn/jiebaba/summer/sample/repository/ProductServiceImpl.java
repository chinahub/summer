package cn.jiebaba.summer.sample.repository;

import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.Page;
import cn.jiebaba.summer.data.service.ServiceImpl;
import cn.jiebaba.summer.data.transaction.Transactional;
import cn.jiebaba.summer.sample.entity.Product;
import cn.jiebaba.summer.sample.mapper.ProductMapper;

@Service
public class ProductServiceImpl extends ServiceImpl<ProductMapper, Product> implements ProductService {

    @Override
    public Page<Product> searchByName(String keyword, int current, int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .like(Product::getName, keyword)
                .orderByDesc(Product::getPrice);
        return (Page<Product>) page(new Page<>(current, size), wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchInsert(String name1, String name2, boolean fail) {
        baseMapper.insert(new Product(name1, 100, 1));
        baseMapper.insert(new Product(name2, 100, 1));
        if (fail) {
            throw new RuntimeException("simulated failure -> rollback both inserts");
        }
    }
}
