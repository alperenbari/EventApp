package com.event.event_ticket_app.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    @Test
    void blocksRequestsOverLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(2, 60_000);

        assertThat(statusFor(filter, "1.2.3.4")).isEqualTo(200);
        assertThat(statusFor(filter, "1.2.3.4")).isEqualTo(200);
        assertThat(statusFor(filter, "1.2.3.4")).isEqualTo(429);
    }

    @Test
    void allowsDifferentIpsIndependently() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 60_000);

        assertThat(statusFor(filter, "1.1.1.1")).isEqualTo(200);
        assertThat(statusFor(filter, "2.2.2.2")).isEqualTo(200);
    }

    @Test
    void evictsExpiredWindows() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(10, 10);

        statusFor(filter, "3.3.3.3");
        assertThat(filter.trackedIpCount()).isEqualTo(1);

        Thread.sleep(20);
        filter.evictExpiredWindows();

        assertThat(filter.trackedIpCount()).isEqualTo(0);
    }

    private int statusFor(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
