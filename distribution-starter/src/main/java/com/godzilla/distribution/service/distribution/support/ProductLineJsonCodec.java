package com.godzilla.distribution.service.distribution.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 产品线 JSON 列编解码。
 *
 * <p>渠道与成员都用 {@code product_lines_json} 存产品线编码数组，序列化逻辑原先在两个
 * Service 中各写一遍，抽出来后异常语义和空值处理保持一致。</p>
 */
@Component
public class ProductLineJsonCodec {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};

    private final ObjectMapper objectMapper;

    public ProductLineJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> read(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析产品线配置失败", e);
        }
    }

    public String write(List<String> productLines) {
        if (productLines == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(productLines);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化产品线配置失败", e);
        }
    }
}
