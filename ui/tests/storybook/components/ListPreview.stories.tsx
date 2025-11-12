import ListPreview from "../../../src/components/ListPreview.vue";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import fakeData from "../../fixtures/fake-data.json"
;

const meta: Meta<typeof ListPreview> = {
    title: "components/ListPreview",
    component: ListPreview,
}

export default meta;

export const Empty: StoryObj<typeof ListPreview> = {
    render: () => ({
        setup(){
            return () => <ListPreview value={[]} />
        }
    }),
}

export const Simple: StoryObj<typeof ListPreview> = {
    render: () => ({
        setup(){
            return () => <ListPreview value={[{title: "Item 1"}, {title: "Item 2"}]} />
        }
    }),
}

export const DoubleSmall: StoryObj<typeof ListPreview> = {
    render: () => ({
        setup(){
            return () => <ListPreview value={[
                {title: "Item 0"},
                {title: "Item 1", desc: "Description 1"},
                {title: "Item 2", desc: "Description 2"},
                {desc: "Description 3"}
            ]} />
        }
    }),
}

export const FullJSON: StoryObj<typeof ListPreview> = {
    render: () => ({
        setup(){
            return () => <ListPreview value={[
                {output: {key1: "value1", key2: "value2", key3: {subKey1: "subValue1"}}},
            ]} />
        }
    }),
}

export const FullJSONTruncated: StoryObj<typeof ListPreview> = {
    render: () => ({
        setup(){
            return () => <ListPreview value={[
                {output: fakeData},
            ]} />
        }
    }),
}