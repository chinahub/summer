package cn.jiebaba.summer.sample.repository;

import cn.jiebaba.summer.data.page.Page;
import cn.jiebaba.summer.data.service.IService;
import cn.jiebaba.summer.sample.entity.Product;

public interface ProductService extends IService<Product> {
    Page<Product> searchByName(String keyword, int current, int size);
    void batchInsert(String name1, String name2, boolean fail);
}
