import type {Page} from "@playwright/test"
import {expect} from "@playwright/test"

/**
 * Shared plumbing for the AI Copilot specs.
 *
 * The AI endpoints are stubbed with `page.route` so the specs exercise the frontend
 * turn machinery without a configured LLM provider.
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
