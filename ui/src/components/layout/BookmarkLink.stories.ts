import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect, userEvent, waitFor} from "storybook/test"
import {vueRouter} from "storybook-vue3-router"
import {KsSideBarSection} from "@kestra-io/design-system"
import BookmarkLink from "./BookmarkLink.vue"

const meta: Meta<typeof BookmarkLink> = {
    title: "Layout/BookmarkLink",
    component: BookmarkLink,
    decorators: [vueRouter([{path: "/:path(.*)", component: {template: "<div />"}}])],
}
export default meta

export const KeyboardFocus: StoryObj<typeof BookmarkLink> = {
    render: () => ({
        components: {BookmarkLink, KsSideBarSection},
        template: `
            <div style="width:240px">
                <KsSideBarSection title="Favourites">
                    <BookmarkLink href="/flows" title="Flows" />
                </KsSideBarSection>
            </div>
        `,
    }),
    play: async ({canvasElement}) => {
        await userEvent.tab()

        const link = canvasElement.querySelector<HTMLElement>("a[href]")!
        await expect(link).toHaveFocus()

        // Links transition their outline in the app styles, so the ring settles after focus lands.
        await waitFor(async () => {
            const {outlineWidth, outlineOffset} = getComputedStyle(link)
            const reach = Math.max(0, parseFloat(outlineWidth) + parseFloat(outlineOffset))
            const ring = link.getBoundingClientRect()
            const clip = link.closest(".ks-sidebar-section__body-inner")!.getBoundingClientRect()
            await expect(ring.left - reach).toBeGreaterThanOrEqual(clip.left)
            await expect(ring.right + reach).toBeLessThanOrEqual(clip.right)
        })
    },
}
