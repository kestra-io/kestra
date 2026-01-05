import type {Page} from "@playwright/test";
import {expect} from "@playwright/test";
import {shared} from "../fixtures/shared";

export class BasePage {
    constructor(public readonly page: Page) { }

    async login() {
        await this.page.goto("/");
        await this.page.getByRole("textbox", {name: "Email"}).fill(shared.username);
        await this.page.getByRole("textbox", {name: "Password"}).fill(shared.password);
        await this.page.getByRole("button", {name: "Login"}).click();

        await this.page.goto("/");

        await expect(this.page.getByRole("heading", {name: "Overview"})).toBeVisible();
    }

    async addQueryParam(page: Page, key: string, value: string) {
        // Get the current URL
        const url = new URL(page.url());

        // Change query params
        url.searchParams.set(key, value);

        // Navigate to the new URL
        await page.goto(url.toString());
    }

    async removeQueryParam(page: Page, key: string) {
        // Get the current URL
        const url = new URL(page.url());

        // Change query params
        url.searchParams.delete(key);

        // Navigate to the new URL
        await page.goto(url.toString());
    }

    async modifyQueryParam(page: Page, values: {[key: string]: string|undefined}) {
        // Get the current URL
        const url = new URL(page.url());

        // Change query params
        for (const key in values) {
            const value = values[key];
            if (value === undefined) {
                url.searchParams.delete(key);
            } else {
                url.searchParams.set(key, value);
            }
        }

        // Navigate to the new URL
        await page.goto(url.toString());
    }
}

export enum ExecutionState {
    FAILED = "FAILED",
    SUCCESS = "SUCCESS"
}

export enum Pagination {
    ITEMS_10 = 10,
    ITEMS_25 = 25,
    ITEMS_50 = 50,
    ITEMS_100 = 100
}