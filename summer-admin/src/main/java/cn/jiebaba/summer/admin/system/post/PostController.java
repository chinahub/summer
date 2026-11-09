package cn.jiebaba.summer.admin.system.post;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.api.R;
import cn.jiebaba.summer.admin.common.api.TableData;
import cn.jiebaba.summer.data.page.IPage;
import cn.jiebaba.summer.security.annotation.PreAuthorize;
import cn.jiebaba.summer.web.annotation.DeleteMapping;
import cn.jiebaba.summer.web.annotation.GetMapping;
import cn.jiebaba.summer.web.annotation.PathVariable;
import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.PutMapping;
import cn.jiebaba.summer.web.annotation.RequestBody;
import cn.jiebaba.summer.web.annotation.RequestMapping;
import cn.jiebaba.summer.web.annotation.RequestParam;
import cn.jiebaba.summer.web.annotation.RestController;

/** 岗位管理接口：条件分页列表、详情、新增、修改与删除。 */
@RestController
@RequestMapping("/api/system/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** 岗位分页列表：支持名称/编码模糊与状态过滤。 */
    @PreAuthorize(authorities = {"system:post:list"})
    @GetMapping("/list")
    public R<TableData<SysPost>> list(PageQuery pageQuery,
                                      @RequestParam(value = "postName", required = false) String postName,
                                      @RequestParam(value = "postCode", required = false) String postCode,
                                      @RequestParam(value = "status", required = false) String status) {
        IPage<SysPost> page = postService.page(pageQuery, postName, postCode, status);
        return R.ok(TableData.of(page));
    }

    /** 岗位详情。 */
    @PreAuthorize(authorities = {"system:post:query"})
    @GetMapping("/{postId}")
    public R<SysPost> get(@PathVariable("postId") Long postId) {
        return R.ok(postService.get(postId));
    }

    /** 新增岗位。 */
    @PreAuthorize(authorities = {"system:post:add"})
    @PostMapping
    public R<Long> create(@RequestBody SysPost post) {
        return R.ok(postService.create(post));
    }

    /** 修改岗位。 */
    @PreAuthorize(authorities = {"system:post:edit"})
    @PutMapping
    public R<Void> update(@RequestBody SysPost post) {
        postService.update(post);
        return R.ok();
    }

    /** 删除岗位（已分配给用户时拒绝）。 */
    @PreAuthorize(authorities = {"system:post:remove"})
    @DeleteMapping("/{postId}")
    public R<Void> delete(@PathVariable("postId") Long postId) {
        postService.delete(postId);
        return R.ok();
    }
}
