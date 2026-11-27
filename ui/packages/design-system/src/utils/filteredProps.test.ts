import {describe, test, expect} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {useFilteredProps, usePassedProps} from "./filteredProps"

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

describe("usePassedProps", () => {
    const Wrapper = defineComponent({
        props: {teleported: Boolean, validateEvent: Boolean, size: String, borderless: Boolean},
        setup(props) {
            const passed = usePassedProps(props, ["borderless"])
            return () => h("div", {"data-forwarded": JSON.stringify(passed())})
        },
    })
    const forwarded = (props: Record<string, unknown>) =>
        JSON.parse(mount(Wrapper, {props}).attributes("data-forwarded") ?? "{}")

    test("leaves out a boolean the parent did not pass, instead of the false Vue casts it to", () => {
        expect(forwarded({size: "small"})).toStrictEqual({size: "small"})
    })

    test("forwards a false the parent passed explicitly", () => {
        expect(forwarded({teleported: false, "validate-event": true})).toStrictEqual({teleported: false, validateEvent: true})
    })

    test("drops keys listed in skip", () => {
        expect(forwarded({borderless: true})).toStrictEqual({})
    })
})
