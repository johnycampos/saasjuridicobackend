package com.jurisflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jurisflow.modules.mcp.McpTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = {McpTokenAuthFilter.class, ObjectMapper.class})
@TestPropertySource(properties = "app.public-backend-url=https://jurisflow.example.com")
class McpTokenAuthFilterContextTest {

    @MockBean
    private McpTokenService mcpTokenService;

    @Autowired
    private McpTokenAuthFilter filter;

    @Test
    void filterIsInstantiatedBySpringContext() {
        assertThat(filter).isNotNull();
    }
}
