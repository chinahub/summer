package cn.jiebaba.summer.admin.system.post;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.common.log.OperLog;
import cn.jiebaba.summer.core.annotation.Service;
import cn.jiebaba.summer.data.conditions.LambdaQueryWrapper;
import cn.jiebaba.summer.data.page.IPage;

import java.util.List;
import java.util.Objects;

/**
 * 岗位服务：分页条件查询、新增/修改（编码与名称唯一性校验）与逻辑删除
 * （被用户引用时拒绝）。
 */
@Service
public class PostService {

    private final SysPostMapper postMapper;
    private final SysUserPostMapper userPostMapper;

    public PostService(SysPostMapper postMapper, SysUserPostMapper userPostMapper) {
        this.postMapper = postMapper;
        this.userPostMapper = userPostMapper;
    }

    /**
     * 分页查询岗位列表：名称/编码模糊匹配、状态精确过滤，按显示排序升序。
     * 注意框架 Wrapper 查询不自动附加逻辑删除条件，需显式声明 del_flag = '0'。
     *
     * @param query    分页参数
     * @param postName 岗位名称模糊词（可空）
     * @param postCode 岗位编码模糊词（可空）
     * @param status   状态过滤（可空）
     * @return 分页结果
     */
    public IPage<SysPost> page(PageQuery query, String postName, String postCode, String status) {
        LambdaQueryWrapper<SysPost> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq("del_flag", "0");
        if (postName != null && !postName.isBlank()) {
            wrapper.like(SysPost::getPostName, postName);
        }
        if (postCode != null && !postCode.isBlank()) {
            wrapper.like(SysPost::getPostCode, postCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysPost::getStatus, status);
        }
        wrapper.orderByAsc(SysPost::getPostSort);
        return postMapper.selectPage(query.toPage(), wrapper);
    }

    /** 按 ID 查询岗位，不存在抛 404。 */
    public SysPost get(Long postId) {
        if (postId == null) {
            throw new BusinessException(400, "岗位ID不能为空");
        }
        SysPost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        return post;
    }

    /** 新增岗位：编码与名称在未删除岗位中唯一。 */
    @OperLog(module = "岗位管理", action = "新增")
    public Long create(SysPost post) {
        validateUnique(post, null);
        if (post.getPostSort() == null) {
            post.setPostSort(0);
        }
        if (post.getStatus() == null || post.getStatus().isBlank()) {
            post.setStatus("0");
        }
        post.setDelFlag("0");
        postMapper.insert(post);
        return post.getPostId();
    }

    /** 更新岗位：目标必须存在，编码与名称唯一性校验排除自身。 */
    @OperLog(module = "岗位管理", action = "修改")
    public void update(SysPost post) {
        if (post == null || post.getPostId() == null) {
            throw new BusinessException(400, "岗位ID不能为空");
        }
        get(post.getPostId());
        validateUnique(post, post.getPostId());
        postMapper.updateById(post);
    }

    /**
     * 删除岗位（逻辑删除）：已被用户-岗位关系引用时抛 409 拒绝，
     * 关系表为物理删除表，引用检查直接全量读取关联行。
     *
     * @param postId 岗位 ID
     */
    @OperLog(module = "岗位管理", action = "删除")
    public void delete(Long postId) {
        get(postId);
        for (SysUserPost userPost : userPostMapper.selectList()) {
            if (Objects.equals(userPost.getPostId(), postId)) {
                throw new BusinessException(409, "岗位已分配给用户，不允许删除");
            }
        }
        postMapper.deleteById(postId);
    }

    /** 校验岗位编码与名称非空且在未删除岗位中唯一（excludeId 用于更新时排除自身）。 */
    private void validateUnique(SysPost post, Long excludeId) {
        if (post == null || post.getPostCode() == null || post.getPostCode().isBlank()) {
            throw new BusinessException(400, "岗位编码不能为空");
        }
        if (post.getPostName() == null || post.getPostName().isBlank()) {
            throw new BusinessException(400, "岗位名称不能为空");
        }
        List<SysPost> all = postMapper.selectList();
        for (SysPost other : all) {
            if (Objects.equals(other.getPostId(), excludeId)) {
                continue;
            }
            if (post.getPostCode().equals(other.getPostCode())) {
                throw new BusinessException(409, "岗位编码已存在");
            }
            if (post.getPostName().equals(other.getPostName())) {
                throw new BusinessException(409, "岗位名称已存在");
            }
        }
    }
}
