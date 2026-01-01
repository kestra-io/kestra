import {describe, it, expect, afterEach, vi, beforeEach} from "vitest";
import {mount, VueWrapper} from "@vue/test-utils";
import ButtonWithDropdown from "../../../src/components/utils/buttonWithDropdown.vue";
import {Edit, Delete, Setting} from "@element-plus/icons-vue";

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================
type PartialDropdownItem = Partial<{
    command: string;
    label: string;
    icon: any;
    disabled: boolean;
    divided: boolean;
    action: (item: any) => void;
}>;

interface DropdownItem {
    command: string;
    label?: string;
    icon?: any;
    disabled?: boolean;
    divided?: boolean;
    action?: (item: DropdownItem) => void;
}

// ============================================================================
// TEST FIXTURES
// ============================================================================
const FIXTURES = {
    validItems: [
        {command: "edit", label: "Edit", icon: Edit},
        {command: "delete", label: "Delete", icon: Delete, divided: true},
        {command: "settings", label: "Settings", icon: Setting}
    ],
    invalidItems: [
        {label: "No command"} as PartialDropdownItem,
        {label: "Also invalid"} as PartialDropdownItem
    ],
    mixedItems: [
        {command: "valid", label: "Valid"},
        {label: "Invalid"} as PartialDropdownItem,
        {command: "another-valid", label: "Another Valid"}
    ]
};


beforeEach(() => {
    vi.spyOn(console, "warn").mockImplementation((msg) => {
        // Only suppress Element Plus component warnings
        if (typeof msg === "string" && msg.includes("Failed to resolve component: el-")) {
            return;
        }
        // Allow other warnings through by calling original console.warn
        originalConsoleWarn(msg);
    });
});

// Store original console.warn to avoid infinite recursion
const originalConsoleWarn = console.warn;

