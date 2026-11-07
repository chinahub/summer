package cn.jiebaba.summer.sample.controller;

import cn.jiebaba.summer.web.annotation.PostMapping;
import cn.jiebaba.summer.web.annotation.RestController;
import cn.jiebaba.summer.web.multipart.MultipartFile;
import cn.jiebaba.summer.web.annotation.RequestPart;

import java.util.LinkedHashMap;
import java.util.Map;

/** 演示通过 @RequestPart + MultipartFile 进行多部分文件上传。 */
@RestController
public class UploadController {

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestPart("file") MultipartFile file,
                                       @RequestPart(value = "description", required = false) String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", file.getName());
        result.put("filename", file.getOriginalFilename());
        result.put("contentType", file.getContentType());
        result.put("size", file.getSize());
        result.put("description", description);
        return result;
    }
}
