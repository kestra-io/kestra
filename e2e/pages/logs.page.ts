import type {Page} from "@playwright/test"
import {expect} from "@playwright/test"

import {BasePage} from "./base.page"

export class LogsPage extends BasePage {

    constructor(public readonly page: Page) {
        super(page)
    }

    async goto() {
        await this.page.goto("/ui/main/logs")

        await expect(this.page.getByRole("heading", {name: "Logs"})).toBeVisible()
    }

    async setFilterByNamespace(namespace: string) {
        const param = "filters[namespace][EQUALS]"
        await this.modifyQueryParam(this.page, {[param]: namespace})
    }

    async setFilterByFlowId(flowId: string) {
        const param = "filters[flowId][EQUALS]"
        await this.modifyQueryParam(this.page, {[param]: flowId})
    }

    async waitForLogRows() {
        await this.page.locator(".log-row").first().waitFor({state: "visible"})
    }

    async getDisplayedLogCount(): Promise<number> {
        await this.waitForLogRows()
        return await this.page.locator(".log-row").count()
    }

    async expectDisplayedLogCountToBe(expectedCount: number) {
        return expect(this.page.locator(".log-row")).toHaveCount(expectedCount)
    }

    async selectLogRowByIndex(index: number = 0) {
        const checkbox = this.page.locator(".log-row").nth(index).locator("label.kel-checkbox")
        await checkbox.waitFor({state: "visible"})
        await checkbox.click()
        await expect(checkbox).toContainClass("is-checked")
    }

    async clickOnSelectAll() {
        await this.page.locator(".ks-bulk-select").getByRole("button", {name: /Select all/}).click()
    }

    async clickOnDeleteAndConfirm() {
        await this.page.locator(".ks-bulk-select").getByRole("button", {name: "Delete"}).click()
        const confirmation = this.page.locator(".kel-message-box")
        await confirmation.getByRole("button", {name: "Delete"}).click()
        await expect(confirmation).toBeHidden()
    }

    async expectBulkSelectBarToShow(selectedCount: number) {
        const bar = this.page.locator(".ks-bulk-select")
        await expect(bar).toBeVisible()
        await expect(bar).toContainText(`${selectedCount} selected`)
    }

    async expectBulkSelectBarHidden() {
        await expect(this.page.locator(".ks-bulk-select")).toHaveCount(0)
    }
}
