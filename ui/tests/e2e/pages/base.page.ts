import type {Page} from "@playwright/test"

/**
 * There is no `login()` here: the session comes from the `setup` project's
 * `storageState` (see `tests/e2e/auth.setup.ts`).
 */
export class BasePage {
    constructor(public readonly page: Page) { }

    async addQueryParam(page: Page, key: string, value: string) {
        // Get the current URL
        const url = new URL(page.url())

        // Change query params
        url.searchParams.set(key, value)

        // Navigate to the new URL
        await page.goto(url.toString())
    }

    async removeQueryParam(page: Page, key: string) {
        // Get the current URL
        const url = new URL(page.url())

        // Change query params
        url.searchParams.delete(key)

        // Navigate to the new URL
        await page.goto(url.toString())
    }

    async modifyQueryParam(page: Page, values: {[key: string]: string|undefined}) {
        // Get the current URL
        const url = new URL(page.url())

        // Change query params
        for (const key in values) {
            const value = values[key]
            if (value === undefined) {
                url.searchParams.delete(key)
            } else {
                url.searchParams.set(key, value)
            }
        }

        // Navigate to the new URL
        await page.goto(url.toString())
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