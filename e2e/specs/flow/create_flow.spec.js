const { setEditorText } = require("../editor");
const { loadFixture } = require("../fixtures");
const { goto, text, waitFor, click, below, $, toLeftOf } = require("taiko");
const browser = require("../../browser");
const assert = require("assert").strict;
const { flowTools } = require("../tools/flow");

describe("Test on quote wizard", () => {
    beforeAll(async () => {
        await browser.openBrowser();
    });

    afterAll(async () => {
        await browser.closeBrowser();
    });
    describe("As user I manage flows", () => {
        test("I to create a new flow and then delete it", async () => {
            await goto(process.env.TEST_BASE_URL);
            await flowTools.add("io.kestra.test-logs.yml", (flow) => {
                flow.id = `mock-create-${+new Date()}`;
                return flow;
            });
            assert.ok(await text("successfully saved").exists());
            await click("Delete");
            await waitFor(2000);
            assert.ok(await text("Cancel").exists());
            await click("Cancel", below("Confirmation"));
            await waitFor(2000);
            await click("Delete");
            await waitFor(2000);
            assert.ok(await text("OK").exists());
            await click("OK", below("Confirmation"));
            await waitFor(1500);
            assert.ok(await text("successfully deleted!").exists());
        });
        test("I to run a test flow", async () => {
            await flowTools.add("io.kestra.test-logs.yml", (flow) => {
                flow.id = `mock-create-${+new Date()}`;
                return flow;
            });
            await click("New execution");
            await waitFor(2000);
            assert.ok(
                await text("Do you want to execute this flow ?").exists()
            );
            await click("OK", below("Do you want to execute this flow"));
            await waitFor(1500);
            assert.ok(await text("is successfully triggered").exists());
            await waitFor(1500);
            assert.ok(await text("Gantt").isVisible());
            assert.ok(await text("t1", below("Gantt")).exists());
            assert.ok(await text("t2", below("Gantt")).exists());
            assert.ok(await text("t3", below("Gantt")).exists());
            await click("Logs", below("Execution"));
            assert.ok(await text("Attempt 1"), below("Logs"));
            assert.ok(await text("WARN"), below("Logs"));
            assert.ok(
                await text("second io.kestra.core.tasks.debugs.Echo"),
                below("Logs")
            );
            assert.ok(await text("ERROR"), below("Logs"));
            assert.ok(await text("third logs"), below("Logs"));
            assert.ok(await text("Success"), below("Logs"));
        });
    });
});
