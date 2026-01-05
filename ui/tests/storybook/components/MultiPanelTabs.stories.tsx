import {markRaw, ref, StyleValue} from "vue";
import {within, userEvent, expect, fireEvent, waitFor} from "storybook/test";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import CodeTagsIcon from "vue-material-design-icons/CodeTags.vue";
import MouseRightClickIcon from "vue-material-design-icons/MouseRightClick.vue";
import FileTreeOutlineIcon from "vue-material-design-icons/FileTreeOutline.vue";
import FileDocumentIcon from "vue-material-design-icons/FileDocument.vue";
import FolderOpenOutline from "vue-material-design-icons/FolderOpenOutline.vue";
import ShapePlusOutline from "vue-material-design-icons/ShapePlusOutline.vue";

import MultiPanelTabs from "../../../src/components/MultiPanelTabs.vue";
import {Panel} from "../../../src/utils/multiPanelTypes";

const meta: Meta<typeof MultiPanelTabs> = {
    title: "Components/MultiPanelTabs",
    component: MultiPanelTabs,
}

export default meta

type Story = StoryObj<typeof MultiPanelTabs>;

const render: Story["render"] = ({modelValue}) => ({
    setup() {
        const modelValueRef = ref();
        modelValueRef.value = modelValue;

        const labelStyle: StyleValue = {
            position: "absolute",
            top: 0,
            left: "0",
            color: "white",
            fontSize: "12px",
            textAlign: "right",
            padding: "0 1rem"
        };

        return () => <div style="padding: 1rem;border: 1px solid var(--ks-border-primary); border-radius: 4px; margin: 1rem; background: var(--ks-background-body)">
            <div style={{...labelStyle, background: "red", width: "250px"}}>This is an example of 250px wide element.</div>
            <div style={{...labelStyle, background: "blue", width: "800px", top: "20px"}}>This is an example of 800px wide element.</div>
            <MultiPanelTabs modelValue={modelValueRef.value} />
            <pre>{JSON.stringify(modelValueRef.value.map((p:any) => ({
                tabs:p.tabs.map((t:any) => t.value),
                size: p.size ? Math.round(p.size) : "<undefined>",
            })))}</pre>
        </div>
    }
})


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

const PlaceholderComponent = (props: {tabId:string}) => <div style={{
    padding: "1rem",
    height: "50vh",
    background: BG_COLORS[parseInt(props.tabId)]
}}>Content for Tab {props.tabId}</div>


const argGenerator = (index?: number) => {
    const values =  {
        modelValue: [
            {
                activeTab: {
                    button: {icon: markRaw(CodeTagsIcon), label: "Tab 1"},
                    uid: "tab1",
                    component: () => <PlaceholderComponent tabId="1" />,
                },
                size: 1,
                tabs: [
                    {
                        button: {icon: markRaw(CodeTagsIcon), label: "Tab 1"},
                        uid: "tab1",
                        component: () => <PlaceholderComponent tabId="1" />,
                    },
                    {
                        button: {icon: markRaw(MouseRightClickIcon), label: "Tab 2"},
                        uid: "tab2",
                        component: () => <PlaceholderComponent tabId="2" />,
                    },
                    {
                        button: {icon: markRaw(FileTreeOutlineIcon), label: "Tab 3"},
                        uid: "tab3",
                        component: () => <PlaceholderComponent tabId="3" />,
                    },
                ],
            },
            {
                activeTab: {
                    button: {icon: markRaw(FileDocumentIcon), label: "Tab 4"},
                    uid: "tab4",
                    component: () => <PlaceholderComponent tabId="4" />,
                },
                size: 1,
                tabs: [

                    {
                        button: {icon: markRaw(FileDocumentIcon), label: "Tab 4"},
                        uid: "tab4",
                        component: () => <PlaceholderComponent tabId="4" />,
                    },
                    {
                        button: {icon: markRaw(FolderOpenOutline), label: "Tab 5"},
                        uid: "tab5",
                        component: () => <PlaceholderComponent tabId="5" />,
                    },
                    {
                        button: {icon: markRaw(ShapePlusOutline), label: "Tab 6"},
                        uid: "tab6",
                        component: () => <PlaceholderComponent tabId="6" />,
                    },
                ],
            },
        ] satisfies Panel[],
    }

    return index === undefined ? values : {modelValue:[values.modelValue[index]]}
}
const args = argGenerator()

