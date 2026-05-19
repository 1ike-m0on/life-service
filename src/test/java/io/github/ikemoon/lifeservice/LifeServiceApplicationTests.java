package io.github.ikemoon.lifeservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LifeServiceApplicationTests {

    @Test
    void javaRuntimeIsAtLeast21() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(21);
    }
}
