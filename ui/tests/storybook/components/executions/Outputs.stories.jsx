import {
    ElButton,
    ElDropdown,
    ElDropdownItem,
    ElDropdownMenu,
} from "element-plus";
import {expect, userEvent, waitFor, within} from "storybook/test";

import Outputs from "../../../../src/components/executions/Outputs.vue";

export default {
    title: "Components/Executions/Outputs",
    component: Outputs,
};

const Disabled = {
    render: () => ({
        components: {
            ElButton,
            ElDropdown,
            ElDropdownItem,
            ElDropdownMenu,
            Outputs,
        },
        template: `
            <div style="min-height: 320px; padding: 48px; background: var(--ks-background-body)">
                <el-dropdown trigger="click">
                    <el-button>Options</el-button>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item>Metrics</el-dropdown-item>
                            <Outputs :outputs="{}" :execution="{}" />
                            <el-dropdown-item>Replay</el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
            </div>
        `,
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        await userEvent.click(canvas.getByRole("button", {name: "Options"}));

        const page = within(canvasElement.ownerDocument.body);
        const disabledItem = await page.findByRole("menuitem", {name: "Outputs"});
        const expectedColor = getComputedStyle(canvasElement.ownerDocument.documentElement)
            .getPropertyValue("--ks-content-inactive")
            .trim();

        const probe = canvasElement.ownerDocument.createElement("span");
        probe.style.color = expectedColor;
        canvasElement.ownerDocument.body.appendChild(probe);
        const resolvedExpectedColor = getComputedStyle(probe).color;
        probe.remove();

        await expect(disabledItem).toHaveAttribute("aria-disabled", "true");
        await waitFor(() => expect(getComputedStyle(disabledItem).color).toBe(resolvedExpectedColor));

        const icon = disabledItem.querySelector("i");
        await expect(icon).toBeTruthy();
        await expect(getComputedStyle(icon).color).toBe(resolvedExpectedColor);
    },
};

export const DisabledDarkMode = {
    ...Disabled,
    globals: {
        theme: "dark",
    },
};

export const DisabledLightMode = {
    ...Disabled,
    globals: {
        theme: "light",
    },
};
