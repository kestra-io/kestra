import type {Page} from "@playwright/test";
import {expect} from "@playwright/test";

import {BasePage} from "./base.page";
import {shared} from "../fixtures/shared";

export class LogsPage extends BasePage {

    constructor(public readonly page: Page) {
        super(page);
    }

    async login() {
        await this.page.goto("/");
        await this.page.getByRole("textbox", {name: "Email"}).fill(shared.username);
        await this.page.getByRole("textbox", {name: "Password"}).fill(shared.password);
        await this.page.getByRole("button", {name: "Login"}).click();
        await this.page.waitForURL((url) => !url.toString().includes("/login"), {timeout: 15000});
    }

    async goto() {
        await this.login();
        await this.page.goto("/ui/main/logs");

        await expect(this.page.getByRole("heading", {name: "Logs"})).toBeVisible();
    }

    async setFilterByNamespace(namespace: string) {
        const param = "filters[namespace][EQUALS]";
        await this.modifyQueryParam(this.page, {[param]: namespace});
    }

    async setFilterByFlowId(flowId: string) {
        const param = "filters[flowId][EQUALS]";
        await this.modifyQueryParam(this.page, {[param]: flowId});
    }

    async getDisplayedLogCount(): Promise<number> {
        await this.page.waitForLoadState("networkidle");
        return await this.page.locator(".log-row").count();
    }

    async expectDisplayedLogCountToBe(expectedCount: number) {
        return expect(this.page.locator(".log-row")).toHaveCount(expectedCount);
    }

    async selectLogRowByIndex(index: number = 0) {
        const checkbox = this.page.locator(".log-row").nth(index).locator("label.el-checkbox");
        await checkbox.waitFor({state: "visible"});
        await checkbox.click();
        await expect(checkbox).toContainClass("is-checked");
    }

    async clickOnSelectAll() {
        await this.page.locator(".bulk-select").getByRole("button", {name: /Select all/}).click();
    }

    async clickOnDeleteAndConfirm() {
        await this.page.locator(".bulk-select").getByRole("button", {name: "Delete"}).click();
        await this.page.locator(".el-message-box").getByRole("button", {name: "Delete"}).click();
        await this.page.waitForLoadState("networkidle");
    }

    async expectBulkSelectBarToShow(selectedCount: number) {
        const bar = this.page.locator(".bulk-select");
        await expect(bar).toBeVisible();
        await expect(bar).toContainText(`${selectedCount} selected`);
    }

    async expectBulkSelectBarHidden() {
        await expect(this.page.locator(".bulk-select")).toHaveCount(0);
    }
}
