package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.core.annotation.Autowired;
import cn.jiebaba.summer.sample.entity.Product;
import cn.jiebaba.summer.sample.repository.ProductService;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.ResponseStatus;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @PostMapping
    @ResponseStatus(201)
    public Product create(@Valid @RequestBody Product product) {
        productService.save(product);
        return product;
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.getById(id);
    }

    @GetMapping
    public List<Product> list(@RequestParam(value = "name", required = false) String name,
                              @RequestParam(value = "page", defaultValue = "1") int page,
                              @RequestParam(value = "size", defaultValue = "10") int size) {
        if (name != null && !name.isEmpty()) {
            return productService.searchByName(name, page, size).records();
        }
        return productService.list();
    }
}
