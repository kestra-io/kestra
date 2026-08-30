import {describe, expect, it} from "vitest";

import {render} from "../../../src/utils/markdown";

const href = (html: string) => html.match(/<a [^>]*href="([^"]*)"/)?.[1];

describe("markdown url sanitization", () => {
    it("renders a javascript: link as inert text", async () => {
        const html = await render("[Click here](javascript:alert(document.domain))");

        expect(html).not.toContain("<a ");
        expect(html).toContain("Click here");
    });

    it("renders a javascript: link split by a control character as inert text", async () => {
        expect(await render("[Click here](<JaVa\tScRiPt:alert(1)>)")).not.toContain("<a ");
    });

    it("renders a javascript: image split by a control character as inert text", async () => {
        const html = await render("![oops](<JaVa\tScRiPt:alert(1)>)");

        expect(html).not.toContain("<img");
        expect(html).toContain("oops");
    });

    it("renders a data:image/svg+xml image as inert text", async () => {
        expect(await render("![oops](data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=)")).not.toContain("<img");
    });

    it("renders a link with a scheme outside the allowlist as inert text", async () => {
        expect(await render("[Open](intent://scan#Intent;scheme=zxing;end)")).not.toContain("<a ");
    });

    it("keeps a mailto: link", async () => {
        expect(href(await render("[Mail us](mailto:hello@kestra.io)"))).toBe("mailto:hello@kestra.io");
    });

    it("keeps an https link", async () => {
        expect(href(await render("[Docs](https://kestra.io/docs)"))).toBe("https://kestra.io/docs");
    });

    it("keeps an anchor-only link", async () => {
        expect(href(await render("[Section](#my-title)"))).toBe("#my-title");
    });

    it("keeps a relative link", async () => {
        expect(href(await render("[Flows](/ui/flows)"))).toBe("/ui/flows");
    });

    it("keeps an inline base64 image", async () => {
        const url = "data:image/png;base64,iVBORw0KGgo=";

        expect(await render(`![logo](${url})`)).toContain(`src="${url}"`);
    });
});

describe("markdown html sanitization", () => {
    it("strips an event handler from a raw html image", async () => {
        const html = await render("<img src=\"logo.png\" onerror=\"alert(1)\">", {html: true});

        expect(html).toContain("<img src=\"logo.png\"");
        expect(html).not.toContain("onerror");
    });

    it("drops a javascript: href from a raw html anchor", async () => {
        expect(await render("<a href=\"javascript:alert(1)\">bad</a>", {html: true})).not.toContain("javascript:");
    });

    it("drops an entity-encoded javascript: href from a raw html anchor", async () => {
        expect(await render("<a href=\"&#106;avascript:alert(1)\">bad</a>", {html: true})).not.toContain("avascript:");
    });

    it("removes a script tag along with its body", async () => {
        const html = await render("before<script>alert(1)</script>after", {html: true});

        expect(html).not.toContain("alert(1)");
        expect(html).toContain("before");
    });

    it("keeps a youtube embed but drops any other iframe source", async () => {
        const html = await render(
            "<iframe src=\"https://www.youtube.com/embed/x\"></iframe><iframe src=\"https://evil.example/x\"></iframe>",
            {html: true},
        );

        expect(html).toContain("https://www.youtube.com/embed/x");
        expect(html).not.toContain("evil.example");
    });

    it("keeps data and aria attributes but not an inline handler", async () => {
        const html = await render("<div data-foo=\"1\" aria-label=\"l\" onclick=\"alert(1)\">x</div>", {html: true});

        expect(html).toContain("data-foo=\"1\"");
        expect(html).toContain("aria-label=\"l\"");
        expect(html).not.toContain("onclick");
    });

    it("keeps a bare relative href in raw html", async () => {
        expect(await render("<a href=\"getting-started.md\">docs</a>", {html: true})).toContain("href=\"getting-started.md\"");
    });

    it("keeps the column alignment markdown-it puts on table cells", async () => {
        const html = await render("| a |\n|:--|\n| 1 |");

        expect(html).toContain("text-align:left");
    });
});
