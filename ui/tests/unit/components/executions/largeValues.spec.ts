import {describe, test, expect, vi, afterEach} from "vitest";
import {MAX_INLINE_BYTES, isTooLargeToRender, downloadJson} from "../../../../src/components/executions/largeValues";

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe("isTooLargeToRender", () => {
    test("passes anything up to the budget and rejects past it", () => {
        expect(isTooLargeToRender(undefined)).toBe(false);
        expect(isTooLargeToRender("")).toBe(false);
        expect(isTooLargeToRender("x".repeat(MAX_INLINE_BYTES))).toBe(false);
        expect(isTooLargeToRender("x".repeat(MAX_INLINE_BYTES + 1))).toBe(true);
    });
});

describe("downloadJson", () => {
    test("hands the browser the full text under the given name, and releases the URL", async () => {
        const text = JSON.stringify({items: Array.from({length: 500}, (_, i) => i)}, null, 2);

        let captured: Blob | undefined;
        const revokeObjectURL = vi.fn();
        vi.stubGlobal("URL", {
            createObjectURL: vi.fn((blob: Blob) => {
                captured = blob;
                return "blob:mock";
            }),
            revokeObjectURL,
        });

        const anchor = document.createElement("a");
        const click = vi.spyOn(anchor, "click").mockImplementation(() => {});
        vi.spyOn(document, "createElement").mockImplementation((tag: string) => {
            if (tag === "a") return anchor;
            return Object.getPrototypeOf(document).createElement.call(document, tag);
        });

        downloadJson(text, "output-exec-1.json");

        expect(click).toHaveBeenCalledOnce();
        expect(anchor.download).toBe("output-exec-1.json");
        expect(anchor.href).toBe("blob:mock");
        expect(captured?.type).toBe("application/json");
        await expect(captured!.text()).resolves.toBe(text);
        expect(revokeObjectURL).toHaveBeenCalledWith("blob:mock");
    });
});
