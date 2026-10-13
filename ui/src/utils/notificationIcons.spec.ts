import {describe, expect, it} from "vitest"
import BellOutline from "vue-material-design-icons/BellOutline.vue"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import AccountCheckOutline from "vue-material-design-icons/AccountCheckOutline.vue"
import Star from "vue-material-design-icons/Star.vue"
import {NOTIFICATION_ICONS, registerNotificationIcon} from "./notificationIcons"

describe("NOTIFICATION_ICONS", () => {
    it("maps GENERIC to BellOutline", () => {
        expect(NOTIFICATION_ICONS.GENERIC).toBe(BellOutline)
    })

    it("maps HUMAN_TASK_PENDING to AccountCheckOutline", () => {
        expect(NOTIFICATION_ICONS.HUMAN_TASK_PENDING).toBe(AccountCheckOutline)
    })

    it("maps SYSTEM_EXECUTION_FAILED to AlertCircleOutline", () => {
        expect(NOTIFICATION_ICONS.SYSTEM_EXECUTION_FAILED).toBe(AlertCircleOutline)
    })

    it("has no entry for a type nobody has registered an icon for yet", () => {
        expect(NOTIFICATION_ICONS.SOME_FUTURE_TYPE).toBeUndefined()
    })

    it("lets an outside producer (e.g. an EE feature) register its own icon", () => {
        registerNotificationIcon("SOME_PRODUCER_TYPE", Star)
        expect(NOTIFICATION_ICONS.SOME_PRODUCER_TYPE).toBe(Star)
    })
})
