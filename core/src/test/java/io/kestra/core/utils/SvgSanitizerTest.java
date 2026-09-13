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
    void shouldPreserveHarmlessForeignObjectContent() {
        // Figma's SVG export fakes conic/angular gradients this way; several real plugin icons
        // have no visible content without it (a shape with no explicit fill inherits fill="none"
        // from the root <svg> and relies entirely on this foreignObject for its visible color).
        String svg = "<svg><foreignObject><div style=\"background:conic-gradient(red,blue)\"></div></foreignObject>" +
            "<path d=\"M0 0\"/></svg>";

        assertThat(SvgSanitizer.sanitize(svg)).isEqualTo(svg);
    }

    @Test
    void shouldStripEventHandlersInsideForeignObject() {
        String svg = "<svg><foreignObject><body onload=\"alert(1)\"/></foreignObject><path d=\"M0 0\"/></svg>";

        String sanitized = SvgSanitizer.sanitize(svg);

        assertThat(sanitized).contains("foreignObject").doesNotContain("onload").contains("<path d=\"M0 0\"/>");
    }

    @Test
    void shouldRemoveIframeTags() {
        String svg = "<svg><foreignObject><iframe src=\"https://evil.example\"></iframe></foreignObject>" +
            "<path d=\"M0 0\"/></svg>";

        assertThat(SvgSanitizer.sanitize(svg))
            .doesNotContain("<iframe")
            .contains("<path d=\"M0 0\"/>");
    }

    @Test
    void shouldNeutralizeJavascriptSrcUris() {
        String svg = "<svg><foreignObject><iframe src=\"javascript:alert(1)\"></iframe></foreignObject></svg>";

        assertThat(SvgSanitizer.sanitize(svg)).doesNotContain("javascript:");
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
