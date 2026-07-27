import {expect, test} from "@playwright/test"
import {shared} from "./fixtures/shared"

/**
 * End-to-end coverage for the AI Copilot chat drawer.
 *
 * The AI endpoints (`…/ai/threads`, `/chat`, `/confirm`) are stubbed via
 * `page.route` so the test is deterministic and independent of a configured LLM
 * provider — it exercises the *frontend* turn machinery (streaming render, mode
 * selector, proposed-action confirm) end-to-end against the real app shell.
 *
 * Uses explicit `[data-test=…]` locators (the repo doesn't set testIdAttribute).
 */

const sse = (events: [string, unknown][]) =>
    events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join("")

const THREAD = {uid: "e2e-thread", mode: "ASK", status: "IDLE", createdAt: "", updatedAt: ""}

const D = {
    chat: "[data-test=\"copilot-chat\"]",
    input: "[data-test=\"copilot-composer-input\"]",
    send: "[data-test=\"copilot-send\"]",
    card: "[data-test=\"copilot-proposed-action\"]",
    approve: "[data-test=\"copilot-approve\"]",
    reject: "[data-test=\"copilot-reject\"]",
    draft: "[data-test=\"copilot-draft\"]",
    draftOpen: "[data-test=\"copilot-draft-open\"]",
    draftApply: "[data-test=\"copilot-draft-apply\"]",
}

