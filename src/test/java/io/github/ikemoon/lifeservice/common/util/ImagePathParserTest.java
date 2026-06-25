package io.github.ikemoon.lifeservice.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePathParserTest {

    @Test
    void splitReturnsEmptyListForNullOrBlankInput() {
        assertThat(ImagePathParser.split(null)).isEmpty();
        assertThat(ImagePathParser.split("")).isEmpty();
        assertThat(ImagePathParser.split("   ")).isEmpty();
    }

    @Test
    void splitTrimsPathsAndFiltersBlankSegments() {
        assertThat(ImagePathParser.split(" /a.jpg, ,/b.jpg,  /c.jpg  "))
                .containsExactly("/a.jpg", "/b.jpg", "/c.jpg");
    }
}
