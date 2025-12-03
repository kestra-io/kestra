import Revisions from "../../../src/components/layout/Revisions.vue";
import {ComponentPropsAndSlots, StoryObj} from "@storybook/vue3-vite";
import {expect, fn, waitFor} from "storybook/test";
import {vueRouter} from "storybook-vue3-router";
import {nextTick} from "vue";


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

async function selectorOptions(canvasElement: HTMLElement) {
    const revisionSelectors = [...canvasElement.querySelectorAll(".revision-select .el-select__wrapper")] as HTMLElement[];
    const revisionSelectorsOptions: HTMLElement[][] = [];
    for (const selector of revisionSelectors) {
        selector.click();
        await waitFor(() => selector.ariaDescribedByElements !== null);
        revisionSelectorsOptions.push(
            (selector.ariaDescribedByElements?? []).flatMap(selectorDropdown => [...selectorDropdown.querySelectorAll("[role='option']")] as HTMLElement[])
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

const revisions: {revision: number, source?: string}[] = [
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

        await expect(simplifiedOptions[0]).toEqual([{selected: false, content: "1"}, {selected: true, content: "3"}]);
        await expect(simplifiedOptions[1]).toEqual([{selected: false, content: "1"}, {selected: true, content: "4 (current)"}]);

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
            const confirmButton = document.querySelector("[role='dialog'][aria-label='Confirmation'] button.el-button--primary") as HTMLElement;
            await expect(confirmButton).not.toBeNull();
            confirmButton.click();
        });
        await waitFor(() => expect(revisions[revisions.length - 1].revision).toEqual(5));
        await expect(revisions[revisions.length - 1].source).toContain('"revision": 1');
        await expect(revisionSourceMock).not.toHaveBeenCalledWith(5);
    }
};
