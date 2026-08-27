import type {APIRequestContext, Locator, Page} from "@playwright/test"
import {expect} from "@playwright/test"
import {shared} from "../fixtures/shared"

export const TENANT = process.env.E2E_TENANT ?? "main"

// The generated task-edit form loads its plugin schema asynchronously and
// re-renders (recreating its Monaco instances) once it lands — same for
// switching tabs or expanding an accordion group, which mount fresh fields.
// Wait for the Monaco editor count within scope to settle before touching one,
// so an index-based locator doesn't grab a node that's about to be detached.
export async function waitForMonacoStable(page: Page, scope: Page | Locator = page) {
    const editors = scope.locator(".monaco-editor")
    await expect(async () => {
        const before = await editors.count()
        await page.waitForTimeout(150)
        const after = await editors.count()
        expect(after).toBe(before)
    }).toPass({timeout: 10000})
}

// Click a Monaco field and PROVE the click took the focus before typing —
// the dock's async side-effects (plugin-doc auto-open, schema re-render) can
// steal focus right between a click and the first keystroke, silently sending
// the whole edit nowhere.
export async function replaceMonacoContent(page: Page, editor: Locator, text: string) {
    await expect(async () => {
        await editor.click()
        await expect(editor.locator("textarea.inputarea")).toBeFocused({timeout: 1000})
        await page.keyboard.press("ControlOrMeta+a")
        await page.keyboard.insertText(text)
        // Prove the edit LANDED — an async form re-render can recreate the
        // editor right under a keystroke and silently swallow it.
        await expect(editor).toContainText(text.split("\n")[0], {timeout: 1000})
    }).toPass({timeout: 15000})
}

export async function login(page: Page) {
    await page.goto("/ui")

    // The parked cookie jar can carry the session on its own, in which case the app boots
    // straight into the shell and there is no form to fill — whether it does is a race between
    // the router guard's own auth call and the redirect to the login page. Wait for whichever
    // of the two actually lands, then sign in only if asked to.
    const email = page.getByRole("textbox", {name: "Email"})
    const shell = page.getByRole("button", {name: "Toggle panel"})
    await expect(email.or(shell).first()).toBeVisible({timeout: 30000})

    if (await email.isVisible()) {
        await email.fill(shared.username)
        await page.getByRole("textbox", {name: "Password"}).fill(shared.password)
        await page.getByRole("button", {name: "Login"}).click()
    }
    await expect(shell).toBeVisible({timeout: 30000})
    // The Login button click leaves the cursor parked at a fixed viewport
    // position. If a later hover-highlighted overlay (task picker, command
    // menu) happens to render an option under that exact stale point, the
    // browser fires a real mouseenter and hijacks the highlighted selection
    // away from index 0 with no keyboard action involved. Park it out of the
    // way once, up front, instead of chasing this in every test that opens one.
    await page.mouse.move(0, 0)
}

// Blocks is now the "nocode" tab's engine inside the flow editor's shared
// dock (see MERGE-PLAN.md), not a standalone page — force the flag so this
// stays true regardless of the current rollout default, then open the tab.
// Also force TAB edit mode: this suite asserts the dock-tab editing flow
// (block-editor-task-edit + a "<section> / <id>" tab), so pin it regardless of
// the "Default Task Edit Mode" preference, whose default is MODAL.
export async function openBlockEditor(page: Page, flowId: string) {
    await page.evaluate(() => {
        localStorage.setItem("nocodeEngine", "blocks")
        localStorage.setItem("taskEditDefaultMode", "TAB")
    })
    await page.goto(`/ui/${TENANT}/flows/edit/${shared.namespace}/${flowId}/edit`)
    await page.getByRole("button", {name: "No-code", exact: true}).click()
    await expect(page.locator("[data-test='block-editor']")).toBeVisible()
    await expect(page.locator("[data-block-id]").first()).toBeVisible()
}

// The id of the block currently carrying the keyboard focus ring. The ring
// class lands on the card root for leaves/sentinels and on the header for
// flowable clusters, hence the closest() fallback.
export async function ringId(page: Page): Promise<string | null> {
    return page.evaluate(() => {
        const el = document.querySelector(".block-kbd-focused")
        if (!el) return null
        return el.getAttribute("data-block-id")
            ?? el.closest("[data-block-id]")?.getAttribute("data-block-id")
            ?? null
    })
}

export async function expectRing(page: Page, id: string) {
    await expect(async () => {
        expect(await ringId(page)).toBe(id)
    }).toPass({timeout: 5000})
}

// Reads the ring id, retrying briefly — the ring can lag a tick behind an
// action that creates a brand-new block (insertion, duplication).
export async function waitForRing(page: Page): Promise<string> {
    let id: string | null = null
    await expect(async () => {
        id = await ringId(page)
        expect(id).toBeTruthy()
    }).toPass({timeout: 5000})
    return id as unknown as string
}

