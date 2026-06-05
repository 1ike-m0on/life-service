package io.github.ikemoon.lifeservice.common.util;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

public final class ImagePathParser {

    private ImagePathParser() {
    }

    public static List<String> split(String images) {
        if (!StringUtils.hasText(images)) {
            return List.of();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
