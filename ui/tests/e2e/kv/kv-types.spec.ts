import {v4 as uuid} from "uuid"

import {expect, test} from "./kv.fixture"
import {shared} from "../fixtures/shared"
import type {KvType} from "../pages/kv.page"

/**
 * One round trip per KV type: what the drawer sends, what the API stores it as, and what
 * the drawer shows when the entry is reopened. The type is inferred server-side from the
 * ION payload, so a badly serialized value silently changes type instead of failing.
 */
type RoundTrip = {
    label: string;
    type: KvType;
    /** Typed into the value control, in the pinned Europe/Paris timezone. */
    entered: string;
    storedType: string;
    storedValue: any;
    /** What the value control holds once the entry is reopened; defaults to `entered`. */
    reopened?: string;
}

const ROUND_TRIPS: RoundTrip[] = [
    {label: "STRING", type: "STRING", entered: "hello world", storedType: "STRING", storedValue: "hello world"},
    {label: "STRING keeping its surrounding spaces", type: "STRING", entered: "  spaced  ", storedType: "STRING", storedValue: "  spaced  "},
    {label: "STRING that looks like a number", type: "STRING", entered: "42", storedType: "STRING", storedValue: "42"},
    {label: "STRING that looks like a boolean", type: "STRING", entered: "true", storedType: "STRING", storedValue: "true"},
    {label: "NUMBER", type: "NUMBER", entered: "42", storedType: "NUMBER", storedValue: 42},
    {label: "BOOLEAN", type: "BOOLEAN", entered: "true", storedType: "BOOLEAN", storedValue: true},
    {label: "DATETIME", type: "DATETIME", entered: "2024-01-01 10:30:00", storedType: "DATETIME", storedValue: "2024-01-01T09:30:00Z"},
    {label: "DATE", type: "DATE", entered: "2024-01-01", storedType: "DATE", storedValue: "2024-01-01"},
    {label: "DURATION", type: "DURATION", entered: "PT45M", storedType: "DURATION", storedValue: "PT45M"},
    {label: "JSON", type: "JSON", entered: "{\"a\":1,\"b\":[1,2]}", storedType: "JSON", storedValue: {a: 1, b: [1, 2]}},
]

test.describe("KV Store — value round trip per type", () => {
    for (const roundTrip of ROUND_TRIPS) {
        test(`a ${roundTrip.label} is stored and reopened unchanged`, async ({kvApi, kvPage}) => {
            const key = kvApi.track(`e2e_kv_${roundTrip.type.toLowerCase()}_${uuid().replace(/-/g, "_")}`)

            await kvPage.goto()

            await test.step("create the entry from the drawer", async () => {
                await kvPage.createKv(shared.namespace, key, roundTrip.type, roundTrip.entered)
            })

            await test.step("the API stores it with the chosen type", async () => {
                const stored = await kvApi.getKvViaApi(key)
                expect(stored.type).toBe(roundTrip.storedType)
                expect(stored.value).toStrictEqual(roundTrip.storedValue)
            })

            await test.step("reopening the entry shows what was entered", async () => {
                await kvPage.openEditDrawer(key)
                expect(await kvPage.readType()).toBe(roundTrip.type)
                expect(await kvPage.readValue(roundTrip.type)).toBe(roundTrip.reopened ?? roundTrip.entered)
            })
        })
    }

    test("a BOOLEAN left off is stored as false", async ({kvApi, kvPage}) => {
        const key = kvApi.track(`e2e_kv_boolean_false_${uuid().replace(/-/g, "_")}`)

        await kvPage.goto()
        await kvPage.createKv(shared.namespace, key, "BOOLEAN", "false")

        const stored = await kvApi.getKvViaApi(key)
        expect(stored.type).toBe("BOOLEAN")
        expect(stored.value).toBe(false)

        await kvPage.openEditDrawer(key)
        expect(await kvPage.readValue("BOOLEAN")).toBe("false")
    })

    test("editing a value keeps the type it was created with", async ({kvApi, kvPage}) => {
        const key = kvApi.track(`e2e_kv_edit_${uuid().replace(/-/g, "_")}`)

        await kvPage.goto()
        await kvPage.createKv(shared.namespace, key, "STRING", "before")

        await kvPage.openEditDrawer(key)
        await kvPage.fillValue("STRING", "after")
        await kvPage.save()

        const stored = await kvApi.getKvViaApi(key)
        expect(stored.type).toBe("STRING")
        expect(stored.value).toBe("after")
    })
})
