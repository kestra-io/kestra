import {describe, test, expect} from "vitest";
import {buildChildren, limitFor, PAGE_SIZE} from "../../../../../src/components/executions/outputs/transformOutputs";

const moreLabel = (remaining: number) => `Load more (${remaining} left)`;

const countNodes = (nodes: {children: any[]}[]): number =>
    nodes.reduce((total, node) => total + 1 + countNodes(node.children), 0);

const wideOutputs = (count: number) =>
    Object.fromEntries(Array.from({length: count}, (_, i) => [`item_${i}`, `value ${i}`]));

describe("buildChildren", () => {
    test("keeps every key when the level fits in one page", () => {
        const outputs = {a: 1, b: {c: 2}, d: ["one"]};

        const nodes = buildChildren(outputs, "task", {}, moreLabel);

        expect(nodes.map((n) => n.label)).toEqual(["a", "b", "d"]);
        expect(nodes[1].children.map((n) => n.label)).toEqual(["c"]);
        // A single-element array is unwrapped to that element.
        expect(nodes[2].value).toBe("one");
        expect(nodes.some((n) => n.loadMore)).toBe(false);
    });

    // el-cascader-panel renders a whole column at once and is not virtualized, so an unpaged
    // level put one DOM node per output value on screen and blocked the tab for seconds.
    test("pages a wide level and offers the rest behind a load-more row", () => {
        const nodes = buildChildren(wideOutputs(15_000), "task", {}, moreLabel);

        expect(nodes).toHaveLength(PAGE_SIZE + 1);
        expect(nodes.at(-1)).toMatchObject({
            label: "Load more (14800 left)",
            loadMore: true,
            path: "task",
            children: [],
        });
        expect(nodes[0].label).toBe("item_0");
    });

    // The count in the parent row has to be the real one, not however many pages are open.
    test("reports the true key count on the parent, independent of paging", () => {
        const nodes = buildChildren({values: wideOutputs(15_000)}, "task", {}, moreLabel);

        expect(nodes[0].total).toBe(15_000);
        expect(nodes[0].children).toHaveLength(PAGE_SIZE + 1);
    });

    test("a raised limit reveals the next page and keeps the row while keys remain", () => {
        const outputs = wideOutputs(15_000);

        const second = buildChildren(outputs, "task", {task: 2 * PAGE_SIZE}, moreLabel);
        expect(second).toHaveLength(2 * PAGE_SIZE + 1);
        expect(second[2 * PAGE_SIZE - 1].label).toBe(`item_${2 * PAGE_SIZE - 1}`);
        expect(second.at(-1)!.loadMore).toBe(true);

        const all = buildChildren(outputs, "task", {task: 15_000}, moreLabel);
        expect(all).toHaveLength(15_000);
        expect(all.some((n) => n.loadMore)).toBe(false);
    });

    test("pages nested levels too, so total node count stays bounded", () => {
        const wide = wideOutputs(5_000);
        const nodes = buildChildren({a: wide, b: wide}, "task", {}, moreLabel);

        // 2 parents, each with one page plus its load-more row.
        expect(countNodes(nodes)).toBe(2 + 2 * (PAGE_SIZE + 1));
    });

    test("limitFor defaults to one page and honours a raised limit", () => {
        expect(limitFor({}, "task")).toBe(PAGE_SIZE);
        expect(limitFor({task: 600}, "task")).toBe(600);
    });
});
