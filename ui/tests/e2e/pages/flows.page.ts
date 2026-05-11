import {expect} from "@playwright/test"

import {BasePage} from "./base.page"

export class FlowsPage extends BasePage {
    async goto() {
        await this.login()
        await this.page.goto("/ui/main/flows")

        await expect(this.page.getByRole("heading", {name: "Flows"})).toBeVisible()
    }
}