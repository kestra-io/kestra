import { describe, it, expect, vi } from "vitest";
import { mount, config } from "@vue/test-utils";
import NodeMenu from "../../../src/nodes/NodeMenu.vue";
import NodeMenuItem from "../../../src/nodes/NodeMenuItem.vue";

// Mock i18n global method
config.global.mocks = {
    $t: (msg: string) => msg,
};

describe("NodeMenu.vue", () => {
    const mockActions = [
        {
            id: "action-1",
            label: "Edit",
            icon: "pencil-icon",
            onClick: vi.fn(),
            danger: false,
            divided: false,
        },
        {
            id: "action-2",
            label: "Delete",
            icon: "trash-icon",
            onClick: vi.fn(),
            danger: true,
            divided: true,
        },
    ];

    const mountOptions = {
        global: {
            stubs: {
                // Stub Element Plus components that require provider injection
                ElDropdown: { template: "<div><slot /><slot name='dropdown' /></div>" },
                ElDropdownMenu: { template: "<div><slot /></div>" },
                ElDropdownItem: { template: "<div><slot /></div>" },
                KsDropdown: { template: "<div><slot /><slot name='dropdown' /></div>" },
            },
        },
    };

    it("renders one menu item per action", () => {
        const wrapper = mount(NodeMenu, {
            ...mountOptions,
            props: { actions: mockActions },
        });
        const items = wrapper.findAllComponents(NodeMenuItem);
        expect(items).toHaveLength(mockActions.length);
    });

    it("shows each item's action label and icon", () => {
        const wrapper = mount(NodeMenu, {
            ...mountOptions,
            props: { actions: mockActions },
        });
        expect(wrapper.text()).toContain("Edit");
        expect(wrapper.text()).toContain("Delete");
    });

    it("calls onClick exactly once when an item is clicked", async () => {
        const wrapper = mount(NodeMenu, {
            ...mountOptions,
            props: { actions: mockActions },
        });
        const firstItem = wrapper.findComponent(NodeMenuItem);
        
        await firstItem.trigger("click");
        expect(mockActions[0].onClick).toHaveBeenCalledTimes(1);
    });

    it("applies node-action--danger class for danger actions and omits for others", () => {
        const wrapper = mount(NodeMenu, {
            ...mountOptions,
            props: { actions: mockActions },
        });
        const items = wrapper.findAllComponents(NodeMenuItem);
        
        expect(items[0].classes()).not.toContain("node-action--danger");
        expect(items[1].classes()).toContain("node-action--danger");
    });

    it("renders divided action as divided", () => {
        const wrapper = mount(NodeMenu, {
            ...mountOptions,
            props: { actions: mockActions },
        });
        const items = wrapper.findAllComponents(NodeMenuItem);
        expect(items[1].props("action").divided).toBe(true);
    });

    it("renders an empty menu without throwing when action list is empty", () => {
        expect(() => {
            mount(NodeMenu, {
                ...mountOptions,
                props: { actions: [] },
            });
        }).not.toThrow();
    });
});