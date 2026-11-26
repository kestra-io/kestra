import {beforeEach, describe, expect, test} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import LeafBlockCard from "./LeafBlockCard.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

function mountCard(block: Record<string, unknown>) {
    return i18nMount(LeafBlockCard, {
        global: {
            plugins: [KestraDesignSystem],
            stubs: {
                TaskIcon: {name: "TaskIcon", props: ["cls", "icons", "loadIcon", "onlyIcon"], template: "<span class='task-icon-stub' />"},
            },
        },
        props: {block, path: "inputs[0]"},
    })
}

describe("LeafBlockCard", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("shows the plugin icon of a task", () => {
        const icon = mountCard({id: "log", type: "io.kestra.plugin.core.log.Log"}).findComponent({name: "TaskIcon"})
        expect(icon.exists()).toBe(true)
        expect(icon.props("cls")).toBe("io.kestra.plugin.core.log.Log")
    })

    test("shows no icon for an input, whose type is not a plugin class", () => {
        expect(mountCard({id: "env", type: "STRING"}).findComponent({name: "TaskIcon"}).exists()).toBe(false)
    })
})