// A minimal, valid-enough flow whose namespace + id the direct-apply path can parse.
const FLOW_YAML = "id: applied\nnamespace: company.team\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: hi"

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

        await test.step("login", async () => {
            await page.goto("/ui")
            await page.getByRole("textbox", {name: "Email"}).fill(shared.username)
            await page.getByRole("textbox", {name: "Password"}).fill(shared.password)
            await page.getByRole("button", {name: "Login"}).click()
            // Reload so the auth cookie applies on a clean load. Let the post-login redirect
            // settle first, and retry (the redirect can interrupt an eager goto).
            await page.waitForTimeout(2000)
            for (let i = 0; i < 3; i++) {
                try { await page.goto("/ui", {waitUntil: "domcontentloaded"}); break } catch { await page.waitForTimeout(1000) }
            }
            await expect(page.getByRole("heading", {name: "Default Dashboard"})).toBeVisible({timeout: 25000})
        })

        await test.step("open the AI copilot dock tab", async () => {
            // The right panel is closed after login — toggle it open first, then select AI.
            const chat = page.locator(D.chat)
            if (!(await chat.isVisible().catch(() => false))) {
                await page.getByRole("button", {name: "Toggle panel"}).click().catch(() => {})
                await page.waitForTimeout(500)
            }
            await page.getByRole("tab", {name: "AI"}).click().catch(() => {})
            await expect(chat).toBeVisible({timeout: 15000})
        })
    })

    test("streams an assistant answer", async ({page}) => {
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

        await page.locator(D.input).fill("What is a trigger?")
        await page.locator(D.send).click()

        await expect(page.locator(D.chat)).toContainText("A trigger starts a flow automatically.")
        await expect(page.locator(D.send)).toBeDisabled() // empty input → disabled
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

        // Default mode is already Edit (EDIT); the stub returns a proposal regardless of mode.
        await page.locator(D.input).fill("restart my failed execution")
        await page.locator(D.send).click()

        const card = page.locator(D.card)
        await expect(card).toBeVisible()
        await expect(card).toContainText("Run restart-execution on exec-1")
        await expect(page.locator(D.input)).toBeDisabled()

        await page.locator(D.approve).click()
        await expect(page.locator(D.chat)).toContainText("Done — it's running again.")
        // Resolved in place: the interactive approve/reject controls go away (the proposal then
        // renders as a read-only history card, which reuses the copilot-proposed-action data-test).
        await expect(page.locator(D.approve)).toBeHidden()
    })

    test("declines a proposed action and hands control back to the composer", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "I'll delete the flow company.team/legacy."}],
                    ["proposed_action", {confirmationId: "cf-rej", tool: "delete-flow", family: "MUTATE", summary: "Delete flow company.team/legacy"}],
                    ["done", {status: "AWAITING_CONFIRMATION"}],
                ]),
            })
        })
        // Capture the confirm request so we can assert the decline is sent as a REJECT decision.
        let confirmBody: {confirmationId?: string; decision?: string} | null = null
        await page.route("**/ai/threads/*/confirm", async (route) => {
            confirmBody = route.request().postDataJSON()
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "Okay, I won't delete it — tell me what to change."}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })

        await page.locator(D.input).fill("delete the legacy flow")
        await page.locator(D.send).click()

        const card = page.locator(D.card)
        await expect(card).toBeVisible()
        await expect(card).toContainText("Delete flow company.team/legacy")
        // An action proposal offers a plain "Reject" (the plan "Reply to revise" wording is for plans).
        await expect(page.locator(D.reject)).toHaveText("Reject")

        await page.locator(D.reject).click()

        // The rejection is sent as REJECT on the same confirmation id, the turn resumes with the
        // acknowledgement, the card is dismissed, and the composer is usable again for a revision.
        await expect(page.locator(D.chat)).toContainText("Okay, I won't delete it — tell me what to change.")
        expect(confirmBody).toMatchObject({confirmationId: "cf-rej", decision: "REJECT"})
        // Resolved in place: the interactive approve/reject controls go away (the proposal then
        // renders as a read-only history card, which reuses the copilot-proposed-action data-test).
        await expect(page.locator(D.approve)).toBeHidden()
        await expect(page.locator(D.input)).toBeEnabled()
    })

    test("revises a proposed plan instead of executing it", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "Here's how I'd approach it."}],
                    // A plan carries steps and NO tool — the card renders as a plan whose decline
                    // action reads "Reply to revise" rather than "Reject".
                    ["proposed_action", {
                        confirmationId: "cf-plan",
                        steps: [
                            {title: "Create the flow", detail: "company.team/report"},
                            {title: "Add a schedule trigger", detail: "daily at 08:00"},
                        ],
                    }],
                    ["done", {status: "AWAITING_CONFIRMATION"}],
                ]),
            })
        })
        let confirmBody: {confirmationId?: string; decision?: string} | null = null
        await page.route("**/ai/threads/*/confirm", async (route) => {
            confirmBody = route.request().postDataJSON()
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "Sure — what should I change about the plan?"}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })

        await page.locator(D.input).fill("set up a daily report flow")
        await page.locator(D.send).click()

        const card = page.locator(D.card)
        await expect(card).toBeVisible()
        await expect(card).toContainText("Proposed plan")
        await expect(card).toContainText("Create the flow")
        // A plan is revised, not rejected — the decline affordance reflects that.
        await expect(page.locator(D.reject)).toHaveText("Reply to revise")

        await page.locator(D.reject).click()

        // Revising a plan is still a REJECT decision; control returns to the composer to re-plan.
        await expect(page.locator(D.chat)).toContainText("Sure — what should I change about the plan?")
        expect(confirmBody).toMatchObject({confirmationId: "cf-plan", decision: "REJECT"})
        // Resolved in place: the interactive approve/reject controls go away (the proposal then
        // renders as a read-only history card, which reuses the copilot-proposed-action data-test).
        await expect(page.locator(D.approve)).toBeHidden()
        await expect(page.locator(D.input)).toBeEnabled()
    })

    test("accepts a flow draft into the flow editor with the drafted YAML", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["token", {text: "Here's a flow draft."}],
                    ["artefact_draft", {draftId: "d1", kind: "FLOW", yaml: "id: demo\nnamespace: company.team\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: hi", valid: true, constraints: null}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })

        await page.locator(D.input).fill("draft me a flow")
        await page.locator(D.send).click()

        const draft = page.locator(D.draft)
        await expect(draft).toBeVisible()
        await expect(draft).toContainText("id: demo")

        await page.locator(D.draftOpen).click()
        // Hands the drafted YAML to the flow create editor via the blueprint-source handoff.
        await page.waitForURL(/\/flows\/new\?.*blueprintId=copilot-draft/)
        // Read the query param via URLSearchParams (form-decodes `+`→space); decodeURIComponent doesn't.
        expect(new URL(page.url()).searchParams.get("blueprintSourceYaml")).toContain("id: demo")
    })

    test("accepts a dashboard draft into the dashboard editor", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["artefact_draft", {draftId: "d2", kind: "DASHBOARD", yaml: "id: my-dash\ntitle: My Dashboard\ncharts: []", valid: true, constraints: null}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })

        await page.locator(D.input).fill("draft me a dashboard")
        await page.locator(D.send).click()
        await expect(page.locator(D.draft)).toBeVisible()

        await page.locator(D.draftOpen).click()
        // The dashboard create editor seeds itself from the `sourceYaml` query.
        await page.waitForURL(/\/dashboards\/new\?.*sourceYaml=/)
        // Read the query param via URLSearchParams (form-decodes `+`→space); decodeURIComponent doesn't.
        expect(new URL(page.url()).searchParams.get("sourceYaml")).toContain("id: my-dash")
    })

    test("applies a flow draft directly and navigates to the created flow", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["artefact_draft", {draftId: "d3", kind: "FLOW", yaml: FLOW_YAML, valid: true, constraints: null}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })
        // Stub the create so nothing is really written; capture the posted body.
        let createdBody: string | null = null
        await page.route((u) => u.pathname.endsWith("/flows"), async (route) => {
            if (route.request().method() === "POST") {
                createdBody = route.request().postData()
                await route.fulfill({status: 200, contentType: "application/json", body: JSON.stringify({id: "applied", namespace: "company.team"})})
            } else {
                await route.continue()
            }
        })

        await page.locator(D.input).fill("apply this flow")
        await page.locator(D.send).click()
        await expect(page.locator(D.draft)).toBeVisible()

        await page.locator(D.draftApply).click()
        // Confirm the "create this flow?" prompt (scope to the modal — the draft card also has an "Apply").
        await page.getByRole("dialog").getByRole("button", {name: "Apply", exact: true}).click()

        // The flow was created from the drafted YAML, then the app navigated to it.
        await page.waitForURL(/\/flows\/edit\/company\.team\/applied/)
        expect(createdBody).toContain("id: applied")
    })

    test("surfaces a notice when a turn returns no output", async ({page}) => {
        // The stream closes with only `done` — no tokens, tools, or proposal.
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([["done", {status: "IDLE"}]]),
            })
        })

        await page.locator(D.input).fill("hello?")
        await page.locator(D.send).click()

        await expect(page.locator("[data-test=\"copilot-notice\"]")).toBeVisible()
    })

    test("carries the current page as a context chip on a detail route", async ({page}) => {
        // Open a flow detail route (the flow need not exist — the chip is derived from the route name
        // + params). Re-open the AI dock on the new page, then assert the context chip reflects it.
        const tenant = new URL(page.url()).pathname.split("/")[2] || "main"
        await page.goto(`/ui/${tenant}/flows/edit/company.team/e2e-context-flow`, {waitUntil: "domcontentloaded"})

        const chat = page.locator(D.chat)
        if (!(await chat.isVisible().catch(() => false))) {
            await page.getByRole("button", {name: "Toggle panel"}).click().catch(() => {})
            await page.waitForTimeout(500)
            await page.getByRole("tab", {name: "AI"}).click().catch(() => {})
        }
        await expect(chat).toBeVisible({timeout: 15000})

        const chip = page.locator("[data-test=\"copilot-context-chip\"]")
        await expect(chip).toBeVisible()
        await expect(chip).toContainText("e2e-context-flow")
    })

    test("sends the mode chosen in the mode selector", async ({page}) => {
        let chatBody: {mode?: string} | null = null
        await page.route("**/ai/threads/*/chat", async (route) => {
            chatBody = route.request().postDataJSON()
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([["token", {text: "planning"}], ["done", {status: "IDLE"}]]),
            })
        })

        // Switch the composer from the default (Edit) to Plan via the mode selector, then send.
        await page.locator("[data-test=\"copilot-mode-selector\"]").click()
        await page.getByRole("menuitem", {name: "Plan", exact: true}).click()
        await page.locator(D.input).fill("plan something")
        await page.locator(D.send).click()

        await expect(page.locator(D.chat)).toContainText("planning")
        // The chosen mode is carried on the chat turn request.
        expect(chatBody).toMatchObject({mode: "PLAN"})
    })

    test("surfaces an error alert when the stream emits an error event", async ({page}) => {
        await page.route("**/ai/threads/*/chat", async (route) => {
            await route.fulfill({
                status: 200,
                contentType: "text/event-stream",
                body: sse([
                    ["error", {message: "The model provider is unavailable."}],
                    ["done", {status: "IDLE"}],
                ]),
            })
        })
        await page.locator(D.input).fill("hello?")
        await page.locator(D.send).click()

        const alert = page.locator("[data-test=\"copilot-error\"]")
        await expect(alert).toBeVisible()
        await expect(alert).toContainText("The model provider is unavailable.")
    })

    test("shows the unavailable state on a failed thread create and clears it on retry", async ({page}) => {
        // A 503 on thread creation (no provider configured) → the dedicated unavailable state,
        // never the global not-found redirect.
        await page.route("**/api/v1/*/ai/threads", async (route) => {
            if (route.request().method() === "POST") {
                await route.fulfill({status: 503, contentType: "application/json", body: "{}"})
            } else {
                await route.continue()
            }
        })
        await page.locator(D.input).fill("hi")
        await page.locator(D.send).click()

        const unavailable = page.locator("[data-test=\"copilot-unavailable\"]")
        await expect(unavailable).toBeVisible()

        // Retry clears the unavailable state and returns to the composer.
        await page.locator("[data-test=\"copilot-unavailable-retry\"]").click()
        await expect(unavailable).toBeHidden()
        await expect(page.locator(D.input)).toBeVisible()
    })
})

/**
 * The full-page `/ai` surface (#7909 onboarding cutover) — the same CopilotChat as the dock, hosted
 * in the "page" layout. No dock here; we navigate straight to the route.
 */
test.describe("AI Copilot — full-page /ai surface", () => {
    test.beforeEach(async ({page}) => {
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
    })

    test("hosts the copilot full-page at /ai with the page-only Need Help section", async ({page}) => {
        const tenant = new URL(page.url()).pathname.split("/")[2] || "main"
        await page.goto(`/ui/${tenant}/ai`, {waitUntil: "domcontentloaded"})

        // The copilot mounts as the full-page host (fresh context → empty state, no dock).
        await expect(page.locator(D.chat)).toBeVisible({timeout: 15000})
        await expect(page.locator(D.input)).toBeVisible()

        // "Need Help?" renders only in the page layout (`v-if="layout === 'page'"`), so its presence
        // confirms this is the full-page surface rather than the right-side dock.
        const help = page.locator("[data-test=\"copilot-help\"]")
        await expect(help).toBeVisible()
        await expect(help).toContainText("Blueprints")
        await expect(help).toContainText("Slack")
    })
})
