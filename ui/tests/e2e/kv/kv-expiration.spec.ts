import {v4 as uuid} from "uuid"

import {expect, test} from "./kv.fixture"

/**
 * The update API only takes a TTL duration, so the drawer prefills the remaining TTL and
 * shows the stored absolute expiration; an untouched TTL must keep the original expiration
 * on save instead of shifting it or silently removing it (kestra-io/kestra#14825).
 */
test.describe("KV Store — expiration on edit", () => {
    test("editing an entry with an expiration shows it and keeps it on save", async ({kvApi, kvPage}) => {
        const key = kvApi.track(`e2e_kv_expiration_${uuid().replace(/-/g, "_")}`)
        await kvApi.setKvViaApi(key, "until-the-hour", "PT1H")
        const before = await kvApi.getExpirationDateViaApi(key)
        expect(before).toBeDefined()

        await kvPage.goto()
        await kvPage.openEditDrawer(key)

        await expect(kvPage.expirationHint()).toContainText("Currently expires on")
        expect(await kvPage.readTtl()).toMatch(/^PT/)

        await kvPage.save()

        const after = await kvApi.getExpirationDateViaApi(key)
        expect(after).toBeDefined()
        expect(Math.abs(new Date(after!).getTime() - new Date(before!).getTime())).toBeLessThan(2000)
    })

    test("editing an entry without an expiration shows no hint", async ({kvApi, kvPage}) => {
        const key = kvApi.track(`e2e_kv_no_expiration_${uuid().replace(/-/g, "_")}`)
        await kvApi.setKvViaApi(key, "forever")

        await kvPage.goto()
        await kvPage.openEditDrawer(key)

        await expect(kvPage.expirationHint()).toBeHidden()
    })
})
