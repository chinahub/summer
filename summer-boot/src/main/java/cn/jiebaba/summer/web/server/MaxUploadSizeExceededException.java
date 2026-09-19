package cn.jiebaba.summer.web.server;

import java.io.IOException;

public class MaxUploadSizeExceededException extends IOException {

    private final int actualSize;
    private final int maxSize;

    public MaxUploadSizeExceededException(int actualSize, int maxSize) {
        super("Request body size " + actualSize + " exceeds maximum allowed " + maxSize + " bytes");
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }

    public int actualSize() { return actualSize; }
    public int maxSize() { return maxSize; }
}
