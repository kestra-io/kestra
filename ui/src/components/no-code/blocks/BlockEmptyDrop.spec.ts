import {describe, expect, test} from "vitest"
import BlockEmptyDrop from "./BlockEmptyDrop.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import en from "../../../translations/en.json"

type Props = InstanceType<typeof BlockEmptyDrop>["$props"]

function mountDrop(props: Partial<Props> = {}) {
    return i18nMount(BlockEmptyDrop, {locales: en, props: {label: "task", ...props}})
}

const lead = (wrapper: ReturnType<typeof mountDrop>) => wrapper.find(".block-empty-drop-lead").text()
const hint = (wrapper: ReturnType<typeof mountDrop>) => wrapper.find(".block-empty-drop-hint")

describe("BlockEmptyDrop", () => {
    test("uses the empty wording for the empty variant and the inline wording for inline", () => {
        expect(lead(mountDrop({variant: "empty"}))).toBe("Click to add task")
        expect(lead(mountDrop({variant: "inline"}))).toBe("Add task")
    })

    test("defaults to the inline variant", () => {
        const wrapper = mountDrop()

        expect(lead(wrapper)).toBe("Add task")
        expect(wrapper.classes()).toContain("block-empty-drop--inline")
    })

    test.each(["empty", "inline"] as const)("replaces the %s label with the refusal wording on a forbidden drop", (variant) => {
        expect(lead(mountDrop({variant, dropState: "forbidden"}))).toBe("Can't drop here")
    })

    test("takes its class from the variant and has no drop class while idle", () => {
        const classes = mountDrop({variant: "empty"}).classes()

        expect(classes).toContain("block-empty-drop--empty")
        expect(classes).not.toContain("block-empty-drop--inline")
        expect(classes.filter((name) => name.startsWith("block-empty-drop--drop-"))).toEqual([])

        expect(mountDrop({dropState: "allowed"}).classes()).toContain("block-empty-drop--drop-allowed")
        expect(mountDrop({dropState: "forbidden"}).classes()).toContain("block-empty-drop--drop-forbidden")
    })

    test("adds the required modifier only when required", () => {
        expect(mountDrop().classes()).not.toContain("block-empty-drop--required")
        expect(mountDrop({required: true}).classes()).toContain("block-empty-drop--required")
    })

    test("renders the hint only while idle and only when one is given", () => {
        expect(hint(mountDrop({hint: "or press / to search tasks"})).text()).toBe("or press / to search tasks")
        expect(hint(mountDrop()).exists()).toBe(false)
        expect(hint(mountDrop({hint: "or press / to search tasks", dropState: "allowed"})).exists()).toBe(false)
        expect(hint(mountDrop({hint: "or press / to search tasks", dropState: "forbidden"})).exists()).toBe(false)
    })

    test("emits add with the click event", async () => {
        const wrapper = mountDrop()

        await wrapper.find("button").trigger("click")

        expect(wrapper.emitted("add")).toHaveLength(1)
        expect(wrapper.emitted("add")?.[0]?.[0]).toBeInstanceOf(MouseEvent)
    })

    test("does not emit add when disabled", async () => {
        const wrapper = mountDrop({disabled: true})

        await wrapper.find("button").trigger("click")

        expect(wrapper.emitted("add")).toBeUndefined()
    })
})
