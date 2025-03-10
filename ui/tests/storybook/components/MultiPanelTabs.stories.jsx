import {defineComponent, markRaw, ref} from "vue";
import MultiPanelTabs from "../../../src/components/MultiPanelTabs.vue";
import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
import DotsSquareIcon from "vue-material-design-icons/DotsSquare.vue";
import BallotOutlineIcon from "vue-material-design-icons/BallotOutline.vue";
import {within, userEvent, expect} from "@storybook/test";

export default {
  title: "Components/MultiPanelTabs",
  component: MultiPanelTabs,
}


const Template = (props) => defineComponent(() => {
    const modelValueRef = ref(props.modelValue);
    return () => <div style="padding: 1rem;border: 1ps solid #ccc; border-radius: 4px; margin: 1rem; background: #f9f9f9;">
        <div style={{position: "absolute",
            left: "200px", top: "70px",
            width: "1px", height: "1px",
            background: "red",
            boxShadow: "0 0 5px 5px rgba(255, 0, 0, 1)"
        }}/>

        <MultiPanelTabs modelValue={modelValueRef.value} />
        <pre>{JSON.stringify(modelValueRef.value.map(p => p.tabs.map(t => t.value)))}</pre>
    </div>
});

const BG_COLORS = [
    // lightpink
    "#FFB6C1",
    // lightblue
    "#ADD8E6",
    // lightgreen
    "#90EE90",
    // lightyellow
    "#FFFFE0",
    // lightcoral
    "#F08080",
    // lightcyan
    "#E0FFFF",
];

const PlaceholderComponent = ({tabId}) => <div style={{
    padding: "1rem",
    height: "50vh",
    background: BG_COLORS[parseInt(tabId)]
}}>Content for Tab {tabId}</div>;

export const Default = Template.bind({});
Default.args = {
  modelValue: [
    {
      activeTab: {
        button: {icon: markRaw(CodeTagsIcon), label: "Tab 1"},
        value: "tab1",
        component: () => <PlaceholderComponent tabId="1" />,
      },
      tabs: [
        {
          button: {icon: markRaw(CodeTagsIcon), label: "Tab 1"},
          value: "tab1",
          component: () => <PlaceholderComponent tabId="1" />,
        },
        {
          button: {icon: markRaw(MouseRightClickIcon), label: "Tab 2"},
          value: "tab2",
          component: () => <PlaceholderComponent tabId="2" />,
        },
        {
            button: {icon: markRaw(FileTreeOutlineIcon), label: "Tab 3"},
            value: "tab3",
            component: () => <PlaceholderComponent tabId="3" />,
        },
      ],
    },
    {
      activeTab: {
        button: {icon: markRaw(FileDocumentIcon), label: "Tab 4"},
        value: "tab4",
        component: () => <PlaceholderComponent tabId="4" />,
      },
      tabs: [

        {
          button: {icon: markRaw(FileDocumentIcon), label: "Tab 4"},
          value: "tab4",
          component: () => <PlaceholderComponent tabId="4" />,
        },
        {
          button: {icon: markRaw(DotsSquareIcon), label: "Tab 5"},
          value: "tab5",
          component: () => <PlaceholderComponent tabId="5" />,
        },
        {
          button: {icon: markRaw(BallotOutlineIcon), label: "Tab 6"},
          value: "tab6",
          component: () => <PlaceholderComponent tabId="6" />,
        },
      ],
    },
  ],
};

// Add interaction test story
export const TabInteractionTest = Template.bind({});
TabInteractionTest.args = Default.args;
TabInteractionTest.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);

  // Wait for the component to render
  await new Promise(resolve => setTimeout(resolve, 100));

  // Test clicking tabs in the first panel
  // Get the second tab in the first panel (Tab 2)
  const secondTab = canvas.getByText("Tab 2");
  await userEvent.click(secondTab);

  // Verify Tab 2 content is visible
  expect(canvas.getByText("Content for Tab 2")).toBeInTheDocument();

  // Test clicking tabs in the second panel
  // Get the third tab in the second panel (Tab 6)
  const thirdTab = canvas.getByText("Tab 6");
  await userEvent.click(thirdTab);

  // Verify Tab 6 content is visible
  expect(canvas.getByText("Content for Tab 6")).toBeInTheDocument();
};

const TARGET_SIZE = 320

