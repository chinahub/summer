package cn.jiebaba.summer.web.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Represents an uploaded file received in a multipart/form-data request.
 * Mirrors a minimal subset of Spring's {@code MultipartFile}.
 */
public interface MultipartFile {

    /** The form field name (the {@code name} parameter of Content-Disposition). */
    String getName();

    /** The original filename from the client, with any path stripped. May be {@code null}. */
    String getOriginalFilename();

    /** The part's Content-Type, or {@code null} if not provided. */
    String getContentType();

    boolean isEmpty();

    long getSize();

    byte[] getBytes();

    InputStream getInputStream() throws IOException;

    /** Write the part content to {@code dest}, overwriting it if it exists. */
    void transferTo(File dest) throws IOException;
}