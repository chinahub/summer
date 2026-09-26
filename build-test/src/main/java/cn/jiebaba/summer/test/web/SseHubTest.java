package cn.jiebaba.summer.test.web;

import cn.jiebaba.summer.web.sse.SseComment;
import cn.jiebaba.summer.web.sse.SseEmitter;
import cn.jiebaba.summer.web.sse.SseEvent;
import cn.jiebaba.summer.web.sse.SseHub;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

/**
 * {@link SseHub} 广播中枢单测：自动事件 id、断线补发（Last-Event-ID）、心跳注释帧、
 * 退订与关闭语义。
 */
public class SseHubTest {

    @Test
    @DisplayName("广播送达全部订阅者并自动分配递增事件 id")
    void broadcastReachesAllWithAutoIds() throws Exception {
        SseHub hub = new SseHub(16, 0);
        try {
            SseEmitter a = hub.subscribe(new SseEmitter(1000));
            SseEmitter b = hub.subscribe(new SseEmitter(1000));
            Assertions.assertEquals(2, hub.subscriberCount());

            long id1 = hub.broadcast("hello", "ping");
            long id2 = hub.broadcast("world", "ping");
            Assertions.assertEquals(1, id1);
            Assertions.assertEquals(2, id2);

            SseEvent ea1 = (SseEvent) a.take();
            SseEvent ea2 = (SseEvent) a.take();
            SseEvent eb1 = (SseEvent) b.take();
            Assertions.assertEquals("1", ea1.id());
            Assertions.assertEquals("hello", ea1.data());
            Assertions.assertEquals("ping", ea1.event());
            Assertions.assertEquals("2", ea2.id());
            Assertions.assertEquals("1", eb1.id());
        } finally {
            hub.close();
        }
    }

    @Test
    @DisplayName("断线补发：携带 Last-Event-ID 订阅只补发其后的帧")
    void replayAfterLastEventId() throws Exception {
        SseHub hub = new SseHub(16, 0);
        try {
            hub.broadcast("e1");
            hub.broadcast("e2");
            hub.broadcast("e3");
            SseEmitter reconnected = hub.subscribe(new SseEmitter(1000), "1");
            SseEvent first = (SseEvent) reconnected.take();
            SseEvent second = (SseEvent) reconnected.take();
            Assertions.assertEquals("2", first.id(), "应补发 id>1 的帧");
            Assertions.assertEquals("3", second.id());
            // 后续广播继续送达
            hub.broadcast("e4");
            Assertions.assertEquals("4", ((SseEvent) reconnected.take()).id());
        } finally {
            hub.close();
        }
    }

    @Test
    @DisplayName("补发缓冲按容量淘汰，只保留最近 N 帧")
    void replayBufferEvictsOldest() throws Exception {
        SseHub hub = new SseHub(2, 0);
        try {
            for (int i = 1; i <= 5; i++) hub.broadcast("e" + i);
            SseEmitter late = hub.subscribe(new SseEmitter(1000), "4");
            SseEvent only = (SseEvent) late.take();
            Assertions.assertEquals("5", only.id(), "缓冲仅留最近 2 帧，只应补发 id=5");
        } finally {
            hub.close();
        }
    }

    @Test
    @DisplayName("心跳定时发送注释帧保活")
    void heartbeatSendsCommentFrame() throws Exception {
        SseHub hub = new SseHub(0, 30);
        try {
            SseEmitter e = hub.subscribe(new SseEmitter(2000));
            Object item = e.take();
            Assertions.assertInstanceOf(SseComment.class, item, "心跳应为注释帧");
        } finally {
            hub.close();
        }
    }

    @Test
    @DisplayName("退订结束事件流；关闭中枢完成全部订阅者并拒绝后续广播")
    void unsubscribeAndClose() {
        SseHub hub = new SseHub(16, 0);
        SseEmitter a = hub.subscribe(new SseEmitter(1000));
        SseEmitter b = hub.subscribe(new SseEmitter(1000));
        hub.unsubscribe(a);
        Assertions.assertTrue(a.isDone(), "退订应结束该事件流");
        Assertions.assertEquals(1, hub.subscriberCount());

        hub.close();
        Assertions.assertTrue(b.isDone(), "关闭中枢应完成全部订阅者");
        Assertions.assertEquals(0, hub.subscriberCount());
        Assertions.assertThrows(IllegalStateException.class, () -> hub.broadcast("late"));
    }

    @Test
    @DisplayName("SseComment 编码符合 SSE 注释帧规范")
    void commentEncoding() {
        Assertions.assertEquals(": hb\n\n",
                new String(SseComment.heartbeat().encode(), StandardCharsets.UTF_8));
        Assertions.assertEquals(": note\n\n",
                new String(new SseComment("note").encode(), StandardCharsets.UTF_8));
    }
}
