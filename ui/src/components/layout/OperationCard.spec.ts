import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {i18nMount} from "../../../tests/unit/i18nMount"
import OperationCard from "./OperationCard.vue"
import type {Notification} from "../../stores/notifications"

const NOW = new Date("2026-01-01T00:00:00Z")

const messages = {
    notifications: {
        operation: {
            running: {EXECUTION_KILL: "Killing {count} execution | Killing {count} executions"},
            processed: "{count} processed",
            rejected: "{count} rejected",
            elapsed: "running for {duration}",
        },
    },
}

function mountCard(overrides: Partial<Notification> = {}) {
    const notification = {
        id: "n1",
        title: "Fallback title",
        asyncOperationType: "EXECUTION_KILL",
        totalItems: 10,
        succeededItems: 0,
        failedItems: 0,
        createdDate: NOW.toISOString(),
        ...overrides,
    } as Notification

    return i18nMount(OperationCard, {props: {notification}, messages})
}

describe("OperationCard", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(NOW)
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    test("shouldUseOperationTranslationWhenKeyExistsAndTotalItemsIsSet", () => {
        const wrapper = mountCard({totalItems: 3})

        expect(wrapper.find(".title").text()).toBe("Killing 3 executions")
    })

    test("shouldFallBackToNotificationTitleWhenTotalItemsIsMissing", () => {
        const wrapper = mountCard({totalItems: undefined})

        expect(wrapper.find(".title").text()).toBe("Fallback title")
    })

    test("shouldFallBackToNotificationTitleWhenTranslationKeyIsMissing", () => {
        const wrapper = mountCard({asyncOperationType: "UNKNOWN_OPERATION" as Notification["asyncOperationType"]})

        expect(wrapper.find(".title").text()).toBe("Fallback title")
    })

    test("shouldRenderProgressBarOnlyWhenPercentageIsNotNull", () => {
        expect(mountCard({totalItems: 10}).find("[role='progressbar']").exists()).toBe(true)
        expect(mountCard({totalItems: undefined}).find("[role='progressbar']").exists()).toBe(false)
    })

    test("shouldSumSucceededAndFailedItemsInProcessedCount", () => {
        const wrapper = mountCard({succeededItems: 4, failedItems: 2})

        expect(wrapper.find(".meta").text()).toContain("6 processed")
    })

    test("shouldRenderRejectedSegmentOnlyWhenFailedItemsIsNonZero", () => {
        expect(mountCard({failedItems: 0}).find(".meta").text()).not.toContain("rejected")
        expect(mountCard({failedItems: 2}).find(".meta").text()).toContain("2 rejected")
    })

    test("shouldAlwaysRenderElapsedSegmentAndJoinSegmentsWithSeparator", () => {
        const withAll = mountCard({succeededItems: 4, failedItems: 2}).find(".meta").text()
        const elapsedOnly = mountCard({totalItems: undefined}).find(".meta").text()

        expect(withAll.split(" · ")).toEqual(["6 processed", "2 rejected", expect.stringContaining("running for")])
        expect(elapsedOnly).toMatch(/^running for /)
        expect(elapsedOnly).not.toContain(" · ")
    })

    test("shouldEmitClickWhenClickedOrEnterOrSpacePressed", async () => {
        const wrapper = mountCard()
        const card = wrapper.find("[data-test='operation-card']")

        await card.trigger("click")
        await card.trigger("keydown.enter")
        await card.trigger("keydown.space")

        expect(wrapper.emitted("click")).toHaveLength(3)
    })
})
