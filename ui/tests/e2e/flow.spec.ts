import {expect, test} from "@playwright/test";
import {v4 as uuidv4} from "uuid";
import fs from "fs";
import {fileURLToPath} from "url";
import path from "path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const helloFlowYaml = fs.readFileSync(
    path.resolve(__dirname, "./fixtures/flows/hello.yaml"),
    "utf-8"
);
const helloFlowId = "my-hello-flow-1";

test.describe("Flow Page", () => {

    let testUUID = "";

    test.beforeEach(async () => {
        testUUID = uuidv4().replace(/-/g, "_");
    });
    test("should create and execute the example Flow", async ({page}) => {

        await page.goto("/ui");

        await test.step("login in", async () => {
            await page.getByRole("textbox", {name: "Email"}).fill("user@kestra.io");
            await page.getByRole("textbox", {name: "Password"}).fill("DemoDemo1");
            await page.getByRole("button", {name: "Login"}).click();
            await expect(page.getByRole("heading", {name: "Overview"})).toBeVisible();
        });

        await page.goto("/ui/flows");

        await test.step("create the example Flow", async () => {

            await page.getByRole("button", {name: "Create"}).click();

            await page.getByRole("button", {name: "Save"}).click();
            await page.getByRole("link", {name: "Overview"}).click();
        });

        await test.step("execute the flow", async () => {

            await expect(page.locator("section").getByRole("button", {name: "Execute"})).toBeVisible();
            await page.locator("section").getByRole("button", {name: "Execute"}).click();

            await page.getByRole("dialog").getByRole("button", {name: "Execute"}).click();

            await page.getByText("hello").click();// default task log
            await expect(page.getByText("Hello World!")).toBeVisible();
        });
    });

    // TODO unflaky this test on CI
    test.skip("should create and execute a Flow with input", async ({page, context}) => {
        await context.grantPermissions(["clipboard-read", "clipboard-write"]);

        const flowId = `flowId_${testUUID}`;
        const flowYaml = helloFlowYaml.replace(helloFlowId, flowId);

        await page.goto("/ui/flows");

        await test.step("create a the flow by pasting the YAML", async () => {
            // TODO login in
            await page.getByRole("button", {name: "Create"}).click();
            await page.waitForURL("**/flows/new");
            await page.getByTestId("monaco-editor").getByText("Hello World").isVisible();

            const monacoEditor = page.getByTestId("monaco-editor-hidden-synced-textarea");
            await monacoEditor.clear({force: true});
            await expect(page.getByTestId("monaco-editor").getByText("Hello World")).not.toBeVisible();
            await monacoEditor.fill(flowYaml, {force: true});
            await page.getByRole("button", {name: "Actions"}).click();
            await expect(page.getByTestId("monaco-editor").getByText(flowId)).toBeVisible();

            await page.getByRole("button", {name: "Save"}).click();
            await page.getByRole("link", {name: "Overview"}).click();
            await expect(page.locator("#app").getByText(flowId)).toBeVisible();
        });

        const inputValue = "my-input_" + testUUID;
        await test.step("execute the flow with INPUT_A: " + inputValue, async () => {

            await page.getByRole("button", {name: "Execute"}).first().click();

            await expect(page.getByRole("dialog").getByText("INPUT_A")).toBeVisible();
            await page.getByRole("dialog").getByRole("textbox", {name: "Editor content"} ).fill(inputValue);
            await page.getByRole("dialog").getByRole("button", {name: "Execute"}).click();

            await page.getByText("log_hello_task").click();
            await expect(page.getByText(inputValue)).toBeVisible();
        });
    });
});