export const Default: Story = {
    render: render.bind({}),
    args,
}

// Add interaction test story
export const TabInteractionTest: Story = {
    render: render.bind({}),
    args,
    play: async ({canvasElement}) => {
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
    }
}

const TARGET_SIZE = 320

// Add test for panel resize functionality
export const PanelResizeTest: Story = {
    render: render.bind({}),
    args,
    play: async ({canvasElement}) => {
        const canvas = within(canvasElement);

        // Wait for the component to render
        await new Promise(resolve => setTimeout(resolve, 100));

        // Find the resize handle
        const resizeHandle = canvasElement.querySelector(".el-splitter__splitter");

        if (resizeHandle) {
            // Click on the tab to ensure it's visible
            await userEvent.click(canvas.getByText("Tab 1"));

            // Get initial positions/dimensions
            const initialRect = canvas.getByText("Content for Tab 1").getBoundingClientRect();

            // Simulate drag operation
            await userEvent.pointer({
                keys: "[MouseLeft>]", // Press left mouse button
                target: resizeHandle,
            });

            // Move pointer to resize
            await userEvent.pointer({
                coords: {
                    clientX: TARGET_SIZE - initialRect.width,
                },
            });

            // Release mouse button
            await userEvent.pointer({
                keys: "[/MouseLeft]", // Release left mouse button
            });

            const newWidth = canvas.getByText("Content for Tab 1").getBoundingClientRect()?.width
            // Add assertions based on expected behavior after resize
            expect(newWidth).toBeGreaterThan(TARGET_SIZE - 10);
            expect(newWidth).toBeLessThan(TARGET_SIZE + 10);

            // Click to free the mouse from the resize handle
            await userEvent.pointer({
                keys: "[MouseLeft]",
                target: resizeHandle
            });
        }
    }
}

// Test for reordering tabs within a panel using drag and drop
export const TabReorderTest: Story = {
    render: render.bind({}),
    args: argGenerator(0),
    play: async ({mount}) => {
        const canvas = await mount(render(argGenerator(0), {} as any));

        const dropBetweenTabs = async () => {
            // Find the tab elements in the first panel
            const firstTab = canvas.getByText("Tab 1");
            const tabList = canvas.getByRole("tablist");

            // Perform drag operation
            await fireEvent.dragStart(firstTab);

            await fireEvent.dragOver(tabList, {clientX: 800});

            // Perform drop operation at the calculated position
            await fireEvent.drop(canvas.getAllByText("Tab 3")[0]);

            // Wait for the reorder to complete
            await new Promise(resolve => setTimeout(resolve, 100));

            // Verify the tabs have been reordered
            await userEvent.click(firstTab);
            expect(canvas.getAllByRole("tab").map(tab => tab.querySelector(".tab-title")?.textContent?.trim())).toMatchObject(["Tab 2", "Tab 3", "Tab 1"]);
        }

        const dropBetweenTwoTabs = async () => {
            // Find the tab elements in the first panel
            const firstTab = canvas.getByText("Tab 2");
            const tabList = canvas.getByRole("tablist");

            // Perform drag operation
            await fireEvent.dragStart(firstTab);

            await fireEvent.dragOver(tabList, {clientX: 250});

            // Perform drop operation at the calculated position
            await fireEvent.drop(canvas.getAllByText("Tab 1")[0]);

            // Wait for the reorder to complete
            await new Promise(resolve => setTimeout(resolve, 100));

            // Verify the tabs have been reordered
            await userEvent.click(firstTab);
            expect(canvas.getAllByRole("tab").map(tab => tab.querySelector(".tab-title")?.textContent?.trim())).toMatchObject(["Tab 3", "Tab 2", "Tab 1"]);
        }

        const dragEnterOnPanelDropOnPanel = async () => {
            // Find the tab elements in the first panel
            const secondTab = canvas.getByText("Tab 2");
            const panelOverlay = canvas.getByText("Content for Tab 2").parentNode!;

            // Perform drag operation
            await fireEvent.dragStart(secondTab);

            await fireEvent.dragOver(panelOverlay, {clientX: 800});

            // Perform drop operation at the calculated position
            await fireEvent.drop(panelOverlay);

            // Wait for the reorder to complete
            await new Promise(resolve => setTimeout(resolve, 100));

            expect(canvas.getAllByRole("tab").map(tab => tab.querySelector(".tab-title")?.textContent?.trim())).toMatchObject(["Tab 3", "Tab 1", "Tab 2"]);
        }

        await waitFor(dropBetweenTabs);

        await new Promise(resolve => setTimeout(resolve, 100));
        await waitFor(dropBetweenTwoTabs);

        await new Promise(resolve => setTimeout(resolve, 100));
        await waitFor(dragEnterOnPanelDropOnPanel);
    }
};

