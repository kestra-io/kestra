import type {Meta, StoryObj} from "@storybook/vue3"
import LogLevelNavigator from "../../../../src/components/logs/LogLevelNavigator.vue"

const LEVELS = ["ERROR", "WARN", "INFO", "DEBUG", "TRACE"] as const

const meta: Meta<typeof LogLevelNavigator> = {
    title: "Components/Logs/LogLevelNavigator",
    component: LogLevelNavigator,
    parameters: {layout: "padded"},
    argTypes: {
        level: {control: "select", options: LEVELS},
        totalCount: {control: "number"},
        cursorIdx: {control: "number"},
        filterMode: {control: "boolean"},
    },
}

export default meta
type Story = StoryObj<typeof meta>

export const Navigation: Story = {
    args: {level: "ERROR", totalCount: 12, cursorIdx: 2, filterMode: false},
}

export const NoSelection: Story = {
    args: {level: "WARN", totalCount: 7, filterMode: false},
}

export const FilterMode: Story = {
    args: {level: "INFO", totalCount: 43, filterMode: true},
}

export const AllLevels: Story = {
    render: () => ({
        components: {LogLevelNavigator},
        setup: () => () => (
            <ks-card style="display: flex; flex-direction: column; gap: var(--ks-spacing-2); padding: 1rem; width: 280px">
                {LEVELS.map((level, i) => (
                    <LogLevelNavigator key={level} level={level} totalCount={i * 5 + 3} cursorIdx={i === 0 ? 1 : undefined} filterMode={false} />
                ))}
            </ks-card>
        ),
    }),
}

export const AllLevelsFilterMode: Story = {
    render: () => ({
        components: {LogLevelNavigator},
        setup: () => () => (
            <ks-card style="display: flex; flex-direction: column; gap: var(--ks-spacing-2); padding: 1rem; width: 280px">
                {LEVELS.map((level, i) => (
                    <LogLevelNavigator key={level} level={level} totalCount={i * 3 + 1} filterMode={true} />
                ))}
            </ks-card>
        ),
    }),
}
