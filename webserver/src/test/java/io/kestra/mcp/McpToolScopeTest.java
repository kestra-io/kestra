package io.kestra.mcp;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.AccessScope;

import static org.assertj.core.api.Assertions.assertThat;

class McpToolScopeTest {
    @Test
    void shouldCoverTheSubtreeButNotAPrefixSiblingWhenScopedToNamespaces() {
        AccessScope scope = AccessScope.namespaces(List.of("io.kestra"));

        assertThat(McpToolScope.allows(scope, "io.kestra")).isTrue();
        assertThat(McpToolScope.allows(scope, "io.kestra.team")).isTrue();
        assertThat(McpToolScope.allows(scope, "io.kestrax")).isFalse();
        assertThat(McpToolScope.allows(scope, "com.other")).isFalse();
    }

    @Test
    void shouldAllowEverythingWhenGlobalAndNothingWhenDenyAll() {
        assertThat(McpToolScope.allows(AccessScope.global(), "io.kestra")).isTrue();
        assertThat(McpToolScope.allows(AccessScope.denyAll(), "io.kestra")).isFalse();
    }

    @Test
    void shouldShareACacheKeyWhenTheSameNamespacesAreGrantedInAnotherOrder() {
        assertThat(McpToolScope.cacheKey(AccessScope.namespaces(List.of("b", "a"))))
            .isEqualTo(McpToolScope.cacheKey(AccessScope.namespaces(List.of("a", "b"))))
            .isNotEqualTo(McpToolScope.cacheKey(AccessScope.global()));
    }
}
