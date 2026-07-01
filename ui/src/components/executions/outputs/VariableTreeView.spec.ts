import {mount} from "@vue/test-utils"
import {describe, expect, test, vi} from "vitest"

// import VariableTreeView from "../../../../../src/components/executions/outputs/VariableTreeView.vue"
import VariableTreeView from "./VariableTreeView.vue"

vi.mock("vue-i18n", () => ({
    useI18n: () => ({
        t: (key: string, params?: {count?: number}) => params?.count ? `${params.count} ${key}` : key,
    }),
}))

describe("VariableTreeView", () => {
    test("emits the selected nested value with its expression path", async () => {
        const fileUri = "kestra:///company/team/ship-logs/executions/execution-id/tasks/ship/output.ion"
        const wrapper = mount(VariableTreeView, {
            props: {
                basePath: "outputs.ship",
                value: {
                    outputs: {
                        file: {
                            uris: [fileUri],
                        },
                    },
                },
            },
        })

        const fileRow = wrapper
            .findAll(".json-tree__row")
            .find((row) => row.text().includes(fileUri))

        expect(fileRow).toBeDefined()
        await fileRow!.trigger("click")

        expect(wrapper.emitted("select")).toEqual([
            ["outputs.ship.outputs.file.uris[\"0\"]", fileUri],
        ])
    })
})
