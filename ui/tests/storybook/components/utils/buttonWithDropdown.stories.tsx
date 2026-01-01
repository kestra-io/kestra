import type {Meta, StoryObj} from "@storybook/vue3";
import ButtonWithDropdown from "../../../../src/components/utils/buttonWithDropdown.vue";
import {markRaw} from "vue";
import {Download, VideoPlay, VideoPause, Delete, Edit, Setting, MoreFilled} from "@element-plus/icons-vue";


const meta: Meta<typeof ButtonWithDropdown> = {
    title: "components/utils/ButtonWithDropdown",
    component: ButtonWithDropdown,
    parameters: {
        layout: "centered",
        docs: {
            description: {
                component: `
A reusable button with dropdown component that supports primary actions and secondary dropdown actions.
This component consolidates the various "button with dropdown" patterns used across the Kestra UI.

## Features
- Primary button with optional dropdown
- Configurable dropdown items with icons
- Multiple trigger types (click, hover, contextmenu)
- Flexible positioning
- Loading and disabled states
- Custom styling support
- TypeScript support with full type safety
                `
            }
        }
    },
    argTypes: {
        primaryText: {
            control: "text",
            description: "Text displayed on the primary button"
        },
        primaryButtonType: {
            control: "select",
            options: ["primary", "success", "warning", "danger", "info", "default"],
            description: "Type of the primary button"
        },
        trigger: {
            control: "select",
            options: ["click", "hover", "contextmenu"],
            description: "How the dropdown is triggered"
        },
        placement: {
            control: "select",
            options: ["top", "top-start", "top-end", "bottom", "bottom-start", "bottom-end", "left", "left-start", "left-end", "right", "right-start", "right-end"],
            description: "Position of the dropdown menu"
        },
        size: {
            control: "select",
            options: ["large", "default", "small"],
            description: "Size of the button"
        },
        loading: {
            control: "boolean",
            description: "Whether the button is in loading state"
        },
        disabled: {
            control: "boolean",
            description: "Whether the button is disabled"
        },
        showDropdownIcon: {
            control: "boolean",
            description: "Whether to show the dropdown arrow icon"
        }
    }
};

export default meta;
type Story = StoryObj<typeof meta>;

// Basic example with dropdown items (split button)
export const Default: Story = {
    args: {
        primaryText: "Actions",
        showDropdownIcon: true,
        dropdownItems: [
            {
                command: "download",
                label: "Download",
                icon: markRaw(Download)
            },
            {
                command: "edit",
                label: "Edit",
                icon: markRaw(Edit)
            },
            {
                command: "delete",
                label: "Delete",
                icon: markRaw(Delete),
                divided: true
            }
        ] as any
    }
};

// Primary action only (single button - no dropdown)
export const PrimaryOnly: Story = {
    args: {
        primaryText: "Run Flow",
        primaryButtonType: "success",
        primaryIcon: markRaw(VideoPlay),
        showDropdownIcon: false,
        dropdownItems: []
    }
};

// Split button style (main action + dropdown)
export const SplitButton: Story = {
    args: {
        primaryText: "Execute",
        primaryButtonType: "primary",
        primaryIcon: markRaw(VideoPlay),
        showDropdownIcon: true,
        dropdownItems: [
            {
                command: "execute-now",
                label: "Execute Now",
                icon: markRaw(VideoPlay)
            },
            {
                command: "schedule",
                label: "Schedule Execution",
                icon: markRaw(Setting)
            },
            {
                command: "execute-with-inputs",
                label: "Execute with Inputs",
                icon: markRaw(Edit),
                divided: true
            }
        ] as any
    }
};

// Different button types (all as split buttons)
export const Variants: Story = {
    render: () => ({
        components: {ButtonWithDropdown},
        setup() {            
            return () => (
                <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                    <ButtonWithDropdown primaryText="Primary" primaryButtonType="primary" showDropdownIcon={true} dropdownItems={[{command: "settings", label: "Settings", icon: markRaw(Setting)}]} />
                    <ButtonWithDropdown primaryText="Success" primaryButtonType="success" showDropdownIcon={true} dropdownItems={[{command: "settings", label: "Settings", icon: markRaw(Setting)}]} />
                    <ButtonWithDropdown primaryText="Warning" primaryButtonType="warning" showDropdownIcon={true} dropdownItems={[{command: "settings", label: "Settings", icon: markRaw(Setting)}]} />
                    <ButtonWithDropdown primaryText="Danger" primaryButtonType="danger" showDropdownIcon={true} dropdownItems={[{command: "settings", label: "Settings", icon: markRaw(Setting)}]} />
                    <ButtonWithDropdown primaryText="Info" primaryButtonType="info" showDropdownIcon={true} dropdownItems={[{command: "settings", label: "Settings", icon: markRaw(Setting)}]} />
                </div>
            );
        }
    })
};

