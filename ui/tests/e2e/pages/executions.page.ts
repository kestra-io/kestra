import {expect} from "@playwright/test"

import {BasePage, ExecutionState, Pagination} from "./base.page"

export class ExecutionsPage extends BasePage {
    /** The "Total N" counter of the pagination bar, scoped to the outermost table. */
    private get totalLocator() {
        return this.page.locator(".kel-pagination__total").first()
    }

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
     * Same assertion, for counts that only settle once the server finishes an asynchronous bulk
     * action: the list is fetched on navigation and never polls, so it has to be reloaded until the
     * backend catches up. Asserts the pagination total, not the row count, which also depends on
     * the selected page size.
     */
    async expectTotalExecutionsCountToBeAfterRefresh(expectedCount: number) {
        await expect(async () => {
            await this.page.reload()
            // Short per-attempt timeout: the retry above is what waits, not this assertion.
            await expect(this.totalLocator).toHaveText(`Total ${expectedCount}`, {timeout: 2000})
        }).toPass({timeout: 60000})
    }

    async expectTotalExecutionsCountToBe(expectedCount: number) {
        return expect(this.totalLocator).toHaveText(`Total ${expectedCount}`)
    }

    async getTotalExecutionsCount() {
        const content = await this.totalLocator.textContent()
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

        // A background data load can re-render the table and drop the selection, so retry.
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
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
    }

    async clickOnRestart() {
        await this.page.getByRole("button", {name: "Restart"}).click()
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
    }

    async clickOnReplay() {
        await this.page.getByRole("button", {name: "Replay"}).click()
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
    }

    async setLabelOnSelectedExecutions() {
        await this.page.getByRole("textbox", {name: "Key"}).fill("foo")
        await this.page.getByRole("textbox", {name: "Value"}).fill("baz")
        const labelsAccepted = this.page.waitForResponse(
            (response) => response.url().includes("/executions/labels/by-query") && response.request().method() === "POST",
        )
        await this.page.getByRole("button", {name: "OK", exact: true}).click()
        await this.page.getByRole("button", {name: "OK", exact: true}).click()

        await labelsAccepted
        await expect(async () => {
            await this.page.reload()
            await expect(this.page.getByRole("row")).toHaveCount(1)
        }).toPass({timeout: 30000})
    }

    /*
     * Through the query param rather than the pagination dropdown, which cannot be awaited: its
     * handler pushes `size=` onto the route without awaiting the async router guards, and Element
     * Plus swallows a click on the size already in effect, leaving no event to wait on at all.
     */
    async setPaginationTo(size: Pagination) {
        await this.modifyQueryParam(this.page, {size: String(size)})
    }
}
