import {afterEach, describe, expect, it} from "vitest"
import {defineComponent, nextTick} from "vue"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import {KsMessageBox} from "@kestra-io/design-system"
import {useExecutionDeletionDialog, type ExecutionDeletionOptions} from "./useExecutionDeletionDialog"

const openDialog = (offerNonTerminated: boolean, nonTerminatedCount = 0) => {
    let result: Promise<ExecutionDeletionOptions | undefined> | undefined
    i18nMount(defineComponent({
        setup() {
            result = useExecutionDeletionDialog().confirmExecutionDeletion("Delete <code>e1</code>?", {
                offerNonTerminated,
                nonTerminatedCount,
                total: nonTerminatedCount,
            })
            return () => null
        },
    }))
    return result!
}

const findButton = (name: string) => [...document.body.querySelectorAll<HTMLButtonElement>("button")]
    .find(button => button.textContent?.trim() === name)

afterEach(async () => {
    KsMessageBox.close()
    await flushPromises()
})

describe("execution deletion dialog", () => {
    it("resolves to nothing when dismissed", async () => {
        const result = openDialog(false)
        await nextTick()

        KsMessageBox.close()

        await expect(result).resolves.toBeUndefined()
    })

    it("offers the non-terminated switch only when asked", async () => {
        openDialog(false)
        await nextTick()
        expect(document.body.querySelector('[role="switch"]')).toBeNull()

        KsMessageBox.close()
        await flushPromises()

        openDialog(true)
        await nextTick()
        expect(document.body.querySelector('[role="switch"]')).not.toBeNull()
    })

    it("requires consent before confirming deletion of non-terminated executions", async () => {
        const result = openDialog(true, 2)
        await nextTick()

        const confirm = findButton("Confirm")
        const consent = document.body.querySelector<HTMLButtonElement>('[role="switch"]')
        expect(confirm).toBeDefined()
        expect(consent).toBeDefined()

        let resolved = false
        void result.then(() => {
            resolved = true
        })

        confirm?.click()
        await flushPromises()
        expect(resolved).toBe(false)

        consent?.click()
        await flushPromises()
        confirm?.click()

        await expect(result).resolves.toEqual({deleteLogs: true, deleteMetrics: true, deleteStorage: true, includeNonTerminated: true})
    })
})