// Different sizes (showing both single and split modes)
export const Sizes: Story = {
    render: () => ({
        components: {ButtonWithDropdown},
        setup() {
            const items = [
                {command: "action", label: "Action", icon: markRaw(Setting)}
            ];
            
            return () => (
                <div style="display: flex; gap: 12px; align-items: center;">
                    <ButtonWithDropdown primaryText="Large" size="large" showDropdownIcon={true} dropdownItems={items} />
                    <ButtonWithDropdown primaryText="Default" size="default" showDropdownIcon={true} dropdownItems={items} />
                    <ButtonWithDropdown primaryText="Small" size="small" showDropdownIcon={true} dropdownItems={items} />
                    <div style="margin-left: 20px;">
                        <ButtonWithDropdown primaryText="Single" size="default" showDropdownIcon={false} dropdownItems={[]} />
                    </div>
                </div>
            );
        }
    })
};

// States (demonstrating split vs single button modes)
export const States: Story = {
    render: () => ({
        components: {ButtonWithDropdown},
        setup() {
            const items = [
                {command: "action", label: "Action", icon: markRaw(Setting)}
            ];
            
            return () => (
                <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                    <ButtonWithDropdown primaryText="Normal Split" showDropdownIcon={true} dropdownItems={items} />
                    <ButtonWithDropdown primaryText="Loading Split" loading={true} showDropdownIcon={true} dropdownItems={items} />
                    <ButtonWithDropdown primaryText="Disabled Split" disabled={true} showDropdownIcon={true} dropdownItems={items} />
                    <div style="margin-left: 20px; display: flex; gap: 12px;">
                        <ButtonWithDropdown primaryText="Normal Single" showDropdownIcon={false} dropdownItems={[]} />
                        <ButtonWithDropdown primaryText="Disabled Single" disabled={true} showDropdownIcon={false} dropdownItems={[]} />
                    </div>
                </div>
            );
        }
    })
};

// Execution actions example (realistic use case with split button)
export const ExecutionActions: Story = {
    args: {
        primaryText: "Execute",
        primaryButtonType: "success",
        primaryIcon: markRaw(VideoPlay),
        showDropdownIcon: true,
        dropdownItems: [
            {
                command: "execute",
                label: "Execute Now",
                icon: markRaw(VideoPlay)
            },
            {
                command: "execute-with-inputs",
                label: "Execute with Inputs",
                icon: markRaw(Edit)
            },
            {
                command: "schedule",
                label: "Schedule",
                icon: markRaw(Setting),
                divided: true
            },
            {
                command: "pause",
                label: "Pause",
                icon: markRaw(VideoPause)
            }
        ] as any
    }
};



// Interactive example with event handling (demonstrating split vs single)
export const Interactive: Story = {
    render: () => ({
        components: {ButtonWithDropdown},
        setup() {
            const handlePrimaryClick = () => {
                console.log("Primary button clicked");
            };
            
            const handleItemClick = (item: any) => {
                console.log("Dropdown item clicked:", item);
            };
            
            const items: any[] = [
                {command: "action1", label: "Action 1"},
                {command: "action2", label: "Action 2"},
                {command: "disabled", label: "Disabled Action", disabled: true}
            ];
            
            return () => (
                <div style="padding: 20px;">
                    <div style="margin-bottom: 20px;">
                        <h4>Split Button (Main + Dropdown)</h4>
                        <ButtonWithDropdown 
                            primaryText="Interactive Example"
                            showDropdownIcon={true}
                            dropdownItems={items}
                            onPrimary-click={handlePrimaryClick}
                            onItem-click={handleItemClick}
                        />
                    </div>
                    <div style="margin-bottom: 20px;">
                        <h4>Single Button (No Dropdown)</h4>
                        <ButtonWithDropdown 
                            primaryText="Single Action"
                            showDropdownIcon={false}
                            dropdownItems={[]}
                            onPrimary-click={handlePrimaryClick}
                        />
                    </div>
                    <p style="margin-top: 20px; color: #666; font-size: 14px;">
                        Check the console for event logs when interacting with the buttons.
                        The split button has separate click areas for main action and dropdown.
                    </p>
                </div>
            );
        }
    })
};