// Press ArrowDown/ArrowUp until the ring lands on the target block. Bounded so
// a regression fails fast instead of looping forever.
export async function walkTo(page: Page, targetId: string, direction: "down" | "up" = "down") {
    const key = direction === "down" ? "ArrowDown" : "ArrowUp"
    for (let i = 0; i < 25; i++) {
        if (await ringId(page) === targetId) return
        await page.keyboard.press(key)
    }
    expect(await ringId(page), `walkTo(${targetId}) never reached its target`).toBe(targetId)
}

// Open the command menu and activate its "Go to <section>" entry.
//
// Everything here is scoped to the menu itself. A page-wide getByText for the
// section name can resolve to a block label on the canvas carrying the same
// words — the card sits behind the overlay, which still looks visible to
// Playwright — the same hazard pickTask documents for the insert picker.
//
// Activated by click rather than Enter: the same term also matches
// "Insert <section>", and Enter takes whatever sorted first rather than the
// entry the test asked for.
export async function goToSectionViaPalette(page: Page, section: string) {
    await page.keyboard.press("ControlOrMeta+Shift+p")
    const menu = page.locator("[data-test='block-command-menu']")
    await expect(menu).toBeVisible()

    const input = menu.getByRole("textbox")
    await expect(input).toBeFocused()
    await input.fill(section)

    await menu.getByText(`Go to ${section}`, {exact: true}).click()
    await expect(menu).toBeHidden()
}

// Search the insert picker and confirm the named match. Confirms with a
// click rather than Enter: the picker preselects whatever landed first in
// the filtered list, which for a broad search term (e.g. "if" substring-
// matches dozens of unrelated plugins) is often not the entry the test
// actually asked for. The lookup is scoped to the picker's own listbox — an
// unscoped page-wide text match can resolve to an identically named block
// already on the canvas (e.g. an existing "Log" task) sitting underneath the
// picker overlay, which looks "visible" to Playwright but is a different
// element entirely.
export async function pickTask(page: Page, search: string, optionTitle: string) {
    const input = page.getByPlaceholder("Search or describe a task…")
    await expect(input).toBeVisible()
    await input.fill(search)
    const listbox = page.locator("#block-editor-picker-listbox")
    const option = listbox.getByText(optionTitle, {exact: true}).first()
    await expect(option).toBeVisible()
    await option.click()
    await expect(input).toBeHidden()
}

// Opening a block lands it as a same-place tab in the shared dock (the
// intended default — see "opening blocks by default lands them as same-place
// tabs"), hiding the canvas behind its own "No-code" tab. This returns the
// user to the canvas, like clicking that tab for real.
export async function backToCanvas(page: Page) {
    await page.getByRole("tab", {name: /No-code/}).click()
    await expect(page.locator("[data-test='block-editor-canvas']")).toBeVisible()
}

export async function saveFlow(page: Page) {
    // A toast from an earlier save could satisfy the visibility check before
    // this save's round-trip completes — let it dismiss first.
    await page.getByText("Successfully saved", {exact: false}).first()
        .waitFor({state: "hidden", timeout: 10000}).catch(() => {})
    // Monaco binds Ctrl/Cmd+S for itself, so a Source tab left focused eats the shortcut
    // before the editor shell sees it — save the way a user does, from outside the field.
    await page.evaluate(() => (document.activeElement as HTMLElement | null)?.blur())
    await page.keyboard.press("ControlOrMeta+s")
    await expect(page.getByText("Successfully saved", {exact: false}).first()).toBeVisible()
}

export async function fetchFlowSource(request: APIRequestContext, baseURL: string, flowId: string): Promise<string> {
    const auth = `Basic ${Buffer.from(`${shared.username}:${shared.password}`).toString("base64")}`
    const response = await request.get(
        `${baseURL}/api/v1/flows/${shared.namespace}/${flowId}?source=true`,
        {headers: {Authorization: auth, Accept: "application/json"}},
    )
    expect(response.status()).toBe(200)
    return (await response.json()).source as string
}

// Ids of the canvas cards in DOM order — the user-visible block order.
export async function canvasCardIds(page: Page): Promise<string[]> {
    return page.locator("[data-test='block-card'][data-block-id]")
        .evaluateAll(els => els.map(el => el.getAttribute("data-block-id") ?? ""))
}

// Top-level task ids in persisted YAML order — scoped to the `tasks:` block so
// a same-indent `triggers:`/`errors:`/`finally:` entry is never mistaken for one.
export function taskIdsInOrder(source: string): string[] {
    return sectionTaskIds(source, "tasks")
}

// Same, generalized to any top-level task-list section (errors, finally,
// afterExecution, triggers).
export function sectionTaskIds(source: string, section: string): string[] {
    const withLeadingNewline = `\n${source}`
    const start = withLeadingNewline.indexOf(`\n${section}:`)
    if (start < 0) return []
    const rest = withLeadingNewline.slice(start + 1)
    const headerLength = section.length + 1
    const nextTopLevelKey = rest.slice(headerLength).search(/\n\S/)
    const block = nextTopLevelKey < 0 ? rest : rest.slice(0, nextTopLevelKey + headerLength)
    return [...block.matchAll(/^ {2}- id: (\S+)/gm)].map(m => m[1])
}
