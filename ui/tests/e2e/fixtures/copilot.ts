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

const NEW_CHAT = "[data-test=\"copilot-new-chat\"]"

/**
 * Returns the worker-shared page to a fresh conversation between tests: closes any
 * dropdown a previous test left open, then clicks "New chat" — a purely client-side
 * reset (`useAiChat.reset()`), so no stubs are needed. The pill is disabled when the
 * chat is already fresh (the worker's first test), and goes back to disabled once the
 * reset lands, which doubles as the wait condition.
 */
export async function resetCopilotChat(page: Page) {
    await page.keyboard.press("Escape")

    const newChat = page.locator(NEW_CHAT)
    if (await newChat.isEnabled().catch(() => false)) {
        await newChat.click()
        await expect(newChat).toBeDisabled()
    }
}

/**
 * Parks the worker-shared page somewhere the copilot specs can run from: boots the
 * SPA on the worker's first test (about:blank), and re-boots when the previous test
 * left the page on an editor route (`…/new`, `…/edit/…`). Editor routes can hold
 * unsaved draft content — e.g. the draft-accept tests land on `…/new?sourceYaml=…` —
 * and the unsaved-changes router guard would block any client-side navigation away
 * from them with an in-DOM confirm. A hard navigation bypasses that guard; the
 * shared-page fixture auto-accepts the native beforeunload confirm it raises instead.
 */
export async function ensureCopilotHost(page: Page) {
    const url = page.url()
    if (!url.includes("/ui") || url.includes("/edit/") || /\/new([/?#]|$)/.test(url)) {
        await page.goto("/ui")
    }
}
