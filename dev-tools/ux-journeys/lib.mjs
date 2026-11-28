import fs from "node:fs/promises"
import path from "node:path"
import {chromium} from "playwright"

const VIEWPORT = {width: 1440, height: 900}
const HIDE_TOASTS = ".el-notification,.el-message,.kel-notification,.kel-message{display:none!important}"

export async function settle(page, ms = 800) {
    await page.waitForLoadState("networkidle", {timeout: 4000}).catch(() => {})
    await page.waitForTimeout(ms)
}

export function launchBrowser() {
    return chromium.launch()
}

export async function newPage(browser, {baseUrl, storageState}) {
    const context = await browser.newContext({
        baseURL: baseUrl,
        viewport: VIEWPORT,
        deviceScaleFactor: 1,
        colorScheme: "light",
        storageState,
        serviceWorkers: "block",
    })
    const page = await context.newPage()
    page.setDefaultTimeout(20000)
    page.on("dialog", (dialog) => dialog.accept().catch(() => {}))
    return page
}

/**
 * Returns `shot(stepKey, options)`, which writes the screenshot of that step to the file the journey declares for it.
 * By default it waits for the network to go idle plus `wait` ms and hides toasts; `instant` skips the wait.
 */
export async function shooter(page, journey, outDir) {
    const dir = path.join(outDir, "shots", journey.id)
    await fs.rm(dir, {recursive: true, force: true})
    await fs.mkdir(dir, {recursive: true})
    const files = new Map(journey.steps.map((step) => [step.key, step.file]))

    return async (key, {wait = 800, instant = false, toasts = false} = {}) => {
        if (!files.has(key)) {
            throw new Error(`Journey '${journey.id}' declares no step '${key}'.`)
        }
        await page.locator("#loader-wrapper").waitFor({state: "hidden", timeout: 60000})
        if (!instant) {
            await settle(page, wait)
        }
        await page.screenshot({
            path: path.join(dir, files.get(key)),
            type: "jpeg",
            quality: 80,
            style: toasts ? undefined : HIDE_TOASTS,
        })
    }
}

export async function login(page, {username, password}) {
    await page.goto("/ui/login")
    await page.getByRole("textbox", {name: "Email"}).fill(username)
    await page.getByRole("textbox", {name: "Password"}).fill(password)
    await page.getByRole("button", {name: "Login"}).click()
    await page.waitForURL((url) => !url.pathname.includes("/login"))
    await settle(page, 1000)
}

export async function skipProductTour(page) {
    await page.goto("/ui/main/ai")
    await settle(page, 1500)
    const skip = page.getByRole("button", {name: "Skip tour"})
    if (await skip.isVisible()) {
        await skip.click()
        await settle(page, 1000)
    }
}

/** Replaces the whole content of the first Monaco editor on the page, the way a user paste would. */
export async function pasteInEditor(page, text) {
    await page.locator(".monaco-editor .view-lines").first().click()
    await page.keyboard.press("Control+A")
    await page.keyboard.press("Delete")
    await page.evaluate((content) => {
        const textarea = document.querySelector(".monaco-editor textarea")
        textarea.focus()
        const data = new DataTransfer()
        data.setData("text/plain", content)
        textarea.dispatchEvent(new ClipboardEvent("paste", {clipboardData: data, bubbles: true, cancelable: true}))
    }, text)
}

export function visibleDropdownItems(page) {
    return page.locator(".kel-select-dropdown__item").filter({visible: true})
}

export async function waitForInstance(baseUrl, timeoutMs) {
    const deadline = Date.now() + timeoutMs
    for (;;) {
        try {
            const response = await fetch(`${baseUrl}/api/v1/configs/login`)
            if (response.ok) {
                return response.json()
            }
        } catch {
            // not listening yet
        }
        if (Date.now() > deadline) {
            throw new Error(`Kestra did not answer on ${baseUrl} within ${timeoutMs / 1000} seconds.`)
        }
        await new Promise((resolve) => setTimeout(resolve, 2000))
    }
}

export function apiClient({baseUrl, username, password}) {
    const authorization = `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`

    async function request(method, apiPath, {body, contentType, optional = false} = {}) {
        const headers = {Authorization: authorization, Accept: "application/json"}
        if (contentType) {
            headers["Content-Type"] = contentType
        }
        const response = await fetch(`${baseUrl}/api/v1${apiPath}`, {method, headers, body})
        if (optional && response.status === 404) {
            return undefined
        }
        if (!response.ok) {
            throw new Error(`${method} ${apiPath} failed with HTTP ${response.status}: ${await response.text()}`)
        }
        const text = await response.text()
        return text ? JSON.parse(text) : undefined
    }

    return {
        get: (apiPath, options) => request("GET", apiPath, options),
        post: (apiPath, options) => request("POST", apiPath, options),
        put: (apiPath, options) => request("PUT", apiPath, options),
        async latestExecution(namespace, flowId, state) {
            const query = new URLSearchParams({
                "filters[namespace][EQUALS]": namespace,
                "filters[flowId][EQUALS]": flowId,
                "size": "1",
                "sort": "state.startDate:desc",
            })
            if (state) {
                query.set("filters[state][IN]", state)
            }
            const {results} = await request("GET", `/main/executions/search?${query}`)
            if (!results.length) {
                throw new Error(`No ${state ? `${state} ` : ""}execution of ${namespace}.${flowId} was found.`)
            }
            return results[0]
        },
    }
}
