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
