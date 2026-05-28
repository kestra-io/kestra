import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsDateAgo from "../../../src/components/Data/KsDateAgo.vue"
import {setMomentInstance, setDateFormatter} from "../../../src/index"

// Provide minimal moment-like and formatter implementations for the story environment
setMomentInstance((date?: any) => {
    const ms = Date.now() - new Date(date).getTime()
    const seconds = Math.round(ms / 1000)
    const minutes = Math.round(seconds / 60)
    const hours = Math.round(minutes / 60)
    const days = Math.round(hours / 24)
    if (seconds < 45) return {fromNow: () => "a few seconds ago"}
    if (minutes < 2) return {fromNow: () => "a minute ago"}
    if (minutes < 45) return {fromNow: () => `${minutes} minutes ago`}
    if (hours < 2) return {fromNow: () => "an hour ago"}
    if (hours < 22) return {fromNow: () => `${hours} hours ago`}
    if (days < 2) return {fromNow: () => "a day ago"}
    return {fromNow: () => `${days} days ago`}
})
setDateFormatter((date: string | Date, format?: string) => {
    const d = new Date(date)
    if (format === "LL") return d.toLocaleDateString(undefined, {year: "numeric", month: "long", day: "numeric"})
    if (format === "L LTS") return d.toLocaleString()
    return d.toLocaleString(undefined, {year: "numeric", month: "short", day: "numeric", hour: "2-digit", minute: "2-digit"})
})

const NOW = new Date().toISOString()
const ONE_HOUR_AGO = new Date(Date.now() - 3_600_000).toISOString()
const TWO_DAYS_AGO = new Date(Date.now() - 2 * 86_400_000).toISOString()

const meta: Meta<typeof KsDateAgo> = {
    title: "Components/Data/KsDateAgo",
    component: KsDateAgo,
    tags: ["autodocs"],
    argTypes: {
        date: {control: "text"},
        inverted: {control: "boolean"},
        format: {control: "text"},
        className: {control: "text"},
        showTooltip: {control: "boolean"},
    },
    parameters: {
        docs: {description: {component: "KsDateAgo displays a date as a relative time (e.g. \"2 hours ago\") with a tooltip showing the full formatted date, or inverted to show the full date with a relative-time tooltip. Requires `setMomentInstance` and `setDateFormatter` to be called at application startup."}},
    },
}
export default meta
type Story = StoryObj<typeof KsDateAgo>

export const Default: Story = {
    render: (args) => ({
        components: {KsDateAgo},
        setup() { return {args} },
        template: "<div style=\"padding:24px\"><ks-date-ago v-bind=\"args\" /></div>",
    }),
    args: {date: ONE_HOUR_AGO, showTooltip: true},
}

export const Inverted: Story = {
    render: () => ({
        components: {KsDateAgo},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-date-ago :date="'${ONE_HOUR_AGO}'" />
                <ks-date-ago :date="'${ONE_HOUR_AGO}'" :inverted="true" />
            </div>
        `,
    }),
}

export const NoTooltip: Story = {
    render: () => ({
        components: {KsDateAgo},
        template: `
            <div style="padding:24px;display:flex;flex-direction:column;gap:12px">
                <ks-date-ago :date="'${TWO_DAYS_AGO}'" :show-tooltip="false" />
                <ks-date-ago :date="'${TWO_DAYS_AGO}'" :inverted="true" :show-tooltip="false" />
            </div>
        `,
    }),
}

export const CustomFormat: Story = {
    render: () => ({
        components: {KsDateAgo},
        template: `
            <div style="padding:24px">
                <ks-date-ago :date="'${NOW}'" :inverted="true" format="LL" />
            </div>
        `,
    }),
}

export const NoDate: Story = {
    render: () => ({
        components: {KsDateAgo},
        template: "<div style=\"padding:24px\"><ks-date-ago /></div>",
    }),
}
