import Revisions from "./Revisions.vue";
import {ComponentPropsAndSlots, StoryObj} from "@storybook/vue3-vite";
import {expect, fn, spyOn, userEvent, waitFor, within} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {nextTick, ref} from "vue";
import {useFlowStore} from "../../stores/flow";


export default {
    title: "Layout/Revisions",
    component: Revisions,
    decorators: [
        vueRouter([
            {path: "/", name: "home", component: {template: "<div />"}}
        ], {initialRoute: "/"}),
    ]
};

type Story = StoryObj<typeof Revisions>;

function crudText(revision: number) {
    return `CRUD for revision ${revision}`;
}

const render: Story["render"] = (args: ComponentPropsAndSlots<typeof Revisions>) => ({
    components: {Revisions},
    setup() {
        return {args, crudText};
    },
    template: `<Revisions v-bind="{...args}">
        <template #crud="{revision}">
            <div>{{crudText(revision)}}</div>
        </template>
    </Revisions>`
});

const deletionRender: Story["render"] = (args: ComponentPropsAndSlots<typeof Revisions>) => ({
    components: {Revisions},
    setup() {
        const revisionsList = ref([...(args.revisions ?? [])]);
        function onDeleted(revision: number) {
            revisionsList.value = revisionsList.value.filter(r => r.revision !== revision);
        }
        return {args, revisionsList, onDeleted, crudText};
    },
    template: `<Revisions v-bind="{...args}" :revisions="revisionsList" @deleted="onDeleted">
        <template #crud="{revision}">
            <div>{{crudText(revision)}}</div>
        </template>
    </Revisions>`
});

async function selectorOptions(canvasElement: HTMLElement) {
    const revisionSelectors = [...canvasElement.querySelectorAll(".revision-select .kel-select__wrapper")] as HTMLElement[];
    const revisionSelectorsOptions: HTMLElement[][] = [];
    for (const selector of revisionSelectors) {
        selector.click();
        await waitFor(() => selector.ariaDescribedByElements !== null);
        revisionSelectorsOptions.push(
            (selector.ariaDescribedByElements ?? []).flatMap(selectorDropdown => [...selectorDropdown.querySelectorAll("[role='option']")] as HTMLElement[])
        );
    }
    return revisionSelectorsOptions;
}

function getSimplifiedOptions(revisionSelectorsOptions: HTMLElement[][]) {
    return revisionSelectorsOptions.map(options =>
        options.map(option => ({
            selected: option.ariaSelected === "true",
            content: option.textContent
        }))
    );
}

const revisions: { revision: number, source?: string }[] = [
    {
        revision: 1,
    },
    {
        revision: 3,
    },
    {
        revision: 4,
    }
];
const revisionSourceMock = fn((revision: number) => {
    return Promise.resolve(
        `{"revision": ${revision}, "content": "Content for revision ${revision}"}`
    );
});
export const Default: Story = {
    render: render.bind({}),
    args: {
        lang: "json",
        revisions,
        revisionSource: revisionSourceMock,
        onRestore: (source: string) => {
            revisions.push({revision: revisions[revisions.length - 1].revision + 1, source});
            return Promise.resolve();
        }
    },
    async play({args, canvas, canvasElement}) {
        await expect(
            ([...canvasElement.querySelectorAll(".editor .monaco-editor")] as HTMLElement[])
                .every(el => el.getAttribute("aria-uri")?.endsWith(`.${args.lang}`))
        ).toBeTruthy();

        let revisionSelectorsOptions = await selectorOptions(canvasElement);

        await expect(revisionSelectorsOptions.length).toEqual(2);

        let simplifiedOptions = getSimplifiedOptions(revisionSelectorsOptions);

        await expect(simplifiedOptions[0]).toEqual([{selected: false, content: "Revision 1"}, {selected: true, content: "Revision 3"}]);
        await expect(simplifiedOptions[1]).toEqual([{selected: false, content: "Revision 1"}, {selected: true, content: "Revision 4 (current)"}]);

        await expect(canvas.getByText(crudText(3))).not.toBeNull();
        await expect(canvas.getByText(crudText(4))).not.toBeNull();

        await expect(revisionSourceMock).not.toHaveBeenCalledWith(1);
        revisionSelectorsOptions[1][0].click(); // Select revision 1 for the second selector
        await nextTick();
        await expect(revisionSourceMock).toHaveBeenCalledWith(1);

        await waitFor(async () => {
            revisionSelectorsOptions = await selectorOptions(canvasElement)
            simplifiedOptions = getSimplifiedOptions(revisionSelectorsOptions);
            await expect(simplifiedOptions[1][0].selected).toBeTruthy();
        });

        const htmlElement = await canvas.findByTestId("restore-right");
        htmlElement.click();
        await waitFor(async () => {
            const confirmButton = document.querySelector("[role='dialog'][aria-label='Confirmation'] button.kel-button--primary") as HTMLElement;
            await expect(confirmButton).not.toBeNull();
            confirmButton.click();
        });
        await waitFor(() => expect(revisions[revisions.length - 1].revision).toEqual(5));
        await expect(revisions[revisions.length - 1].source).toContain("\"revision\": 1");
        await expect(revisionSourceMock).not.toHaveBeenCalledWith(5);
    }
};

