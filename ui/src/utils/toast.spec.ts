import {beforeEach, describe, expect, it, vi} from "vitest"

// Track every notification handle KsNotification hands out, so the tests can
// assert which ones toast.saved() closed.
const createdHandles: {close: ReturnType<typeof vi.fn>}[] = []

vi.mock("@kestra-io/design-system", () => {
    const KsNotification = Object.assign(
        vi.fn(() => {
            const handle = {close: vi.fn()}
            createdHandles.push(handle)
            return handle
        }),
        {closeAll: vi.fn()},
    )
    return {
        KsNotification,
        KsMarkdown: {name: "KsMarkdown"},
        KsMessageBox: {confirm: vi.fn()},
        KsTable: {name: "KsTable"},
        KsTableColumn: {name: "KsTableColumn"},
    }
})

import type {VNode} from "vue"
import {KsNotification, KsTableColumn} from "@kestra-io/design-system"
import {makeToast} from "./toast"

const t = (key: string) => key

describe("toast.saved", () => {
    beforeEach(() => {
        createdHandles.length = 0
        vi.mocked(KsNotification).mockClear()
        vi.mocked(KsNotification.closeAll).mockClear()
    })

    it("shouldCollapsePreviousSavedToastWhenSavedAgain", () => {
        // Given
        const toast = makeToast(t)
        toast.saved("first-flow")
        const firstHandle = createdHandles[0]

        // When
        toast.saved("second-flow")

        // Then
        expect(firstHandle.close).toHaveBeenCalledTimes(1)
        expect(createdHandles).toHaveLength(2)
        expect(createdHandles[1].close).not.toHaveBeenCalled()
    })

    it("shouldNotDismissUnrelatedNotificationsWhenSaved", () => {
        // Given an unrelated notification (e.g. the plugin auto-install progress toast)
        const unrelatedHandle = KsNotification({title: "Installing 1 plugin"} as Record<string, unknown>)
        const toast = makeToast(t)

        // When
        toast.saved("my-flow")

        // Then the blanket closeAll must not run and the unrelated toast must survive
        expect(KsNotification.closeAll).not.toHaveBeenCalled()
        expect(unrelatedHandle.close).not.toHaveBeenCalled()
    })
})

describe("toast.error", () => {
    beforeEach(() => {
        vi.mocked(KsNotification).mockClear()
    })

    it("should translate the message column header of a list of errors", () => {
        const markingT = (key: string) => `t:${key}`

        makeToast(markingT).error([{message: "a"}, {message: "b"}])

        const {message} = vi.mocked(KsNotification).mock.lastCall![0] as {message: VNode}
        const column = (message.children as VNode[]).find(child => child.type === KsTableColumn)
        expect(column?.props?.label).toBe("t:toast_message_column")
    })
})
