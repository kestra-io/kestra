import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {within, expect, waitFor} from "storybook/test"
import ContextNews from "./ContextNews.vue"
import {useApiStore} from "../../stores/api"

const feeds = [
    {
        id: "f1",
        publicationDate: "2026-10-28T09:00:00Z",
        title: "Kestra 2.0 is here",
        description: "",
        content: "",
        link: "https://kestra.io/blogs/release-2-0",
    },
    {
        id: "f2",
        publicationDate: "2026-09-12T09:00:00Z",
        title: "Building resilient data pipelines",
        description: "",
        content: "",
        link: "https://kestra.io/blogs/resilient-data-pipelines",
    },
]

const releases = [
    {
        version: "2.0.11",
        publicationDate: "2026-11-11T15:00:00Z",
        summary: "Fixes a scheduler race on trigger backfills and a flow editor crash.",
        link: "https://kestra.io/docs/changelog#2-0-11",
    },
    {version: "2.0.10", publicationDate: "2026-11-10T15:08:00Z"},
]

function renderWith(seededReleases: typeof releases) {
    return () => ({
        components: {ContextNews},
        setup() {
            const store = useApiStore()
            store.feeds = feeds
            store.releases = seededReleases
        },
        template: "<div style=\"width:420px;height:640px;display:flex\"><context-news /></div>",
    })
}

const meta: Meta<typeof ContextNews> = {
    title: "Components/Layout/ContextNews",
    component: ContextNews,
}
export default meta
type Story = StoryObj<typeof ContextNews>

export const WithPatchReleases: Story = {
    render: renderWith(releases),
    async play({canvasElement}) {
        const canvas = within(canvasElement)
        await waitFor(() => expect(canvasElement.querySelectorAll("[data-test=news-release]")).toHaveLength(2))

        const cards = canvasElement.querySelectorAll<HTMLElement>("[data-test=news-release]")
        await expect(within(cards[0]).getByText("Kestra 2.0.11 is available")).toBeVisible()
        await expect(cards[0]).toHaveAttribute("href", "https://kestra.io/docs/changelog#2-0-11")
        await expect(cards[1]).toHaveAttribute("href", "https://github.com/kestra-io/kestra/releases/tag/v2.0.10")
    },
}

export const WithoutPatchReleases: Story = {
    render: renderWith([]),
    async play({canvasElement}) {
        const canvas = within(canvasElement)

        await expect(await canvas.findByText("Kestra 2.0 is here")).toBeVisible()
        await expect(canvasElement.querySelector("[data-test=news-releases]")).toBeNull()
    },
}
