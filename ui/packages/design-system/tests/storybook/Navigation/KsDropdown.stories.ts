import type {Meta, StoryObj} from "@storybook/vue3-vite"
import {expect} from "storybook/test"
import {ref} from "vue"
import KsButton from "../../../src/components/Basic/KsButton/KsButton.vue"
import KsDropdown from "../../../src/components/Navigation/KsDropdown/KsDropdown.vue"
import KsDropdownItem from "../../../src/components/Navigation/KsDropdown/KsDropdownItem.vue"
import KsDropdownMenu from "../../../src/components/Navigation/KsDropdown/KsDropdownMenu.vue"

const meta: Meta<typeof KsDropdown> = {
    title: "Components/Navigation/KsDropdown",
    component: KsDropdown,
    tags: ["autodocs"],
    parameters: {
        docs: {
            description: {
                component: "KsDropdown is the Kestra design-system abstraction over `ElDropdown` from Element Plus.",
            },
        },
    },
}
export default meta
type Story = StoryObj<typeof KsDropdown>

/** Basic usage – hover to unfold */
export const Default: Story = {
    render: () => ({
        components: {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu},
        template: `
            <div style="padding:48px">
                <ks-dropdown>
                    <ks-button type="primary">Actions <span style="font-size:0.7em">▼</span></ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item command="edit">Edit</ks-dropdown-item>
                            <ks-dropdown-item command="duplicate">Duplicate</ks-dropdown-item>
                            <ks-dropdown-item divided command="delete" disabled>Delete</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
            </div>
        `,
    }),
}

/** How to trigger – hover, click, contextmenu */
export const Triggers: Story = {
    render: () => ({
        components: {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu},
        template: `
            <div style="padding:48px;display:flex;gap:16px;flex-wrap:wrap">
                <ks-dropdown trigger="hover">
                    <ks-button>Hover</ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>Option 1</ks-dropdown-item>
                            <ks-dropdown-item>Option 2</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
                <ks-dropdown trigger="click">
                    <ks-button>Click</ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>Option 1</ks-dropdown-item>
                            <ks-dropdown-item>Option 2</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
                <ks-dropdown trigger="contextmenu">
                    <ks-button>Right-click</ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>Option 1</ks-dropdown-item>
                            <ks-dropdown-item>Option 2</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
            </div>
        `,
    }),
}

/** Command event – items with command values */
export const CommandEvent: Story = {
    render: () => ({
        components: {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu},
        setup() {
            const lastCmd = ref<string | null>(null)
            return {lastCmd}
        },
        template: `
            <div style="padding:48px">
                <ks-dropdown @command="lastCmd = $event">
                    <ks-button type="primary">Actions ▼</ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item command="run">Run now</ks-dropdown-item>
                            <ks-dropdown-item command="edit">Edit flow</ks-dropdown-item>
                            <ks-dropdown-item command="export">Export YAML</ks-dropdown-item>
                            <ks-dropdown-item divided command="delete">Delete</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
                <p style="margin-top:12px;font-size:13px;opacity:0.6">
                    Last command: {{ lastCmd || 'none' }}
                </p>
            </div>
        `,
    }),
}

/** Disabled items */
export const WithDisabledItems: Story = {
    render: () => ({
        components: {KsButton, KsDropdown, KsDropdownItem, KsDropdownMenu},
        template: `
            <div style="padding:48px">
                <ks-dropdown>
                    <ks-button>Options ▼</ks-button>
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>Enabled option</ks-dropdown-item>
                            <ks-dropdown-item disabled>Disabled option</ks-dropdown-item>
                            <ks-dropdown-item divided>Another option</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
            </div>
        `,
    }),
}

/**
 * Split button – the label runs the default action, the caret opens the menu.
 * The halves are plain `ElButton`s, so pass `buttonProps: {plain: true}` to get the
 * secondary look that `KsButton` applies by default.
 */
export const SplitButton: Story = {
    render: () => ({
        components: {KsDropdown, KsDropdownItem, KsDropdownMenu},
        template: `
            <div style="padding:48px;display:flex;gap:16px;flex-wrap:wrap;align-items:center">
                <ks-dropdown split-button type="primary">
                    Save
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>Save as draft</ks-dropdown-item>
                            <ks-dropdown-item>Save and execute</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
                <ks-dropdown split-button type="default" :button-props="{plain: true}">
                    Download
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>September 2026</ks-dropdown-item>
                            <ks-dropdown-item>August 2026</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
                <ks-dropdown split-button type="primary" disabled>
                    Disabled
                    <template #dropdown>
                        <ks-dropdown-menu>
                            <ks-dropdown-item>Option</ks-dropdown-item>
                        </ks-dropdown-menu>
                    </template>
                </ks-dropdown>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const carets = Array.from(canvasElement.querySelectorAll<HTMLElement>(".kel-dropdown__caret-button"))
        await expect(carets).toHaveLength(3)
        // every variant separates its halves with a divider that stands out from the button fill
        for (const caret of carets) {
            const divider = getComputedStyle(caret, "::before")
            await expect(divider.width).toBe("1px")
            await expect(divider.opacity).toBe("1")
            await expect(divider.backgroundColor).not.toBe(getComputedStyle(caret).backgroundColor)
        }
    },
}
