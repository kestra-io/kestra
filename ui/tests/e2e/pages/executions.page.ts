import {expect} from "@playwright/test"

import {BasePage, ExecutionState, Pagination} from "./base.page"

export class ExecutionsPage extends BasePage {
    async goto() {
        await this.page.goto("/ui/executions")

        await expect(this.page.getByRole("heading", {name: "Executions"})).toBeVisible()
    }

    async setFilterByFlowId(flowId: string) {
        const param = "filters[flowId][EQUALS]"
        await this.modifyQueryParam(this.page, {[param]: flowId})
    }

    async setFilterByLabel(key: string, value: string) {
        const param = `filters[labels][EQUALS][${key}]`
        await this.modifyQueryParam(this.page, {[param]: value})
    }

    async setFilterByState(state: ExecutionState) {
        const param = "filters[state][EQUALS]"
        await this.modifyQueryParam(this.page, {[param]: state})
    }

    async removeFilterByLabelKey(key: string) {
        await this.removeQueryParam(this.page, `filters[labels][EQUALS][${key}]`)
    }

    async expectCountOfExecutionsToBe(expectedCount: number) {
        return expect(this.page.getByRole("row")).toHaveCount(expectedCount + 1)
    }

    /**
     * Same assertion, but for counts that only settle once the server finishes applying an
     * asynchronous bulk action. The list is fetched on navigation and never polls, so the
     * page has to be reloaded until the backend catches up.
     */
    async expectCountOfExecutionsToBeAfterRefresh(expectedCount: number) {
        await expect(async () => {
            await this.page.reload()
            await expect(this.page.getByRole("row")).toHaveCount(expectedCount + 1, {timeout: 2000})
        }).toPass({timeout: 60000})
    }

    async expectTotalExecutionsCountToBe(expectedCount: number) {
        return expect(this.page.locator(".kel-pagination__total").first()).toHaveText(`Total ${expectedCount}`)
    }

    async getTotalExecutionsCount() {
        const content = await this.page.locator(".kel-pagination__total").first().textContent()
        if (!content) {
            throw new Error("Totals not found")
        }
        const match = content.match(/(\d+)/)
        if (!match) {
            throw new Error(`Cannot parse total from "${content}"`)
        }
        return Number.parseInt(match[1])
    }

    async selectExecutionRowByNumber(rowNumber: number = 1) {
        if (rowNumber < 0) {
            throw new Error("Negative row number is not allowed")
        }
        const checkbox = this.page.getByRole("row").nth(rowNumber).locator("label.kel-checkbox")

        await checkbox.waitFor({state: "visible"})

        // A background data load can re-render the table and drop the selection, so retry
        // until it sticks rather than sleeping first and hoping the reload already happened.
        await expect(async () => {
            await checkbox.click()
            await expect(checkbox).toContainClass("is-checked", {timeout: 1000})
        }).toPass({timeout: 15000})
    }

    async clickOnSelectAll() {
        await this.page.getByRole("button", {name: "Select All"}).click()
    }

    async clickOnSetLabels() {
        await this.page.locator(".ks-bulk-select").locator(".kel-button-group").locator(".kel-dropdown").click()
        await this.page.getByRole("menuitem", {name: "Set labels"}).click()
    }

    async clickOnResume() {
        await this.page.locator(".ks-bulk-select").locator(".kel-button-group").locator(".kel-dropdown").click()
        await this.page.getByRole("menuitem", {name: "Resume"}).click()
        // Confirm
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
    }

    async clickOnRestart() {
        await this.page.getByRole("button", {name: "Restart"}).click()
        // Confirm
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
    }

    async clickOnReplay() {
        await this.page.getByRole("button", {name: "Replay"}).click()
        // Confirm
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
    }

    async setLabelOnSelectedExecutions() {
        await this.page.getByRole("textbox", {name: "Key"}).fill("foo")
        await this.page.getByRole("textbox", {name: "Value"}).fill("baz")
        const labelsAccepted = this.page.waitForResponse(
            (response) => response.url().includes("/executions/labels/by-query") && response.request().method() === "POST",
        )
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
        // Confirm
        await this.page.getByRole("button", {name: "OK", exact: true}).click()

        await labelsAccepted
        await expect(async () => {
            await this.page.reload()
            await expect(this.page.getByRole("row")).toHaveCount(1)
        }).toPass({timeout: 30000})
        await this.page.waitForLoadState("networkidle")
    }

    async setPaginationTo(size: Pagination) {
        // The Element-Plus dropdown is not a `select` - click on text
        await this.page.locator(".kel-pagination .kel-select").click()

        // Wait for the select dropdown to show
        const dropdowns = this.page.locator(".kel-select-dropdown")
        const visibleDropdown = dropdowns.filter({has: this.page.locator(":visible")}).last()

        // Wait for the visible dropdown to actually appear
        await visibleDropdown.waitFor({state: "visible"})

        // Find and click the matching option
        const option = visibleDropdown.locator(".kel-select-dropdown__item", {hasText: `${size} per page`})
        await option.waitFor({state: "visible"})
        await option.click()
    }
}