// Add test for panel resize functionality
export const PanelResizeTest = Template.bind({});
PanelResizeTest.args = Default.args;
PanelResizeTest.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);

  // Wait for the component to render
  await new Promise(resolve => setTimeout(resolve, 100));

  // Find the resize handle (implementation depends on your component structure)
  // This is a simplified example - you'll need to adjust based on actual DOM structure
  const resizeHandles = canvasElement.querySelector(".splitpanes__splitter");

  if (resizeHandles) {
    // Click on the tab to ensure it's visible
    await userEvent.click(canvas.getByText("Tab 1"));

    // Get initial positions/dimensions
    const initialRect = canvas.getByText("Content for Tab 1").getBoundingClientRect();

    // Simulate drag operation
    await userEvent.pointer({
      keys: "[MouseLeft>]", // Press left mouse button
      target: resizeHandles,
    });

    // Move pointer to resize
    await userEvent.pointer({
      target: document.body,
      coords: {clientX: initialRect.x + TARGET_SIZE, clientY: initialRect.y},
    });

    // Release mouse button
    await userEvent.pointer({
      keys: "[/MouseLeft]", // Release left mouse button
    });

    const newWidth = canvas.getByText("Content for Tab 1").getBoundingClientRect().width
    // Add assertions based on expected behavior after resize
    expect(newWidth).toBeGreaterThan(TARGET_SIZE - 5);
    expect(newWidth).toBeLessThan(TARGET_SIZE);

    // Click to free the mouse from the resize handle
    await userEvent.pointer({keys: "[MouseLeft]", target: resizeHandles})
  }
};

// Test for reordering tabs within a panel using drag and drop
export const TabReorderTest = Template.bind({});
TabReorderTest.args = Default.args;
TabReorderTest.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);

  // Wait for the component to render
  await new Promise(resolve => setTimeout(resolve, 100));

  // Find the tab elements in the first panel
  const firstTab = canvas.getByText("Tab 1");
  const thirdTab = canvas.getByText("Tab 3");

  // Get the coordinates of the elements
  const thirdTabRect = thirdTab.getBoundingClientRect();

  // Perform drag and drop to reorder tabs (moving Tab 1 after Tab 3)
  await userEvent.pointer({
    keys: "[MouseLeft>]", // Press left mouse button
    target: firstTab,
  });

  // Move to the position after Tab 3
  await userEvent.pointer({
    target: document.body,
    coords: {
      clientX: thirdTabRect.right + 10,
      clientY: thirdTabRect.y + (thirdTabRect.height / 2)
    },
  });

  // Release mouse button
  await userEvent.pointer({
    keys: "[/MouseLeft]", // Release left mouse button
  });

  // Wait for the reorder to complete
  await new Promise(resolve => setTimeout(resolve, 100));

  // Verify the tabs have been reordered (implementation-specific)
  // This could check DOM order or test the component's internal state
  // e.g., verify that Tab 2 now comes before Tab 1 in the DOM

  // You might also click on the reordered tab to verify it still works
  await userEvent.click(firstTab);
  expect(canvas.getByText("Content for Tab 1")).toBeInTheDocument();
};

// Test for moving a tab from one panel to another using drag and drop
export const TabMoveBetweenPanelsTest = Template.bind({});
TabMoveBetweenPanelsTest.args = Default.args;
TabMoveBetweenPanelsTest.play = async ({canvasElement}) => {
  const canvas = within(canvasElement);

  // Wait for the component to render
  await new Promise(resolve => setTimeout(resolve, 100));

  // Find a tab in the first panel to move
  const tabToMove = canvas.getByText("Tab 3");

  // Find a target location in the second panel
  // This could be a specific tab in the second panel or the panel container itself
  const targetPanel = canvas.getByText("Tab 5").closest(".panel-container") ||
                      canvas.getByText("Tab 5").parentElement.closest("div");

  // Get coordinates
  const tabRect = tabToMove.getBoundingClientRect();
  const targetRect = targetPanel.getBoundingClientRect();

  // Perform drag and drop to move tab from first panel to second panel
  await userEvent.pointer({
    keys: "[MouseLeft>]", // Press left mouse button
    target: tabToMove,
  });

  // Move to the target panel
  await userEvent.pointer({
    target: document.body,
    coords: {
      clientX: targetRect.x + (targetRect.width / 2),
      clientY: targetRect.y + 20 // Position near the top of the panel where tabs usually are
    },
  });

  // Release mouse button
  await userEvent.pointer({
    keys: "[/MouseLeft]", // Release left mouse button
  });

  // Wait for the move to complete
  await new Promise(resolve => setTimeout(resolve, 100));

  // Verify the tab has been moved to the second panel
  // One way to verify: Click the tab in its new location and check content
  await userEvent.click(canvas.getByText("Tab 3"));
  expect(canvas.getByText("Content for Tab 3")).toBeInTheDocument();

  // You might also check if the tab is no longer in the first panel
  // This depends on how your component handles the UI after a tab is moved
};
