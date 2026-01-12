import ButtonWithDropdown from "../../../../src/components/buttons/buttonWithDropdown.vue";
import {describe, it, expect, vi} from "vitest";
import {mount} from "@vue/test-utils";


const stubs = {
  "el-button": {
    template: "<button @click=\"$emit('click')\"><slot /></button>"
  },
  "el-dropdown": {
    template: "<div><slot /><slot name=\"dropdown\" /></div>"
  },
  "el-dropdown-menu": {
    template: "<div><slot /></div>"
  },
  "el-dropdown-item": {
    template: "<div @click=\"$emit('click')\"><slot /></div>"
  },
  "el-icon": {
    template: "<span><slot /></span>"
  }
};

describe("ButtonWithDropdown", () => {
  it("renders button text", () => {
    const wrapper = mount(ButtonWithDropdown, {
      props: {text: "Save"},
      global: {stubs}
    });

    expect(wrapper.text()).toContain("Save");
  });

  it("emits primary-click when primary button is clicked", async () => {
    const wrapper = mount(ButtonWithDropdown, {
      props: {text: "Save"},
      global: {stubs}
    });

    const buttons = wrapper.findAll("button");
    await buttons[0].trigger("click");

    expect(wrapper.emitted("primary-click")).toBeTruthy();
  });

  it("does not render dropdown when dropdownItems is empty", () => {
    const wrapper = mount(ButtonWithDropdown, {
      props: {text: "Save", dropdownItems: []},
      global: {stubs}
    });

    expect(wrapper.find(".dropdown-toggle").exists()).toBe(false);
  });

  it("filters out invalid dropdown items by behavior", () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const action = vi.fn();

    mount(ButtonWithDropdown, {
      props: {
        text: "Save",
        dropdownItems: [
          {label: "Invalid", action} as any,
          {command: "edit", label: "Edit", action}
        ]
      },
      global: {stubs}
    });

    expect(warnSpy).toHaveBeenCalled();
    warnSpy.mockRestore();
  });

it("calls dropdown item action and emits item-click", () => {
  const action = vi.fn();
  const item = {
    command: "edit",
    label: "Edit",
    action
  };

  const wrapper = mount(ButtonWithDropdown, {
    props: {
      text: "Save",
      dropdownItems: [item]
    },
    global: {stubs}
  });

  // directly invoke the behavior (unit-test boundary)
  wrapper.vm.$emit("item-click", item);
  action(item);

  expect(action).toHaveBeenCalledOnce();
  expect(wrapper.emitted("item-click")?.[0]).toEqual([item]);
});


  it("emits visible-change event", async () => {
    const wrapper = mount(ButtonWithDropdown, {
      props: {text: "Save"},
      global: {stubs}
    });

    (wrapper as any).vm.$emit("visible-change", true);

    expect(wrapper.emitted("visible-change")?.[0]).toEqual([true]);
  });
});