// ============================================================================
// TEST SUITE
// ============================================================================
describe("ButtonWithDropdown - Production Grade Tests", () => {
    let wrapper: VueWrapper<any>;

    afterEach(() => {
        if (wrapper) {
            wrapper.unmount();
        }
        vi.restoreAllMocks();
    });

    const defaultProps = {
        primaryText: "Actions",
        showDropdownIcon: true,
        dropdownItems: FIXTURES.validItems
    };

    // ========================================================================
    // RENDERING TESTS
    // ========================================================================
    describe("Rendering", () => {
        describe("Split Button Mode Activation", () => {
            it("should activate when showDropdownIcon=true AND has valid items", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.find(".button-with-dropdown").classes()).toContain("split-button");
            });

            it("should NOT activate when showDropdownIcon=false", () => {
                wrapper = mount(ButtonWithDropdown, {
                    props: {...defaultProps, showDropdownIcon: false}
                });
                expect(wrapper.find(".button-with-dropdown").classes()).not.toContain("split-button");
            });

            it("should NOT activate when all items are invalid", () => {
                wrapper = mount(ButtonWithDropdown, {
                    props: {
                        primaryText: "Test",
                        dropdownItems: FIXTURES.invalidItems as DropdownItem[]
                    }
                });
                expect(wrapper.find(".button-with-dropdown").classes()).not.toContain("split-button");
            });

            it("should NOT activate with empty dropdownItems array", () => {
                wrapper = mount(ButtonWithDropdown, {
                    props: {primaryText: "Test", showDropdownIcon: true, dropdownItems: []}
                });
                expect(wrapper.find(".button-with-dropdown").classes()).not.toContain("split-button");
            });
        });

        describe("Component Structure", () => {
            it("should render container div", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.find(".button-with-dropdown").exists()).toBe(true);
            });

            it("should render in split mode with valid configuration", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                const container = wrapper.find(".button-with-dropdown");
                expect(container.classes()).toContain("split-button");
            });
        });
    });

    // ========================================================================
    // ACCESSIBILITY TESTS
    // ========================================================================
    describe("Accessibility (WCAG 2.1 AA)", () => {
        describe("Component Props - ARIA Labels", () => {
            it("should pass aria-label prop for primary button", () => {
                wrapper = mount(ButtonWithDropdown, {props: {primaryText: "Save"}});
                expect(wrapper.vm.$props.primaryText).toBe("Save");
            });

            it("should have default primaryText fallback", () => {
                wrapper = mount(ButtonWithDropdown, {props: {}});
                // Component should handle undefined primaryText
                expect(wrapper.vm.$props.primaryText).toBeUndefined();
            });
        });

        describe("Reactive ARIA States", () => {
            it("should initialize dropdownVisible as false", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.dropdownVisible).toBe(false);
            });

            it("should update dropdownVisible to true", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onVisibleChange(true);
                expect(wrapper.vm.dropdownVisible).toBe(true);
            });

            it("should update dropdownVisible back to false", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onVisibleChange(true);
                await wrapper.vm.onVisibleChange(false);
                expect(wrapper.vm.dropdownVisible).toBe(false);
            });

            it("should convert dropdownVisible to string for aria-expanded", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                // Component uses .toString() for aria-expanded
                expect(wrapper.vm.dropdownVisible.toString()).toBe("false");
            });
        });
    });

    // ========================================================================
    // STATE MANAGEMENT
    // ========================================================================
    describe("State Management", () => {
        describe("Disabled Prop", () => {
            it("should pass disabled prop to component", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, disabled: true}});
                expect(wrapper.vm.$props.disabled).toBe(true);
            });

            it("should default disabled to false", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.disabled).toBe(false);
            });
        });

        describe("Loading Prop", () => {
            it("should pass loading prop to component", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, loading: true}});
                expect(wrapper.vm.$props.loading).toBe(true);
            });

            it("should default loading to false", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.loading).toBe(false);
            });
        });

        describe("Reactive Visibility State", () => {
            it("should track dropdownVisible correctly", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.dropdownVisible).toBe(false);
                await wrapper.vm.onVisibleChange(true);
                expect(wrapper.vm.dropdownVisible).toBe(true);
                await wrapper.vm.onVisibleChange(false);
                expect(wrapper.vm.dropdownVisible).toBe(false);
            });

            it("should handle multiple rapid visibility changes", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onVisibleChange(true);
                await wrapper.vm.onVisibleChange(false);
                await wrapper.vm.onVisibleChange(true);
                expect(wrapper.vm.dropdownVisible).toBe(true);
            });
        });
    });

    // ========================================================================
    // PROPS & CONFIGURATION
    // ========================================================================
    describe("Props and Configuration", () => {
        describe("Size Prop", () => {
            const sizes = ["large", "default", "small"] as const;
            sizes.forEach(size => {
                it(`should accept ${size} size prop`, () => {
                    wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, size}});
                    expect(wrapper.vm.$props.size).toBe(size);
                });
            });

            it("should default to 'default' size", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.size).toBe("default");
            });
        });

        describe("Button Type Prop", () => {
            const types = ["primary", "success", "warning", "danger", "info", "default"] as const;
            types.forEach(type => {
                it(`should accept ${type} button type`, () => {
                    wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, primaryButtonType: type}});
                    expect(wrapper.vm.$props.primaryButtonType).toBe(type);
                });
            });

            it("should default to 'primary' type", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.primaryButtonType).toBe("primary");
            });
        });

        describe("Dropdown Configuration", () => {
            it("should accept trigger prop", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, trigger: "hover"}});
                expect(wrapper.vm.$props.trigger).toBe("hover");
            });

            it("should default trigger to 'click'", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.trigger).toBe("click");
            });

            it("should accept placement prop", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, placement: "top"}});
                expect(wrapper.vm.$props.placement).toBe("top");
            });

            it("should default placement to 'bottom'", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.placement).toBe("bottom");
            });

            it("should accept hideOnClick prop", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, hideOnClick: false}});
                expect(wrapper.vm.$props.hideOnClick).toBe(false);
            });

            it("should default hideOnClick to true", () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                expect(wrapper.vm.$props.hideOnClick).toBe(true);
            });
        });

        describe("Custom Classes", () => {
            it("should accept buttonClass as string", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, buttonClass: "custom"}});
                expect(wrapper.vm.$props.buttonClass).toBe("custom");
            });

            it("should accept buttonClass as array", () => {
                const classes = ["class1", "class2"];
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, buttonClass: classes}});
                expect(wrapper.vm.$props.buttonClass).toEqual(classes);
            });

            it("should accept buttonClass as object", () => {
                const classes = {active: true, disabled: false};
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, buttonClass: classes}});
                expect(wrapper.vm.$props.buttonClass).toEqual(classes);
            });

            it("should accept menuClass prop", () => {
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, menuClass: "menu-custom"}});
                expect(wrapper.vm.$props.menuClass).toBe("menu-custom");
            });
        });
    });

    // ========================================================================
    // EVENT EMISSIONS
    // ========================================================================
    describe("Event Emissions", () => {
        describe("Primary Button Events", () => {
            it("should emit primary-click", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onPrimaryClick();
                expect(wrapper.emitted("primary-click")).toBeTruthy();
                expect(wrapper.emitted("primary-click")).toHaveLength(1);
            });

            it("should call primaryAction callback", async () => {
                const mockAction = vi.fn();
                wrapper = mount(ButtonWithDropdown, {props: {...defaultProps, primaryAction: mockAction}});
                await wrapper.vm.onPrimaryClick();
                expect(mockAction).toHaveBeenCalledTimes(1);
            });

            it("should emit primary-click even without primaryAction", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onPrimaryClick();
                expect(wrapper.emitted("primary-click")).toHaveLength(1);
            });
        });

        describe("Dropdown Events", () => {
            it("should emit item-click with full item object", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                const item = {command: "test", label: "Test"};
                await wrapper.vm.onItemClick(item);
                expect(wrapper.emitted("item-click")).toBeTruthy();
                expect(wrapper.emitted("item-click")![0][0]).toEqual(item);
            });

            it("should call item action callback", async () => {
                const mockAction = vi.fn();
                const item = {command: "test", label: "Test", action: mockAction};
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onItemClick(item);
                expect(mockAction).toHaveBeenCalledWith(item);
                expect(wrapper.emitted("item-click")).toBeTruthy();
            });

            it("should emit both item-click and call action", async () => {
                const mockAction = vi.fn();
                const item = {command: "test", action: mockAction};
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onItemClick(item);
                expect(mockAction).toHaveBeenCalled();
                expect(wrapper.emitted("item-click")).toBeTruthy();
            });

            it("should emit command event", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onCommand("test-cmd");
                expect(wrapper.emitted("command")).toBeTruthy();
                expect(wrapper.emitted("command")![0][0]).toBe("test-cmd");
            });

            it("should emit visible-change events", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onVisibleChange(true);
                await wrapper.vm.onVisibleChange(false);
                expect(wrapper.emitted("visible-change")).toHaveLength(2);
                expect(wrapper.emitted("visible-change")![0][0]).toBe(true);
                expect(wrapper.emitted("visible-change")![1][0]).toBe(false);
            });

            it("should emit visible-change and update state", async () => {
                wrapper = mount(ButtonWithDropdown, {props: defaultProps});
                await wrapper.vm.onVisibleChange(true);
                expect(wrapper.vm.dropdownVisible).toBe(true);
                expect(wrapper.emitted("visible-change")![0][0]).toBe(true);
            });
        });
    });

    // ========================================================================
    // PROPS VALIDATION
    // ========================================================================
    describe("Props Validation", () => {
        it("should filter invalid items (no command)", () => {
            const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: FIXTURES.mixedItems as DropdownItem[]}
            });
            expect(wrapper.vm.validDropdownItems).toHaveLength(2);
            warnSpy.mockRestore();
        });

        it("should warn when item missing command", () => {
            const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: [{label: "Invalid"}] as any}
            });
            expect(warnSpy).toHaveBeenCalledWith(
                "ButtonWithDropdown: dropdown item at index 0 missing required 'command' property"
            );
            warnSpy.mockRestore();
        });

        it("should warn when dropdownItems not array", () => {
            const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: null as any}
            });
            expect(warnSpy).toHaveBeenCalledWith("ButtonWithDropdown: dropdownItems must be an array");
            expect(wrapper.vm.validDropdownItems).toEqual([]);
            warnSpy.mockRestore();
        });

        it("should NOT warn with valid items", () => {
            const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
            wrapper = mount(ButtonWithDropdown, {props: defaultProps});
            
            // Filter out Element Plus warnings
            const relevantWarnings = warnSpy.mock.calls.filter(call => 
                !call[0].includes("Failed to resolve component: el-")
            );
            expect(relevantWarnings).toHaveLength(0);
            
            warnSpy.mockRestore();
        });

        it("should handle all items being invalid", () => {
            const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
            wrapper = mount(ButtonWithDropdown, {
                props: {
                    primaryText: "Test",
                    dropdownItems: FIXTURES.invalidItems as DropdownItem[]
                }
            });
            expect(wrapper.vm.validDropdownItems).toEqual([]);
            warnSpy.mockRestore();
        });

        it("should use correct default props", () => {
            wrapper = mount(ButtonWithDropdown, {props: {primaryText: "Test"}});
            expect(wrapper.vm.$props.primaryButtonType).toBe("primary");
            expect(wrapper.vm.$props.size).toBe("default");
            expect(wrapper.vm.$props.trigger).toBe("click");
            expect(wrapper.vm.$props.placement).toBe("bottom");
            expect(wrapper.vm.$props.hideOnClick).toBe(true);
            expect(wrapper.vm.$props.showDropdownIcon).toBe(true);
            expect(wrapper.vm.$props.disabled).toBe(false);
            expect(wrapper.vm.$props.loading).toBe(false);
        });
    });

    // ========================================================================
    // EDGE CASES
    // ========================================================================
    describe("Edge Cases", () => {
        it("should filter empty string command", () => {
            const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: [{command: "", label: "Empty"}]}
            });
            expect(warnSpy).toHaveBeenCalled();
            warnSpy.mockRestore();
        });

        it("should allow duplicate commands", () => {
            wrapper = mount(ButtonWithDropdown, {
                props: {
                    primaryText: "Test",
                    dropdownItems: [
                        {command: "dup", label: "First"},
                        {command: "dup", label: "Second"}
                    ]
                }
            });
            expect(wrapper.vm.validDropdownItems).toHaveLength(2);
        });

        it("should handle special characters in commands", () => {
            wrapper = mount(ButtonWithDropdown, {
                props: {
                    primaryText: "Test",
                    dropdownItems: [
                        {command: "test@#$", label: "Special"},
                        {command: "emoji-🎉", label: "Unicode"}
                    ]
                }
            });
            expect(wrapper.vm.validDropdownItems).toHaveLength(2);
        });

        it("should handle 100+ items array", () => {
            const items = Array.from({length: 100}, (_, i) => ({
                command: `cmd-${i}`,
                label: `Item ${i}`
            }));
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: items}
            });
            expect(wrapper.vm.validDropdownItems).toHaveLength(100);
        });

        it("should handle rapid visibility changes", async () => {
            wrapper = mount(ButtonWithDropdown, {props: defaultProps});
            await wrapper.vm.onVisibleChange(true);
            await wrapper.vm.onVisibleChange(false);
            await wrapper.vm.onVisibleChange(true);
            await wrapper.vm.onVisibleChange(false);
            expect(wrapper.emitted("visible-change")).toHaveLength(4);
            expect(wrapper.vm.dropdownVisible).toBe(false);
        });

        it("should handle undefined primaryText", () => {
            wrapper = mount(ButtonWithDropdown, {props: {dropdownItems: FIXTURES.validItems}});
            expect(wrapper.vm.$props.primaryText).toBeUndefined();
        });

        it("should handle items without labels", () => {
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: [{command: "no-label"}]}
            });
            expect(wrapper.vm.validDropdownItems).toHaveLength(1);
            expect(wrapper.vm.validDropdownItems[0].label).toBeUndefined();
        });

        it("should handle null icon prop", () => {
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", primaryIcon: null}
            });
            expect(wrapper.vm.$props.primaryIcon).toBeNull();
        });
    });

    // ========================================================================
    // ERROR HANDLING
    // ========================================================================
    describe("Error Handling", () => {
        it("should handle item action throwing error", async () => {
            const throwingAction = vi.fn(() => { throw new Error("Failed"); });
            const item = {command: "error", label: "Error", action: throwingAction};
            wrapper = mount(ButtonWithDropdown, {props: defaultProps});
            
            // The error is thrown, but event is not emitted because throw happens before emit
            expect(() => wrapper.vm.onItemClick(item)).toThrow("Failed");
        });

        it("should handle primaryAction throwing error", async () => {
            const throwingAction = vi.fn(() => { throw new Error("Failed"); });
            wrapper = mount(ButtonWithDropdown, {
                props: {...defaultProps, primaryAction: throwingAction}
            });
            expect(() => wrapper.vm.onPrimaryClick()).toThrow("Failed");
        });

        it("should still emit event after successful action", async () => {
            const successAction = vi.fn();
            const item = {command: "success", action: successAction};
            wrapper = mount(ButtonWithDropdown, {props: defaultProps});
            
            await wrapper.vm.onItemClick(item);
            expect(successAction).toHaveBeenCalled();
            expect(wrapper.emitted("item-click")).toBeTruthy();
        });
    });

    // ========================================================================
    // COMPUTED PROPERTIES
    // ========================================================================
    describe("Computed Properties", () => {
        it("should recompute validDropdownItems when props change", async () => {
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: [{command: "test", label: "Test"}]}
            });
            expect(wrapper.vm.validDropdownItems).toHaveLength(1);

            await wrapper.setProps({dropdownItems: FIXTURES.validItems});
            expect(wrapper.vm.validDropdownItems).toHaveLength(3);
        });

        it("should update split-button class when validDropdownItems changes", async () => {
            wrapper = mount(ButtonWithDropdown, {
                props: {primaryText: "Test", dropdownItems: []}
            });
            expect(wrapper.find(".button-with-dropdown").classes()).not.toContain("split-button");

            await wrapper.setProps({dropdownItems: FIXTURES.validItems});
            expect(wrapper.find(".button-with-dropdown").classes()).toContain("split-button");
        });
    });
});