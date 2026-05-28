import type {Meta, StoryObj} from "@storybook/vue3-vite"
import KsIcon from "../../../src/components/Basic/KsIcon.vue"

const meta: Meta<typeof KsIcon> = {
    title: "Components/Basic/KsIcon",
    component: KsIcon,
    tags: ["autodocs"],
    argTypes: {
        size: {control: "select", options: ["xs", "sm", "base", "lg", "xl"]},
        color: {control: "color"},
    },
    parameters: {
        docs: {description: {component: "KsIcon is the Kestra design-system abstraction over `ElIcon` from Element Plus."}},
    },
}
export default meta
type Story = StoryObj<typeof KsIcon>

export const Default: Story = {
    render: (args) => ({
        components: {KsIcon},
        setup() { return {args} },
        template: `
            <div style="padding:24px">
                <ks-icon v-bind="args">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372zm19.8-481.6c-9.3 9.3-9.3 24.5 0 33.9L531.9 448H288c-13.3 0-24 10.7-24 24v16c0 13.3 10.7 24 24 24h243.9l-0.1 11.6c0 9 5 17.4 12.9 21.8s17.6 4.2 25.2-0.5l165.2-99.2c7-4.2 11.3-11.7 11.3-19.8s-4.3-15.6-11.3-19.8L570 306.3c-7.6-4.6-17.2-4.9-25.2-.5-7.9 4.4-12.9 12.8-12.9 21.8l0.1 11.6c-0.1 0-0.1 11.2-0.2 11.2z" fill="currentColor"/></svg>
                </ks-icon>
            </div>
        `,
    }),
    args: {size: "lg"},
}

/** Loading animation – spinning icon */
export const Loading: Story = {
    render: () => ({
        components: {KsIcon},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-icon size="lg" class="is-loading">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
                        <path d="M988 548c-19.9 0-36-16.1-36-36 0-59.4-11.6-117-34.6-171.3a440.45 440.45 0 0 0-94.3-139.9 437.71 437.71 0 0 0-139.9-94.3C629 83.6 571.4 72 512 72c-19.9 0-36-16.1-36-36s16.1-36 36-36c69.1 0 136.2 13.5 199.3 40.3C772.3 66 827 103 874 150c47 47 83.9 101.8 109.7 162.7 26.7 63.1 40.2 130.2 40.2 199.3.1 19.9-16 36-35.9 36z" fill="currentColor"/>
                    </svg>
                </ks-icon>
                <span style="font-size:13px;opacity:0.6">Spinning with is-loading class</span>
            </div>
        `,
    }),
}

/** Colors */
export const Colors: Story = {
    render: () => ({
        components: {KsIcon},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-icon size="lg" color="var(--ks-status-info)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
                <ks-icon size="lg" color="var(--ks-status-success)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
                <ks-icon size="lg" color="var(--ks-status-error)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
            </div>
        `,
    }),
}

/** Sizes — full token scale (xs / sm / base / lg / xl) */
export const Sizes: Story = {
    render: () => ({
        components: {KsIcon},
        template: `
            <div style="padding:24px;display:flex;gap:16px;align-items:center">
                <ks-icon size="xs" color="var(--ks-text-primary)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
                <ks-icon size="sm" color="var(--ks-text-primary)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
                <ks-icon size="base" color="var(--ks-text-primary)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
                <ks-icon size="lg" color="var(--ks-text-primary)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
                <ks-icon size="xl" color="var(--ks-text-primary)">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
            </div>
        `,
    }),
}

/** Tooltip */
export const Tooltip: Story = {
    render: (args) => ({
        components: {KsIcon},
        setup() { return {args} },
        template: `
            <div style="padding:24px">
                <ks-icon v-bind="args">
                    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm32 664h-64V448h64v280zm-32-340a48 48 0 1 1 0-96 48 48 0 0 1 0 96z" fill="currentColor"/></svg>
                </ks-icon>
            </div>
        `,
    }),
    args: {tooltip: "I'm a tooltip", size: "lg"},
}
