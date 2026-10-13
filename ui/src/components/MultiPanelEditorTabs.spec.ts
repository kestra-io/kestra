import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelEditorTabs from "./MultiPanelEditorTabs.vue"

const IconStub = {template: "<span class='icon-stub' />"}
const PaneStub = {name: "PaneStub", template: "<div class='pane-stub' />"}

describe("MultiPanelEditorTabs", () => {
    test("renders translated labels when labelKey is provided and falls back to label", () => {
        const tabs = [
            {
                uid: "code",
                button: {label: "Flow Code", labelKey: "multi_panel_editor.flow_code", icon: IconStub},
                component: PaneStub,
            },
            {
                uid: "topology",
                button: {label: "Topology", labelKey: "topology", icon: IconStub},
                component: PaneStub,
            },
            {
                uid: "custom",
                button: {label: "Custom Tab", icon: IconStub},
                component: PaneStub,
            },
        ]

        const i18n = createI18n({
            legacy: false,
            locale: "fr",
            messages: {
                fr: {
                    topology: "Topologie",
                    multi_panel_editor: {
                        flow_code: "Code du flow",
                    },
                },
            },
        })

        const wrapper = mount(MultiPanelEditorTabs, {
            global: {
                plugins: [i18n, KestraDesignSystem],
            },
            props: {
                tabs,
                openTabs: ["code"],
            },
        })

        const labels = wrapper.findAll(".tab-label").map((el) => el.text())
        expect(labels).toContain("Code du flow")
        expect(labels).toContain("Topologie")
        expect(labels).toContain("Custom Tab")
    })
})
