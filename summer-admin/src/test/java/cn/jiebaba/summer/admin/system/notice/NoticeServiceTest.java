package cn.jiebaba.summer.admin.system.notice;

import cn.jiebaba.summer.admin.common.api.PageQuery;
import cn.jiebaba.summer.admin.common.exception.BusinessException;
import cn.jiebaba.summer.admin.testsupport.InMemoryBaseMapper;
import cn.jiebaba.summer.admin.testsupport.WrapperSupport;
import cn.jiebaba.summer.data.conditions.AbstractWrapper;
import cn.jiebaba.summer.data.page.IPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 公告服务纯内存单测：标题必填、非法状态 400、草稿不进入普通可见列表、
 * 发布状态切换、管理端可见全部状态，以及按创建时间倒序 + 主键倒序的稳定排序。
 * sys_notice 无逻辑删除字段，删除为物理删除。内存 Mapper 不回填自增主键，显式指定 ID。
 */
class NoticeServiceTest {

    private NoticeFake mapper;
    private NoticeService noticeService;
    private long nextId = 100L;

    @BeforeEach
    void setUp() {
        mapper = new NoticeFake();
        noticeService = new NoticeService(mapper);
        nextId = 100L;
    }

    @Test
    void createRequiresTitle() {
        SysNotice noTitle = notice(null, "1", null);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> noticeService.create(noTitle)).status());

        SysNotice blankTitle = notice("  ", "1", null);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> noticeService.create(blankTitle)).status());
    }

    @Test
    void createAppliesDefaults() {
        Long id = noticeService.create(notice("欢迎", "1", null));

        SysNotice saved = mapper.selectById(id);
        assertEquals("0", saved.getStatus(), "缺省状态应为已发布");
    }

    @Test
    void invalidStatusRejected() {
        SysNotice bad = notice("坏状态", "1", "9");
        assertEquals(400, assertThrows(BusinessException.class,
                () -> noticeService.create(bad)).status());

        Long id = noticeService.create(notice("正常", "1", null));
        SysNotice badUpdate = notice("坏状态", "1", "x");
        badUpdate.setNoticeId(id);
        assertEquals(400, assertThrows(BusinessException.class,
                () -> noticeService.update(badUpdate)).status());
    }

    @Test
    void draftsAndClosedExcludedFromPublishedList() {
        noticeService.create(notice("已发布", "1", "0"));
        noticeService.create(notice("草稿", "1", "2"));
        noticeService.create(notice("已关闭", "2", "1"));

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        IPage<SysNotice> published = noticeService.publishedPage(query);
        assertEquals(1, published.total());
        assertEquals("已发布", published.records().get(0).getNoticeTitle());

        // 管理端列表可见全部状态（含草稿）
        IPage<SysNotice> management = noticeService.page(query, null, null, null);
        assertEquals(3, management.total());
    }

    @Test
    void managementPageFiltersByTitleTypeAndStatus() {
        noticeService.create(notice("系统升级通知", "1", "0"));
        noticeService.create(notice("放假公告", "2", "0"));
        noticeService.create(notice("草稿公告", "2", "2"));

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        IPage<SysNotice> byTitle = noticeService.page(query, "升级", null, null);
        assertEquals(1, byTitle.total());

        IPage<SysNotice> byType = noticeService.page(query, null, "2", null);
        assertEquals(2, byType.total());

        IPage<SysNotice> byStatus = noticeService.page(query, null, null, "2");
        assertEquals(1, byStatus.total());
        assertEquals("草稿公告", byStatus.records().get(0).getNoticeTitle());
    }

    @Test
    void publishStatusToggle() {
        Long draftId = noticeService.create(notice("待发布", "1", "2"));

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        assertEquals(0, noticeService.publishedPage(query).total());

        SysNotice publish = notice("待发布", "1", "0");
        publish.setNoticeId(draftId);
        noticeService.update(publish);
        assertEquals(1, noticeService.publishedPage(query).total());

        SysNotice close = notice("待发布", "1", "1");
        close.setNoticeId(draftId);
        noticeService.update(close);
        assertEquals(0, noticeService.publishedPage(query).total());
        assertEquals("1", mapper.selectById(draftId).getStatus());
    }

    @Test
    void listOrderedByCreateTimeDescThenIdDesc() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 10, 0);
        SysNotice older = notice("较早", "1", "0");
        older.setCreateTime(now.minusHours(2));
        noticeService.create(older);
        SysNotice latestA = notice("最新A", "1", "0");
        latestA.setCreateTime(now);
        noticeService.create(latestA);
        SysNotice latestB = notice("最新B", "1", "0");
        latestB.setCreateTime(now);
        noticeService.create(latestB);

        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        List<SysNotice> records = noticeService.publishedPage(query).records();

        assertEquals(3, records.size());
        // 同时间按主键倒序，保证排序稳定
        assertEquals("最新B", records.get(0).getNoticeTitle());
        assertEquals("最新A", records.get(1).getNoticeTitle());
        assertEquals("较早", records.get(2).getNoticeTitle());
    }

    @Test
    void updateNotFoundReturns404() {
        SysNotice missing = notice("不存在", "1", "0");
        missing.setNoticeId(999L);
        assertEquals(404, assertThrows(BusinessException.class,
                () -> noticeService.update(missing)).status());
    }

    @Test
    void getNotFoundReturns404() {
        assertEquals(404, assertThrows(BusinessException.class,
                () -> noticeService.get(999L)).status());
    }

    @Test
    void deleteRemovesPhysically() {
        Long id = noticeService.create(notice("待删", "1", "0"));

        noticeService.delete(id);

        assertNull(mapper.selectById(id));
        assertTrue(mapper.selectList().isEmpty(), "物理删除后内存表应无残留行");
    }

    private SysNotice notice(String title, String type, String status) {
        SysNotice entity = new SysNotice();
        entity.setNoticeId(nextId++);
        entity.setNoticeTitle(title);
        entity.setNoticeType(type);
        if (status != null) {
            entity.setStatus(status);
        }
        return entity;
    }

    /** sys_notice 内存 Mapper（物理删除），selectPage 由 WrapperSupport 求值。 */
    private static final class NoticeFake extends InMemoryBaseMapper<SysNotice> implements SysNoticeMapper {
        @Override
        protected Long idOf(SysNotice entity) {
            return entity.getNoticeId();
        }

        @Override
        public IPage<SysNotice> selectPage(IPage<SysNotice> page, AbstractWrapper<SysNotice, ?> wrapper) {
            List<SysNotice> all = WrapperSupport.select(rows, wrapper, ACCESSOR);
            page.setTotal(all.size());
            int from = (int) Math.min(page.offset(), all.size());
            int to = (int) Math.min(from + page.size(), all.size());
            page.setRecords(all.subList(from, to));
            return page;
        }
    }

    /** 列访问器：同时兼容下划线列名与驼峰属性名。 */
    private static final Function<String, Function<SysNotice, Object>> ACCESSOR = col -> switch (col) {
        case "notice_id", "noticeId" -> SysNotice::getNoticeId;
        case "notice_title", "noticeTitle" -> SysNotice::getNoticeTitle;
        case "notice_type", "noticeType" -> SysNotice::getNoticeType;
        case "notice_content", "noticeContent" -> SysNotice::getNoticeContent;
        case "status" -> SysNotice::getStatus;
        case "create_time", "createTime" -> SysNotice::getCreateTime;
        default -> throw new IllegalArgumentException("未知列: " + col);
    };
}
