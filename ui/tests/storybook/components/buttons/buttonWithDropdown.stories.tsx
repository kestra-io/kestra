import type { Meta, StoryObj } from "@storybook/vue3";
import ButtonWithDropdown from "../../../../src/components/buttons/buttonWithDropdown.vue";
import { markRaw } from "vue";
import { Download, VideoPlay, Delete, Edit, Setting } from "@element-plus/icons-vue";

const meta: Meta<typeof ButtonWithDropdown> = {
    title: "components/utils/ButtonWithDropdown",
    component: ButtonWithDropdown,
};

export default meta;

export const Default: StoryObj<typeof ButtonWithDropdown> = {
    render: () => ({
        components: { ButtonWithDropdown },
        setup() {
            const items = [
                {
                    command: "download",
                    label: "Download",
                    icon: markRaw(Download),
                    action: (item: any) => {
                        console.log("Download clicked:", item);
                    }
                },
                {
                    command: "edit",
                    label: "Edit",
                    icon: markRaw(Edit),
                    action: (item: any) => {
                        console.log("Edit clicked:", item);
                    }
                },
                {
                    command: "delete",
                    label: "Delete",
                    icon: markRaw(Delete),
                    divided: true,
                    action: (item: any) => {
                        console.log("Delete clicked:", item);
                    }
                },
            ] as any;

            const handleMainButtonClick = () => {
                console.log("Main button clicked by user");
            };

            return () => (
                <div style="display: flex; justify-content: center; align-items: center; min-height: 100px;">
                    <ButtonWithDropdown
                        text="Actions"
                        type="primary"

                        dropdownItems={items}
                        onPrimary-click={handleMainButtonClick}

                    />
                </div>
            );
        },
    }),
};

export const Primary: StoryObj<typeof ButtonWithDropdown> = {
    render: () => ({
        components: { ButtonWithDropdown },
        setup() {
            const handleClick = () => {
                console.log("primary button clicked by user");
            };

            return () => (
                <div style="display: flex; justify-content: center; align-items: center; min-height: 100px;">
                    <ButtonWithDropdown
                        type="primary"
                        icon={markRaw(VideoPlay)}
                        text="Run flow"
                        onPrimary-click={handleClick}
                    />
                </div>
            );
        },
    }),
};

export const Variants: StoryObj<typeof ButtonWithDropdown> = {
    render: () => ({
        components: { ButtonWithDropdown },
        setup() {
            const handleClick = () => {
                console.log("primary button clicked by user");
            };
            return () => (
                <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                    <ButtonWithDropdown
                        text="Primary"
                        type="primary"
                        showDropdownIcon={true}
                        dropdownItems={[{ command: "settings", label: "Settings", icon: markRaw(Setting), action: (item: any) => console.log("Settings clicked:", item) }]}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Success"
                        type="success"
                        showDropdownIcon={true}
                        dropdownItems={[{ command: "settings", label: "Settings", icon: markRaw(Setting), action: (item: any) => console.log("Settings clicked:", item) }]}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Warning"
                        type="warning"
                        showDropdownIcon={true}
                        dropdownItems={[{ command: "settings", label: "Settings", icon: markRaw(Setting), action: (item: any) => console.log("Settings clicked:", item) }]}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Danger"
                        type="danger"
                        showDropdownIcon={true}
                        dropdownItems={[{ command: "settings", label: "Settings", icon: markRaw(Setting), action: (item: any) => console.log("Settings clicked:", item) }]}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Info"
                        type="info"
                        showDropdownIcon={true}
                        dropdownItems={[{ command: "settings", label: "Settings", icon: markRaw(Setting), action: (item: any) => console.log("Settings clicked:", item) }]}
                        onPrimary-click={handleClick}
                    />
                </div>
            );
        },
    }),
};

export const Sizes: StoryObj<typeof ButtonWithDropdown> = {
    render: () => ({
        components: { ButtonWithDropdown },
        setup() {
            const items = [
                { command: "action", label: "Action", icon: markRaw(Setting), action: (item: any) => console.log("Action clicked:", item) },
            ];
            const handleClick = () => {
                console.log("button clicked by user");
            };

            return () => (
                <div style="display: flex; gap: 12px; align-items: center;">
                    <ButtonWithDropdown
                        text="Large"
                        type="primary"
                        size="large"
                        showDropdownIcon={true}
                        dropdownItems={items}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Default"
                        type="primary"
                        size="default"
                        showDropdownIcon={true}
                        dropdownItems={items}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Small"
                        type="primary"
                        size="small"
                        showDropdownIcon={true}
                        dropdownItems={items}
                        onPrimary-click={handleClick}
                    />
                    <div style="margin-left: 20px;">
                        <ButtonWithDropdown
                            text="Single"
                            type="primary"
                            size="default"
                            showDropdownIcon={false}
                            dropdownItems={[]}
                            onPrimary-click={handleClick}
                        />
                    </div>
                </div>
            );
        },
    }),
};

export const States: StoryObj<typeof ButtonWithDropdown> = {
    render: () => ({
        components: { ButtonWithDropdown },
        setup() {
            const items = [
                { command: "action", label: "Action", icon: markRaw(Setting), action: (item: any) => console.log("Action clicked:", item) },
            ];

              const handleClick = () => {
                console.log("button clicked by user");
            };

            return () => (
                <div style="display: flex; gap: 12px; flex-wrap: wrap;">
                    <ButtonWithDropdown
                        text="Normal Split"
                        type="primary"
                        dropdownItems={items}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Loading Split"
                        type="primary"
                        loading={true}
                        dropdownItems={items}
                        onPrimary-click={handleClick}
                    />
                    <ButtonWithDropdown
                        text="Disabled Split"
                        type="primary"
                        disabled={true}
                        dropdownItems={items}
                        onPrimary-click={handleClick}
                        />
                    <div style="margin-left: 20px; display: flex; gap: 12px;">
                        <ButtonWithDropdown
                            text="Normal Single"
                            type="primary"
                            onPrimary-click={handleClick}
                        />
                        <ButtonWithDropdown
                            text="Disabled Single"
                            type="primary"
                            disabled={true}
                            onPrimary-click={handleClick}
                        />
                    </div>
                </div>
            );
        },
    }),
};



