import type {Page} from "@playwright/test";
import {expect} from "@playwright/test";

import {BasePage} from "./base.page";

export class FlowsPage extends BasePage {
    constructor(public readonly page: Page) {
        super(page);
    }

    async goto() {
        await this.login();
        await this.page.goto("/ui/main/flows");

        await expect(this.page.getByRole("heading", {name: "Flows"})).toBeVisible();
    }
}