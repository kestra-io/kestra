import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {ref} from "vue"
import KsScrollbar from "../../../src/components/Basic/KsScrollbar.vue"

const meta: Meta<typeof KsScrollbar> = {
    title: "Components/Basic/KsScrollbar",
    component: KsScrollbar,
    tags: ["autodocs"],
    parameters: {
        docs: {description: {component: "KsScrollbar is the Kestra design-system abstraction over `ElScrollbar` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsScrollbar>

/** Basic usage – fixed height with vertical overflow */
export const Default: Story = {
    render: () => ({
        components: {KsScrollbar},
        template: `
            <div style="padding:24px">
                <ks-scrollbar height="200px">
                    <div v-for="i in 20" :key="i" style="padding:8px;border-bottom:1px solid #eee">
                        Item {{ i }}
                    </div>
                </ks-scrollbar>
            </div>
        `,
    }),
}

/** Horizontal scroll – content wider than container */
export const HorizontalScroll: Story = {
    render: () => ({
        components: {KsScrollbar},
        template: `
            <div style="padding:24px;width:400px">
                <ks-scrollbar>
                    <div style="display:flex;gap:12px;padding-bottom:8px">
                        <div
                            v-for="i in 12"
                            :key="i"
                            style="flex:0 0 100px;height:80px;border-radius:6px;background:#f0f0f0;display:flex;align-items:center;justify-content:center;font-size:13px"
                        >Item {{ i }}</div>
                    </div>
                </ks-scrollbar>
            </div>
        `,
    }),
}

/** Max height – scrollbar only appears when content exceeds threshold */
export const MaxHeight: Story = {
    render: () => ({
        components: {KsScrollbar},
        setup() {
            const count = ref(3)
            return {count}
        },
        template: `
            <div style="padding:24px;width:400px">
                <div style="display:flex;gap:8px;margin-bottom:12px">
                    <button @click="count = Math.max(1, count - 1)">Remove</button>
                    <button @click="count++">Add</button>
                    <span style="font-size:13px;opacity:0.6;line-height:2">{{ count }} items</span>
                </div>
                <ks-scrollbar max-height="200px">
                    <div v-for="i in count" :key="i" style="padding:10px;border-bottom:1px solid #eee">
                        Item {{ i }}
                    </div>
                </ks-scrollbar>
            </div>
        `,
    }),
}

/** Manual scroll – programmatic scrollTo control */
export const ManualScroll: Story = {
    render: () => ({
        components: {KsScrollbar},
        setup() {
            const scrollbarRef = ref<any>(null)
            function scrollToTop() { scrollbarRef.value?.setScrollTop(0) }
            function scrollToBottom() { scrollbarRef.value?.setScrollTop(9999) }
            return {scrollbarRef, scrollToTop, scrollToBottom}
        },
        template: `
            <div style="padding:24px;width:400px">
                <div style="display:flex;gap:8px;margin-bottom:12px">
                    <button @click="scrollToTop">Scroll to top</button>
                    <button @click="scrollToBottom">Scroll to bottom</button>
                </div>
                <ks-scrollbar ref="scrollbarRef" height="200px">
                    <div v-for="i in 20" :key="i" style="padding:8px;border-bottom:1px solid #eee">
                        Item {{ i }}
                    </div>
                </ks-scrollbar>
            </div>
        `,
    }),
}
