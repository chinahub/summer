package cn.jiebaba.summer.test.ai;

import cn.jiebaba.summer.ai.chat.UserMessage;
import cn.jiebaba.summer.ai.chat.content.ContentPart;
import cn.jiebaba.summer.ai.chat.content.ImageUrlPart;
import cn.jiebaba.summer.ai.chat.content.InputAudioPart;
import cn.jiebaba.summer.ai.chat.content.TextPart;
import cn.jiebaba.summer.core.test.Assert;
import cn.jiebaba.summer.core.test.Test;

import java.util.List;

/** 多模态消息模型与内容片段的单元测试。 */
public class AiMessageModelTest {

    @Test
    public void textOnlyUserMessage() {
        UserMessage msg = new UserMessage("你好");
        Assert.assertEquals("user", msg.role());
        Assert.assertEquals("你好", msg.content());
        Assert.assertEquals(1, msg.parts().size());
        Assert.assertTrue(msg.parts().get(0) instanceof TextPart);
    }

    @Test
    public void multimodalPartsJoinText() {
        UserMessage msg = UserMessage.of(
                new TextPart("这是什么图？"),
                new ImageUrlPart("https://example.com/a.png", "high"),
                new InputAudioPart("base64data", "wav"));
        Assert.assertEquals(3, msg.parts().size());
        Assert.assertEquals("这是什么图？", msg.content());
        Assert.assertEquals("user", msg.role());
    }

    @Test
    public void imageUrlDefaultDetail() {
        ImageUrlPart part = new ImageUrlPart("data:image/png;base64,AAA");
        Assert.assertNull(part.detail());
        Assert.assertEquals("image_url", part.type());
        ImageUrlPart withDetail = new ImageUrlPart("https://x", "low");
        Assert.assertEquals("low", withDetail.detail());
    }

    @Test
    public void emptyUserMessageRejected() {
        Assert.assertThrows(IllegalArgumentException.class, () -> new UserMessage(List.<ContentPart>of()));
    }
}