export const DeleteSelectedRevision: Story = {
    render: deletionRender,
    args: {
        lang: "json",
        revisions: [{revision: 1}, {revision: 3}, {revision: 4}],
        revisionSource: fn((revision: number) =>
            Promise.resolve(`{"revision": ${revision}, "content": "Content for revision ${revision}"}`)
        ),
        canDelete: true,
        editRouteQuery: false,
    },
    async play({canvasElement}) {
        const flowStore = useFlowStore();
        spyOn(flowStore, "deleteRevision").mockResolvedValue(undefined);

        await waitFor(() =>
            expect(canvasElement.querySelectorAll(".revision-grid-col").length).toBe(2)
        );

        const [leftOptions] = await selectorOptions(canvasElement);

        const rev3Option = leftOptions.find(opt => opt.textContent?.includes("3"));
        expect(rev3Option).toBeDefined();
        const trashIcon = (rev3Option!.querySelector("span[role='img']") ?? rev3Option!.querySelector("svg")) as Element;
        expect(trashIcon).not.toBeNull();
        (trashIcon as HTMLElement).click();

        await waitFor(() => {
            const confirmBtn = document.querySelector(
                "[role='dialog'][aria-label='Confirmation'] button.kel-button--primary"
            ) as HTMLElement;
            expect(confirmBtn).not.toBeNull();
            confirmBtn.click();
        });

        await waitFor(() =>
            expect(canvasElement.querySelector(".revision-grid-col")).not.toBeNull()
        );
    }
};

let delayedSource: Promise<string>;
let finishSource: (source: string) => void;

export const LatestSelectionWins: Story = {
    loaders: [async () => {
        await import("@kestra-io/design-system/components/Form/KsEditor.vue");
        return {};
    }],
    render: () => ({
        components: {Revisions},
        setup() {
            delayedSource = new Promise(resolve => {finishSource = resolve});
            const revisionsList = [
                {revision: 1, source: "FIRST_REVISION"},
                {revision: 3},
                {revision: 4, source: "CURRENT_REVISION"},
            ];
            return {revisionsList, loadSource: () => delayedSource};
        },
        template: '<div style="height: 100vh"><Revisions lang="text" :revisions="revisionsList" :revisionSource="loadSource" :editRouteQuery="false" /></div>',
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        const left = within(canvasElement.querySelector('[data-test="revision-left"]') as HTMLElement);
        await userEvent.click(left.getByRole("combobox"));
        await userEvent.click(within(document.body).getByRole("option", {name: "Revision 1"}));
        await waitFor(() => expect(canvas.getByText("FIRST_REVISION")).toBeVisible(), {timeout: 10000});
        await expect(canvas.getByText("CURRENT_REVISION")).toBeVisible();
        finishSource("OBSOLETE_REVISION");
        await delayedSource;
        await nextTick();
        await nextTick();
        await expect(canvas.queryByText("OBSOLETE_REVISION")).not.toBeInTheDocument();
        await expect(canvas.getByText("FIRST_REVISION")).toBeVisible();
    },
};

export const RefreshHistoricalComparison: Story = {
    loaders: LatestSelectionWins.loaders,
    render: () => ({
        components: {Revisions},
        setup() {
            const revisionsList = ref([
                {revision: 1, source: "FIRST_REVISION"},
                {revision: 3, source: "HISTORICAL_REVISION"},
                {revision: 4, source: "CURRENT_REVISION"},
            ]);
            function refresh() {
                revisionsList.value = [
                    {revision: 1, source: "REFRESHED_FIRST_REVISION"},
                    {revision: 3, source: "REFRESHED_HISTORICAL_REVISION"},
                    {revision: 4, source: "CURRENT_REVISION"},
                    {revision: 9, source: "NEW_CURRENT_REVISION"},
                ];
            }
            return {revisionsList, refresh, loadSource: () => Promise.resolve(undefined)};
        },
        template: '<div style="height: 100vh"><button @click="refresh">Refresh revisions</button><Revisions lang="text" :revisions="revisionsList" :revisionSource="loadSource" :editRouteQuery="false" /></div>',
    }),
    async play({canvasElement}) {
        const canvas = within(canvasElement);
        const right = within(canvasElement.querySelector('[data-test="revision-right"]') as HTMLElement);
        await userEvent.click(right.getByRole("combobox"));
        await userEvent.click(within(document.body).getByRole("option", {name: "Revision 1"}));
        await waitFor(() => expect(canvas.getByText("FIRST_REVISION")).toBeVisible(), {timeout: 10000});
        await userEvent.click(canvas.getByRole("button", {name: "Refresh revisions"}));
        await waitFor(() => expect(canvas.getByText("REFRESHED_FIRST_REVISION")).toBeVisible());
        await expect(canvas.getByText("REFRESHED_HISTORICAL_REVISION")).toBeVisible();
        await expect(canvas.queryByText("NEW_CURRENT_REVISION")).not.toBeInTheDocument();
        await expect(canvas.getByText("Revision 1", {exact: true})).toBeVisible();
        await expect(canvas.getByText("Revision 3", {exact: true})).toBeVisible();
    },
};
