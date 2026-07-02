package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SvgSanitizerTest {

    @Test
    void shouldRemoveScriptTags() {
        String svg = "<svg><script>alert(1)</script><path d=\"M0 0\"/></svg>";

        assertThat(SvgSanitizer.sanitize(svg))
            .doesNotContain("<script>")
            .contains("<path d=\"M0 0\"/>");
    }

    @Test
    void shouldRemoveForeignObjectTags() {
        String svg = "<svg><foreignObject><body onload=\"alert(1)\"/></foreignObject><path d=\"M0 0\"/></svg>";

        assertThat(SvgSanitizer.sanitize(svg))
            .doesNotContain("foreignObject")
            .contains("<path d=\"M0 0\"/>");
    }

    @Test
    void shouldRemoveEventHandlerAttributes() {
        String svg = "<svg><path onclick=\"alert(1)\" d=\"M0 0\" onmouseover='alert(2)'/></svg>";

        String sanitized = SvgSanitizer.sanitize(svg);

        assertThat(sanitized).doesNotContain("onclick").doesNotContain("onmouseover");
        assertThat(sanitized).contains("d=\"M0 0\"");
    }

    @Test
    void shouldNeutralizeJavascriptUris() {
        String svg = "<svg><a href=\"javascript:alert(1)\"><path d=\"M0 0\"/></a></svg>";

        assertThat(SvgSanitizer.sanitize(svg)).doesNotContain("javascript:");
    }

    @Test
    void shouldLeaveHarmlessSvgUnchanged() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">" +
            "<linearGradient id=\"a\"/><path fill=\"url(#a)\" d=\"M0 0\"/></svg>";

        assertThat(SvgSanitizer.sanitize(svg)).isEqualTo(svg);
    }
}
