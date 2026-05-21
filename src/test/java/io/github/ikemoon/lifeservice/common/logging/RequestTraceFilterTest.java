package io.github.ikemoon.lifeservice.common.logging;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTraceFilterTest {

    private final RequestTraceFilter filter = new RequestTraceFilter();

    @Test
    void usesIncomingTraceIdAndWritesResponseHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestTraceFilter.TRACE_ID_HEADER, "trace-123");
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                traceIdInChain.set(MDC.get("traceId")));

        assertThat(traceIdInChain.get()).isEqualTo("trace-123");
        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isEqualTo("trace-123");
        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void generatesTraceIdWhenIncomingHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdInChain = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                traceIdInChain.set(MDC.get("traceId")));

        assertThat(traceIdInChain.get()).isNotBlank();
        assertThat(response.getHeader(RequestTraceFilter.TRACE_ID_HEADER)).isEqualTo(traceIdInChain.get());
        assertThat(MDC.get("traceId")).isNull();
    }
}