// Test for moving a tab from one panel to another using drag and drop
export const TabMoveBetweenPanelsTest: Story = {
    render: render.bind({}),
    args,
    play: async ({mount}) => {
        const canvas = await mount(render(argGenerator(), {} as any));

        // Wait for the component to render
        await new Promise(resolve => setTimeout(resolve, 100));

        async function dragOnTabsList () {
            const secondTab = canvas.getByText("Tab 2");
            const panel = canvas.getAllByRole("tablist")[1];

            const br = panel.getBoundingClientRect();

            // Perform drag operation
            await fireEvent.dragStart(secondTab);

            await fireEvent.dragOver(panel, {clientX: br.right - 10});

            // Perform drop operation at the calculated position
            await fireEvent.drop(panel);

            // Wait for the reorder to complete
            await new Promise(resolve => setTimeout(resolve, 100));

            // Verify the tabs have been reordered
            expect(
                within(canvas.getAllByRole("tablist")[1]).getAllByRole("tab")
                    .map(tab => tab.querySelector(".tab-title")?.textContent?.trim())
            ).toMatchObject(["Tab 4", "Tab 5", "Tab 6", "Tab 2"]);
        }

        async function dragOnContentPanel() {
            const secondTab = canvas.getByText("Tab 1");

            userEvent.click(canvas.getByText("Tab 4"));
            const panelOverlay = canvas.getByText("Content for Tab 4").parentNode as HTMLElement;

            const br = panelOverlay.getBoundingClientRect();

            // Perform drag operation
            fireEvent.dragStart(secondTab);

            fireEvent.dragOver(panelOverlay, {clientX: br.left + 10});

            // Perform drop operation at the calculated position
            fireEvent.drop(panelOverlay);

            // Wait for the reorder to complete
            await new Promise(resolve => setTimeout(resolve, 100));

            // Verify the tabs have been reordered
            expect(
                within(canvas.getAllByRole("tablist")[1]).getAllByRole("tab")
                    .map(tab => tab.querySelector(".tab-title")?.textContent?.trim())
            ).toMatchObject(["Tab 1", "Tab 4", "Tab 5", "Tab 6", "Tab 2"]);

            // Verify that the original active tab is now changed
            expect(canvas.getByText("Content for Tab 3")).toBeInTheDocument();
        }



        await waitFor(dragOnTabsList);
        await waitFor(dragOnContentPanel);
    }
};

// Test for reordering tabs within a panel using drag and drop
export const SplitPanel: Story = {
    render: render.bind({}),
    args: argGenerator(0),
    play: async ({mount}) => {
        const canvas = await mount(render(argGenerator(0), {} as any));

        expect(canvas.getAllByRole("tablist")).toHaveLength(1)

        await new Promise(resolve => setTimeout(resolve, 100));

        await userEvent.click(canvas.getByTitle("Split panel"))

        await new Promise(resolve => setTimeout(resolve, 100));

        expect(canvas.getAllByRole("tablist")).toHaveLength(2)
    }
}
