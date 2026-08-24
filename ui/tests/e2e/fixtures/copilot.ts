import type {Page} from "@playwright/test"
import {expect} from "@playwright/test"

/**
 * Shared plumbing for the AI Copilot specs.
 *
 * The AI endpoints are stubbed with `page.route` so the specs exercise the frontend
 * turn machinery without a configured LLM provider - which also means the surface has to be
 * told a provider exists (see `stubAiProviderConfigured`).
 */

export const CHAT = "[data-test=\"copilot-chat\"]"

const PRODUCT_TOUR_STORAGE_KEY = "kestra.productTour.state"

export async function disableProductTour(page: Page) {
    await page.addInitScript((key) => {
        localStorage.setItem(key, JSON.stringify({status: "skipped"}))
    }, PRODUCT_TOUR_STORAGE_KEY)
}

/** Serialises events into the SSE wire format the copilot stream reader expects. */
export const sse = (events: [string, unknown][]) =>
    events.map(([event, data]) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`).join("")

/**
 * Reports whether an AI provider is configured, patching only that flag on the real `/configs`.
 *
 * The copilot renders its "unavailable" state up front when the instance has no provider
 * (kestra-io/kestra#18322), and the e2e backend has none - so a spec that stubs the turn itself
 * has to say `true` here to get the chat surface at all. Stubbing it both ways also keeps the
 * specs independent of whatever the instance under test happens to be configured with.
 */
export async function stubAiProviderConfigured(page: Page, configured: boolean) {
    await page.route("**/api/v1/configs", async (route) => {
        const response = await route.fetch()
        await route.fulfill({json: {...(await response.json()), isAiApiKeyConfigured: configured}})
    })
}

/** Answers thread creation with a fixed thread, leaving every other verb alone. */
export async function stubThreadCreation(page: Page, thread: Record<string, unknown>) {
    await page.route("**/api/v1/*/ai/threads", async (route) => {
        if (route.request().method() === "POST") {
            await route.fulfill({status: 200, contentType: "application/json", body: JSON.stringify(thread)})
        } else {
            await route.continue()
        }
    })
}

/**
 * Brings the copilot dock into view: the right panel starts collapsed, so it has to
 * be toggled open before the AI tab can be selected.
 */
export async function openCopilotDock(page: Page) {
    const chat = page.locator(CHAT)

    if (!(await chat.isVisible())) {
        await page.getByRole("button", {name: "Toggle panel"}).click()
    }
    await page.getByRole("tab", {name: "AI"}).click()

    await expect(chat).toBeVisible()
}
