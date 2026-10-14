import {expect, type Locator} from "@playwright/test"

import {BasePage} from "./base.page"

export type KvType = "STRING" | "NUMBER" | "BOOLEAN" | "DATETIME" | "DATE" | "DURATION" | "JSON"

/**
 * Drives the KV drawer. Every type gets its own value control, so filling and reading
 * back a value both dispatch on the type under test.
 */
export class KvPage extends BasePage {
    private readonly value = this.page.locator("[data-test='kv-value']")

    async goto() {
        await this.page.goto("/ui/main/kv")

        await expect(this.page.getByRole("heading", {name: "KV Store"})).toBeVisible()
    }

    async openCreateDrawer() {
        await this.page.locator("[data-test='kv-add']").click()

        await expect(this.page.locator("[data-test='kv-key'] input")).toBeVisible()
    }

    async openEditDrawer(key: string) {
        await this.row(key).locator("[data-test='kv-edit']").click()

        await expect(this.page.locator("[data-test='kv-key'] input")).toHaveValue(key)
    }

    row(key: string): Locator {
        return this.page.getByRole("row").filter({hasText: key})
    }

    async createKv(namespace: string, key: string, type: KvType, value: string) {
        await this.openCreateDrawer()
        await this.pickOption("kv-namespace", namespace)
        await this.page.locator("[data-test='kv-key'] input").fill(key)
        await this.pickOption("kv-type", type)
        await this.fillValue(type, value)
        await this.save()
    }

    async save() {
        await this.page.locator("[data-test='kv-save']").click()

        await expect(this.page.locator("[data-test='kv-save']")).toBeHidden()
    }

    async fillValue(type: KvType, value: string) {
        switch (type) {
        case "STRING":
            await this.value.locator("textarea").fill(value)
            break
        case "NUMBER":
            await this.value.locator("input").fill(value)
            break
        case "BOOLEAN":
            if (value === "true") {
                await this.value.getByRole("switch").press("Space")
            }
            break
        case "DATETIME":
        case "DATE":
            await this.value.locator("input").fill(value)
            await this.page.keyboard.press("Enter")
            break
        case "DURATION":
            await this.value.locator("[data-test='custom-duration']").fill(value)
            break
        case "JSON":
            await this.fillEditor(value)
            break
        }
    }

    async readValue(type: KvType): Promise<string> {
        switch (type) {
        case "STRING":
            return this.value.locator("textarea").inputValue()
        case "BOOLEAN":
            return String(await this.value.getByRole("switch").getAttribute("aria-checked") === "true")
        case "DURATION":
            return this.value.locator("[data-test='custom-duration']").inputValue()
        case "JSON":
            return (await this.value.locator(".view-lines").innerText()).replace(/ /g, " ").trim()
        default:
            return this.value.locator("input").inputValue()
        }
    }

    expirationHint(): Locator {
        return this.page.locator("[data-test='kv-expiration-hint']")
    }

    async readTtl(): Promise<string> {
        return this.page.locator("[data-test='kv-ttl'] [data-test='custom-duration']").inputValue()
    }

    async readType(): Promise<string> {
        return (await this.page.locator("[data-test='kv-type']").innerText()).trim()
    }

    private async pickOption(testId: string, label: string) {
        const combobox = this.page.locator(`[data-test='${testId}']`).getByRole("combobox")

        await combobox.focus()
        await combobox.press("Enter")

        const listbox = await combobox.getAttribute("aria-controls")
        await this.page.locator(`#${listbox}`).getByRole("option", {name: label, exact: true}).click()
    }

    private async fillEditor(value: string) {
        const editor = this.value.locator(".monaco-editor").first()
        await expect(editor).toBeVisible()

        await editor.click()
        await this.page.keyboard.press("ControlOrMeta+a")
        await this.page.keyboard.insertText(value)
    }
}
