import {describe, test, expect} from "vitest"
import {computed} from "vue"
import {Comparators, type FilterConfiguration} from "../utils/filterTypes"
import {useDismissedKeys} from "./useDismissedKeys"

const makeConfiguration = (keys: FilterConfiguration["keys"] = []): FilterConfiguration => ({
    title: "Test filters",
    keys,
})

const makeKey = (key: string, visibleByDefault = false) => ({
    key,
    label: key,
    visibleByDefault,
    valueType: "text" as const,
    comparators: [Comparators.CONTAINS] as [Comparators],
})

describe("useDismissedKeys", () => {
    test("isDefaultVisibleKey is true only for a configured key with visibleByDefault, and false for an unknown key or a configuration with no keys", () => {
        const configuration = makeConfiguration([
            makeKey("defaultVisible", true),
            makeKey("notDefaultVisible", false),
        ])
        const {isDefaultVisibleKey} = useDismissedKeys(configuration)

        expect(isDefaultVisibleKey("defaultVisible")).toBe(true)
        expect(isDefaultVisibleKey("notDefaultVisible")).toBe(false)
        expect(isDefaultVisibleKey("unknownKey")).toBe(false)

        const emptyConfig = makeConfiguration([])
        const emptyDismissed = useDismissedKeys(emptyConfig)
        expect(emptyDismissed.isDefaultVisibleKey("defaultVisible")).toBe(false)

        const noKeysConfig = {title: "No keys"} as FilterConfiguration
        const noKeysDismissed = useDismissedKeys(noKeysConfig)
        expect(noKeysDismissed.isDefaultVisibleKey("defaultVisible")).toBe(false)
    })

    test("dismissDefaultVisibleKey adds a default-visible key and ignores one that is not default-visible", () => {
        const configuration = makeConfiguration([
            makeKey("defaultVisible", true),
            makeKey("notDefaultVisible", false),
        ])
        const {dismissedKeys, dismissDefaultVisibleKey} = useDismissedKeys(configuration)

        dismissDefaultVisibleKey("defaultVisible")
        expect(dismissedKeys.value.has("defaultVisible")).toBe(true)

        dismissDefaultVisibleKey("notDefaultVisible")
        expect(dismissedKeys.value.has("notDefaultVisible")).toBe(false)

        dismissDefaultVisibleKey("unknownKey")
        expect(dismissedKeys.value.has("unknownKey")).toBe(false)
        expect(dismissedKeys.value).toEqual(new Set(["defaultVisible"]))
    })

    test("restoreDefaultVisibleKey removes a dismissed key and is a no-op for a key that was never dismissed", () => {
        const configuration = makeConfiguration([
            makeKey("defaultVisible", true),
            makeKey("otherDefault", true),
        ])
        const {dismissedKeys, dismissDefaultVisibleKey, restoreDefaultVisibleKey} = useDismissedKeys(configuration)

        dismissDefaultVisibleKey("defaultVisible")
        expect(dismissedKeys.value.has("defaultVisible")).toBe(true)

        const refBefore = dismissedKeys.value
        restoreDefaultVisibleKey("otherDefault")
        expect(dismissedKeys.value).toBe(refBefore)
        expect(dismissedKeys.value.has("defaultVisible")).toBe(true)

        restoreDefaultVisibleKey("defaultVisible")
        expect(dismissedKeys.value.has("defaultVisible")).toBe(false)
        expect(dismissedKeys.value.size).toBe(0)
    })

    test("dismissAllDefaultVisibleKeys dismisses every visibleByDefault key and nothing else", () => {
        const configuration = makeConfiguration([
            makeKey("default1", true),
            makeKey("notDefault", false),
            makeKey("default2", true),
        ])
        const {dismissedKeys, dismissAllDefaultVisibleKeys} = useDismissedKeys(configuration)

        dismissAllDefaultVisibleKeys()

        expect(dismissedKeys.value).toEqual(new Set(["default1", "default2"]))
        expect(dismissedKeys.value.has("notDefault")).toBe(false)

        const noKeysConfig = {title: "No keys"} as FilterConfiguration
        const noKeysDismissed = useDismissedKeys(noKeysConfig)
        noKeysDismissed.dismissAllDefaultVisibleKeys()
        expect(noKeysDismissed.dismissedKeys.value).toEqual(new Set())
    })

    test("resetDismissedDefaultVisibleKeys clears the set", () => {
        const configuration = makeConfiguration([
            makeKey("default1", true),
            makeKey("default2", true),
        ])
        const {dismissedKeys, dismissAllDefaultVisibleKeys, resetDismissedDefaultVisibleKeys} = useDismissedKeys(configuration)

        dismissAllDefaultVisibleKeys()
        expect(dismissedKeys.value.size).toBe(2)

        resetDismissedDefaultVisibleKeys()
        expect(dismissedKeys.value.size).toBe(0)
        expect(dismissedKeys.value).toEqual(new Set())
    })

    test("hasDismissedDefaultVisibleKeys tracks whether the set is non-empty", () => {
        const configuration = makeConfiguration([
            makeKey("default1", true),
        ])
        const {
            hasDismissedDefaultVisibleKeys,
            dismissDefaultVisibleKey,
            restoreDefaultVisibleKey,
            resetDismissedDefaultVisibleKeys,
        } = useDismissedKeys(configuration)

        expect(hasDismissedDefaultVisibleKeys.value).toBe(false)

        dismissDefaultVisibleKey("default1")
        expect(hasDismissedDefaultVisibleKeys.value).toBe(true)

        restoreDefaultVisibleKey("default1")
        expect(hasDismissedDefaultVisibleKeys.value).toBe(false)

        dismissDefaultVisibleKey("default1")
        expect(hasDismissedDefaultVisibleKeys.value).toBe(true)

        resetDismissedDefaultVisibleKeys()
        expect(hasDismissedDefaultVisibleKeys.value).toBe(false)
    })

    test("a computed reading dismissedKeys re-evaluates on every change", () => {
        const configuration = makeConfiguration([
            makeKey("default1", true),
            makeKey("default2", true),
        ])
        const {
            dismissedKeys,
            dismissDefaultVisibleKey,
            restoreDefaultVisibleKey,
            resetDismissedDefaultVisibleKeys,
        } = useDismissedKeys(configuration)

        let evaluationCount = 0
        const computedSize = computed(() => {
            evaluationCount++
            return dismissedKeys.value.size
        })

        expect(computedSize.value).toBe(0)
        expect(evaluationCount).toBe(1)

        const firstSet = dismissedKeys.value
        dismissDefaultVisibleKey("default1")
        expect(dismissedKeys.value).not.toBe(firstSet)
        expect(computedSize.value).toBe(1)
        expect(evaluationCount).toBe(2)

        const secondSet = dismissedKeys.value
        restoreDefaultVisibleKey("default1")
        expect(dismissedKeys.value).not.toBe(secondSet)
        expect(computedSize.value).toBe(0)
        expect(evaluationCount).toBe(3)

        dismissDefaultVisibleKey("default2")
        expect(computedSize.value).toBe(1)
        expect(evaluationCount).toBe(4)

        const thirdSet = dismissedKeys.value
        resetDismissedDefaultVisibleKeys()
        expect(dismissedKeys.value).not.toBe(thirdSet)
        expect(computedSize.value).toBe(0)
        expect(evaluationCount).toBe(5)
    })
})
