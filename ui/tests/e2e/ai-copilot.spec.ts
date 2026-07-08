import {expect, test} from "@playwright/test"
import {shared} from "./fixtures/shared"

/**
 * End-to-end coverage for the AI Copilot chat drawer.
 *
 * The AI endpoints (`…/ai/threads`, `/chat`, `/confirm`) are stubbed via
 * `page.route` so the test is deterministic and independent of a configured LLM
 * provider — it exercises the *frontend* turn machinery (streaming render, mode
 * selector, proposed-action confirm) end-to-end against the real app shell.
 */

const sse = (events: [string, unknown][]) =>
    events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join("")

const THREAD = {uid: "e2e-thread", mode: "ASK", status: "IDLE", createdAt: "", updatedAt: ""}

test.describe("AI Copilot", () => {
    test.beforeEach(async ({page}) => {
        // Thread creation is always the same stubbed thread.
        await page.route("**/api/v1/*/ai/threads", async (route) => {
            if (route.request().method() === "POST") {
                await route.fulfill({status: 200, contentType: "application/json", body: JSON.stringify(THREAD)})
            } else {
                await route.continue()
            }
        })

        await page.goto("/ui")
        await test.step("login", async () => {
            await page.getByRole("textbox", {name: "Email"}).fill(shared.username)
            await page.getByRole("textbox", {name: "Password"}).fill(shared.password)
            await page.getByRole("button", {name: "Login"}).click()
            await page.waitForURL("**/ui/**")
        })

        await test.step("open the AI copilot dock tab", async () => {
            await page.getByRole("tab", {name: "AI"}).click()
            await expect(page.getByTestId("copilot-chat")).toBeVisible()
        })
    })

    test("streams an assistant answer in Ask mode", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "A trigger "}],
                    ["token", {text: "starts a flow automatically."}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })

        await page.getByTestId("copilot-composer-input").fill("What is a trigger?")
        await page.getByTestId("copilot-send").click()

        await expect(page.getByTestId("copilot-chat")).toContainText("A trigger starts a flow automatically.")
        // Composer is usable again once the turn finishes (status back to IDLE).
        await expect(page.getByTestId("copilot-send")).toBeDisabled() // empty input → disabled
    })

    test("proposes an action and resumes the turn on approve", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "I'll restart the failed execution."}],
                    ["proposed_action", {confirmationId: "cf1", tool: "restart-execution", family: "MUTATE", summary: "Run restart-execution on exec-1"}],
                    ["done", {status: "AWAITING_CONFIRMATION"}],
                ]),
            })
        })
        await page.route("**/ai/threads/*/confirm", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["tool_call", {tool: "restart-execution", family: "MUTATE", arguments: {id: "exec-1"}}],
                    ["tool_result", {tool: "restart-execution", outcome: "ok"}],
                    ["token", {text: "Done — it's running again."}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })

        // Switch to Build mode, then send.
        await page.getByTestId("copilot-mode-selector").getByText("Build").click()
        await page.getByTestId("copilot-composer-input").fill("restart my failed execution")
        await page.getByTestId("copilot-send").click()

        // The proposed-action card appears and the composer is suspended.
        const card = page.getByTestId("copilot-proposed-action")
        await expect(card).toBeVisible()
        await expect(card).toContainText("Run restart-execution on exec-1")
        await expect(page.getByTestId("copilot-composer-input")).toBeDisabled()

        // Approve → confirm stream resumes the turn.
        await page.getByTestId("copilot-approve").click()
        await expect(page.getByTestId("copilot-chat")).toContainText("Done — it's running again.")
        await expect(card).toBeHidden()
    })
})
