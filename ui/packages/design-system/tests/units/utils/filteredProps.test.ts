import {describe, test, expect} from "vitest"
import {useFilteredProps} from "../../../src/utils/filteredProps"

describe("useFilteredProps", () => {
    test("drops keys whose value is undefined", () => {
        const filtered = useFilteredProps({label: "Save", disabled: undefined})

        expect(filtered()).toStrictEqual({label: "Save"})
    })

    test("keeps false, 0 and an empty string since only undefined means absent", () => {
        const filtered = useFilteredProps({disabled: false, count: 0, label: ""})

        expect(filtered()).toStrictEqual({disabled: false, count: 0, label: ""})
    })

    test("keeps null", () => {
        const filtered = useFilteredProps({icon: null})

        expect(filtered()).toStrictEqual({icon: null})
    })

    test("drops keys listed in skip even when they have a value", () => {
        const filtered = useFilteredProps({label: "Save", modelValue: "draft", size: "small"}, ["modelValue"])

        expect(filtered()).toStrictEqual({label: "Save", size: "small"})
    })

    test("returns an empty object for empty props", () => {
        expect(useFilteredProps({})()).toStrictEqual({})
    })

    test("reads the props on every call instead of capturing a snapshot", () => {
        const props: Record<string, unknown> = {size: "small", round: undefined}
        const filtered = useFilteredProps(props)

        expect(filtered()).toStrictEqual({size: "small"})

        props.size = "large"
        props.round = true

        expect(filtered()).toStrictEqual({size: "large", round: true})
    })
})
