package io.github.ikemoon.lifeservice.common.exception;

import io.github.ikemoon.lifeservice.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesMissingStaticResourceAsNotFound() {
        ApiResponse<Void> response = handler.handleNoResourceFoundException(
                new NoResourceFoundException(HttpMethod.GET, "favicon.ico"));

        assertThat(response.success()).isFalse();
        assertThat(response.code()).isEqualTo(ErrorCode.NOT_FOUND.name());
        assertThat(response.message()).isEqualTo("Resource not found");
    }
}
