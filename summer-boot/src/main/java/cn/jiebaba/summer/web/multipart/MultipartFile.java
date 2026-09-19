package cn.jiebaba.summer.web.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 表示 multipart/form-data 请求中接收到的上传文件。
 * 对应 Spring {@code MultipartFile} 的一个最小子集。
 */
public interface MultipartFile {

    /** 表单字段名（Content-Disposition 的 {@code name} 参数）。 */
    String getName();

    /** 来自客户端的原始文件名（已剥离路径），可能为 {@code null}。 */
    String getOriginalFilename();

    /** 该部分的 Content-Type，未提供时为 {@code null}。 */
    String getContentType();

    boolean isEmpty();

    long getSize();

    byte[] getBytes();

    InputStream getInputStream() throws IOException;

    /** 将部分内容写入 {@code dest}，若已存在则覆盖。 */
    void transferTo(File dest) throws IOException;
}
