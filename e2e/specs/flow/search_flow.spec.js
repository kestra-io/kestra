const { flowTools } = require("../tools/flow");
const {
    goto,
    write,
    text,
    waitFor,
    clear,
    into,
    textBox,
} = require("taiko");
const browser = require("../../browser");
const assert = require("assert").strict;

describe("Test on quote wizard", () => {
    beforeAll(async () => {
        await browser.openBrowser();
    });

    afterAll(async () => {
        await browser.closeBrowser();
    });

    describe("As user I manage flows", () => {
        test("I want search flows from search bar", async () => {
            const now = +new Date();
            await flowTools.add("io.kestra.test-mock.yml", (flow) => {
                flow.id = `mock-searchable-1-${now}`;
                return flow;
            });
            await flowTools.add("io.kestra.test-mock.yml", (flow) => {
                flow.id = `mock-searchable-2-${now}`;
                return flow;
            });
            await goto(process.env.TEST_BASE_URL + "/ui/flows");
            await write(
                "mock-searchable",
                into(textBox({ placeholder: "Search" }))
            );
            await assert.ok(await text("mock-searchable-1-").isVisible());
            await assert.ok(await text("mock-searchable-2-").isVisible());
            await clear(textBox({ placeholder: "Search" }));
            await write(
                `mock-searchable-1-${now}`,
                into(textBox({ placeholder: "Search" }))
            );
            await assert.ok(await text(`mock-searchable-1-${now}`).isVisible());
            await waitFor(1000);
            await assert.ok(
                !(await text(`mock-searchable-2-${now}`).exists(0, 0))
            );
            await clear(textBox({ placeholder: "Search" }));
            await write(
                "flow-not-found",
                into(textBox({ placeholder: "Search" }))
            );
        });
    });
});
