import type {Page} from "@playwright/test";
import {expect} from "@playwright/test";

import {BasePage, ExecutionState, Pagination} from "./base.page";

export class ExecutionsPage extends BasePage {

    constructor(public readonly page: Page) {
        super(page);
    }

    async goto() {
        await this.login();
        await this.page.goto("/ui/main/executions");

        await expect(this.page.getByRole("heading", {name: "Executions"})).toBeVisible();
    }

    async setFilterByFlowId(flowId: string) {
        const param = "filters[flowId][EQUALS]";
        await this.removeQueryParam(this.page, param);
        await this.addQueryParam(this.page, param, flowId);
    }

    async setFilterByLabel(key: string, value: string) {
        const param = `filters[labels][EQUALS][${key}]`;
        await this.removeQueryParam(this.page, param);
        await this.addQueryParam(this.page, param, value);
    }

    async setFilterByState(state: ExecutionState) {
        const param = "filters[state][EQUALS]";
        await this.removeQueryParam(this.page, param);
        await this.addQueryParam(this.page, param, state);
    }

    async removeFilterByLabelKey(key: string) {
        await this.removeQueryParam(this.page, `filters[labels][EQUALS][${key}]`);
    }

    async getCountOfDisplayedExecutions() {
        const rows = this.page.getByRole("row");
        const count = await rows.count();
        return Math.max(0, count - 1);
    }

    async waitForDisplayedExecutionsCountAtLeast(expected: number, timeoutMs: number = 10000) {
        const start = Date.now();
        while (Date.now() - start < timeoutMs) {
            const count = await this.getCountOfDisplayedExecutions();
            if (count >= expected) {
                return;
            }
            await this.page.waitForTimeout(250);
        }
        const finalCount = await this.getCountOfDisplayedExecutions();
        throw new Error(`Expected at least ${expected} executions displayed but only saw ${finalCount}`);
    }

    async waitForDisplayedExecutionsCountBelow(maxExpected: number, timeoutMs: number = 10000) {
        const start = Date.now();
        while (Date.now() - start < timeoutMs) {
            const count = await this.getCountOfDisplayedExecutions();
            if (count <= maxExpected) {
                return;
            }
            await this.page.waitForTimeout(250);
        }
        const finalCount = await this.getCountOfDisplayedExecutions();
        throw new Error(`Expected at most ${maxExpected} executions displayed but saw ${finalCount}`);
    }

    async getTotalExecutionsCount() {
        const content = await this.page.getByText(/Total:/).first().textContent();
        if (!content) {
            throw new Error("Totals not found");
        }
        return Number.parseInt(content.split(":")[1].trim());
    }

    async selectExecutionRowByNumber(rowNumber: number = 1) {
        if (rowNumber < 0) {
            throw new Error("Negative row number is not allowed");
        }
        const checkbox = this.page.getByRole("row").nth(rowNumber).locator("label.el-checkbox");

        await checkbox.waitFor({state: "visible"});
        await checkbox.click();
        await expect(checkbox).toHaveClass(/is-checked/);
    }

    async clickOnSelectAll() {
        await this.page.getByRole("button", {name: "Select All"}).click();
    }

    async clickOnSetLabels() {
        const bulkSelectDropdown = this.page.locator(".bulk-select").locator(".el-button-group").locator(".el-dropdown");
        await bulkSelectDropdown.waitFor({state: "visible"});
        await bulkSelectDropdown.click();
        const setLabelsItem = this.page.getByRole("menuitem", {name: "Set labels"});
        await setLabelsItem.waitFor({state: "visible"});
        await setLabelsItem.click();
    }

    async clickOnResume() {
        const bulkSelectDropdown = this.page.locator(".bulk-select").locator(".el-button-group").locator(".el-dropdown");
        await bulkSelectDropdown.waitFor({state: "visible"});
        await bulkSelectDropdown.click();
        const resumeItem = this.page.getByRole("menuitem", {name: "Resume"});
        await resumeItem.waitFor({state: "visible"});
        await resumeItem.click();
        // Confirm
        const okButton = this.page.getByRole("button", {name: "OK"}).first();
        await okButton.waitFor({state: "visible"});
        await okButton.click();
    }

    async clickOnRestart() {
        const restartButton = this.page.getByRole("button", {name: "Restart"});
        await restartButton.waitFor({state: "visible"});
        await restartButton.click();
        // Confirm
        const okButton = this.page.getByRole("button", {name: "OK"}).first();
        await okButton.waitFor({state: "visible"});
        await okButton.click();
    }

    async clickOnReplay() {
        const replayButton = this.page.getByRole("button", {name: "Replay"});
        await replayButton.waitFor({state: "visible"});
        await replayButton.click();
        // Confirm
        const okButton = this.page.getByRole("button", {name: "OK"}).first();
        await okButton.waitFor({state: "visible"});
        await okButton.click();
    }

    async clickOnDelete() {
        const deleteButton = this.page.getByRole("button", {name: "Delete"});
        await deleteButton.waitFor({state: "visible"});
        await deleteButton.click();
        // Wait for confirmation dialog
        const okButton = this.page.getByRole("button", {name: "OK"}).first();
        await okButton.waitFor({state: "visible"});
        await okButton.click();
    }

    async setLabelOnSelectedExecutions() {
        await this.page.getByRole("textbox", {name: "Key"}).fill("foo");
        await this.page.getByRole("textbox", {name: "Value"}).fill("baz");
        await this.page.getByRole("button", {name: "OK"}).click();
        // Confirm
        await this.page.getByRole("button", {name: "OK"}).click();
    }

    async setPaginationTo(size: Pagination) {
        // The Element-Plus dropdown is not a `select` - click on text
        await this.page.locator(".pagination .el-select").click();

        // Wait for the select dropdown to show
        const dropdowns = this.page.locator(".el-select-dropdown");
        const visibleDropdown = dropdowns.filter({has: this.page.locator(":visible")}).last();

        // Wait for the visible dropdown to actually appear
        await visibleDropdown.waitFor({state: "visible", timeout: 500});

        // Find and click the matching option
        const option = visibleDropdown.locator(".el-select-dropdown__item", {hasText: `${size} per page`});
        await option.waitFor({state: "visible", timeout: 500});
        await option.click();
    }
}