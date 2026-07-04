package cn.jiebaba.summer.web.multipart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed multipart/form-data body: file parts keyed by field name plus regular
 * (non-file) form fields. Built by {@link MultipartParser}.
 */
public final class MultipartForm {

    private final Map<String, List<MultipartFile>> files = new LinkedHashMap<>();
    private final Map<String, List<String>> fields = new LinkedHashMap<>();

    void addFile(String name, MultipartFile file) {
        files.computeIfAbsent(name, k -> new ArrayList<>()).add(file);
    }

    void addField(String name, String value) {
        fields.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    public MultipartFile getFile(String name) {
        List<MultipartFile> list = files.get(name);
        return list == null ? null : list.get(0);
    }

    public List<MultipartFile> getFiles(String name) {
        return files.getOrDefault(name, List.of());
    }

    public Map<String, List<MultipartFile>> files() {
        return files;
    }

    public String getField(String name) {
        List<String> list = fields.get(name);
        return list == null ? null : list.get(0);
    }

    public List<String> getFields(String name) {
        return fields.getOrDefault(name, List.of());
    }

    public Map<String, List<String>> fields() {
        return fields;
    }
}