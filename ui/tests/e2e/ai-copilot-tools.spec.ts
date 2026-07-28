import {expect, test} from "@playwright/test"
import {shared} from "./fixtures/shared"

/**
 * Per-tool end-to-end coverage for the AI Copilot v2 tool catalog.
 *
 * The `…/chat` SSE stream is stubbed via `page.route` so each tool is exercised
 * deterministically, without a configured LLM provider — this asserts the *frontend*
 * renders each tool's activity (tool_call / tool_result / artefact_draft) end-to-end
 * through the real app shell. A companion unit spec (copilotToolCatalog.spec.ts) covers
 * the same catalog at the reducer/component level.
 *
 * When the backend adds a tool, add it to PLATFORM_TOOLS / AUTHORING_TOOLS below.
 */

const sse = (events: [string, unknown][]) =>
    events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join("")

const THREAD = {uid: "e2e-thread", mode: "EDIT", status: "IDLE", createdAt: "", updatedAt: ""}

const D = {
    chat: "[data-test=\"copilot-chat\"]",
    input: "[data-test=\"copilot-composer-input\"]",
    send: "[data-test=\"copilot-send\"]",
    toolCall: "[data-test=\"copilot-tool-call\"]",
    toolResult: "[data-test=\"copilot-tool-result\"]",
    draft: "[data-test=\"copilot-draft\"]",
}

const PLATFORM_TOOLS = [
    {tool: "read-execution", family: "READ"},
    {tool: "list-executions", family: "READ"},
    {tool: "read-execution-logs", family: "READ"},
    {tool: "read-flow", family: "READ"},
    {tool: "list-flows", family: "READ"},
    {tool: "search-plugins", family: "READ"},
    {tool: "get-plugin-schema", family: "READ"},
    {tool: "validate-flow", family: "READ"},
    {tool: "restart-execution", family: "ACT"},
]

const AUTHORING_TOOLS = [
    {tool: "author-flow", kind: "FLOW", title: "Proposed flow"},
    {tool: "author-dashboard", kind: "DASHBOARD", title: "Proposed dashboard"},
    {tool: "author-app", kind: "APP", title: "Proposed app"},
]

test.describe("AI Copilot v2 — tool catalog", () => {
    test.beforeEach(async ({page}) => {
        await page.route("**/api/v1/*/ai/threads", async (route) => {
            if (route.request().method() === "POST") {
                await route.fulfill({status: 200, contentType: "application/json", body: JSON.stringify(THREAD)})
            } else {
                await route.continue()
            }
        })

        await test.step("login", async () => {
            await page.goto("/ui")
            await page.getByRole("textbox", {name: "Email"}).fill(shared.username)
            await page.getByRole("textbox", {name: "Password"}).fill(shared.password)
            await page.getByRole("button", {name: "Login"}).click()
            await page.waitForTimeout(2000)
            for (let i = 0; i < 3; i++) {
                try { await page.goto("/ui", {waitUntil: "domcontentloaded"}); break } catch { await page.waitForTimeout(1000) }
            }
            await expect(page.getByRole("heading", {name: "Default Dashboard"})).toBeVisible({timeout: 25000})
        })

        await test.step("open the AI copilot dock tab", async () => {
            const chat = page.locator(D.chat)
            if (!(await chat.isVisible().catch(() => false))) {
                await page.getByRole("button", {name: "Toggle panel"}).click().catch(() => {})
                await page.waitForTimeout(500)
            }
            await page.getByRole("tab", {name: "AI"}).click().catch(() => {})
            await expect(chat).toBeVisible({timeout: 15000})
        })
    })

    // Stub the next chat turn's SSE stream, then send a prompt.
    async function runTurn(page: import("@playwright/test").Page, events: [string, unknown][], prompt: string) {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({status: 200, contentType: "text/event-stream", body: sse(events)})
        })
        await page.locator(D.input).fill(prompt)
        await page.locator(D.send).click()
    }

    for (const {tool, family} of PLATFORM_TOOLS) {
        test(`renders the "${tool}" tool activity`, async ({page}) => {
            await runTurn(page, [
                ["tool_call", {tool, kind: "PLATFORM", family, arguments: {}}],
                ["tool_result", {tool, outcome: "ok"}],
                ["token", {text: "Done."}],
                ["done", {status: "IDLE"}],
            ], `use the ${tool} tool`)

            await expect(page.locator(D.toolCall).filter({hasText: tool})).toBeVisible({timeout: 15000})
            await expect(page.locator(D.toolResult).filter({hasText: tool})).toBeVisible({timeout: 15000})
        })
    }

    for (const {tool, kind, title} of AUTHORING_TOOLS) {
        test(`renders the "${tool}" authoring draft (${kind})`, async ({page}) => {
            await runTurn(page, [
                ["tool_call", {tool, kind: "AUTHORING", arguments: {}}],
                ["artefact_draft", {draftId: tool, kind, yaml: `id: ${tool}`, valid: true, constraints: null}],
                ["tool_result", {tool, outcome: "ok"}],
                ["done", {status: "IDLE"}],
            ], `use the ${tool} tool`)

            const draft = page.locator(D.draft)
            await expect(draft).toBeVisible({timeout: 15000})
            await expect(draft).toContainText(title)
            await expect(draft).toContainText(`id: ${tool}`)
        })
    }

    test("renders every tool in a single multi-tool turn", async ({page}) => {
        const events: [string, unknown][] = []
        for (const {tool, family} of PLATFORM_TOOLS) {
            events.push(["tool_call", {tool, kind: "PLATFORM", family, arguments: {}}])
            events.push(["tool_result", {tool, outcome: "ok"}])
        }
        for (const {tool, kind} of AUTHORING_TOOLS) {
            events.push(["tool_call", {tool, kind: "AUTHORING", arguments: {}}])
            events.push(["artefact_draft", {draftId: tool, kind, yaml: `id: ${tool}`, valid: true, constraints: null}])
            events.push(["tool_result", {tool, outcome: "ok"}])
        }
        events.push(["done", {status: "IDLE"}])

        await runTurn(page, events, "exercise every tool")

        await expect(page.locator(D.toolCall)).toHaveCount(PLATFORM_TOOLS.length + AUTHORING_TOOLS.length, {timeout: 15000})
        await expect(page.locator(D.draft)).toHaveCount(AUTHORING_TOOLS.length)
    })
})
