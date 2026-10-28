import {computed, provide, ref} from "vue";
import type {Meta, StoryObj} from "@storybook/vue3-vite";
import {within, expect, waitFor} from "storybook/test";
import GanttRow from "./GanttRow.vue";
import {GANTT_TREE, type Ancestor, type GanttTree} from "./ganttTree";

const ROOT: Ancestor = {key: "tr:root", label: "root"};
const CHILD: Ancestor = {key: "tr:child", label: "child"};

const ROWS = [
    {key: ROOT.key, label: ROOT.label, ancestors: [] as Ancestor[]},
    {key: CHILD.key, label: CHILD.label, ancestors: [ROOT]},
    {key: "tr:grandchild", label: "grandchild", ancestors: [ROOT, CHILD]},
];

const GUIDE_TOLERANCE_PX = 1;

const rect = (element: Element) => element.getBoundingClientRect();
const centreX = (box: DOMRect) => box.left + box.width / 2;

function guideLineCentreX(guide: Element) {
    const line = getComputedStyle(guide, "::before");
    return rect(guide).left + parseFloat(line.left) + parseFloat(line.width) / 2;
}

const meta = {
    title: "Components/Executions/Gantt/GanttRow",
    component: GanttRow,
    parameters: {layout: "padded"},
    decorators: [
        () => ({
            setup() {
                provide(GANTT_TREE, {
                    compact: ref(false),
                    highlightedKey: computed(() => ROOT.key),
                } as unknown as GanttTree);
            },
            template: "<div style='width: 60rem'><story /></div>",
        }),
    ],
    render: () => ({
        components: {GanttRow},
        setup: () => ({rows: ROWS}),
        template: `
            <div role="grid">
                <GanttRow
                    v-for="row in rows"
                    :key="row.key"
                    :nodeKey="row.key"
                    :label="row.label"
                    :ancestors="row.ancestors"
                    offset="0s"
                    :barStyle="{}"
                    state="SUCCESS"
                    :selected="false"
                    :expandable="row.key !== 'tr:grandchild'"
                    expanded
                >
                    <template #label><code>{{ row.label }}</code></template>
                </GanttRow>
            </div>
        `,
    }),
} satisfies Meta<typeof GanttRow>;

export default meta;
type Story = StoryObj<typeof meta>;

export const GuidesAlignWithParentChevrons: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getAllByRole("row")).toHaveLength(ROWS.length));

        const rowElement = (key: string) => canvasElement.querySelector(`[data-row-key="${key}"]`) as HTMLElement;
        const chevronOf = (key: string) => rowElement(key).querySelector("[data-test='gantt-row-chevron']") as HTMLElement;

        for (const row of ROWS) {
            const guides = Array.from(rowElement(row.key).querySelectorAll(".guide"));
            expect(guides).toHaveLength(row.ancestors.length);

            guides.forEach((guide, level) => {
                const parentChevronX = centreX(rect(chevronOf(row.ancestors[level].key)));
                expect(Math.abs(guideLineCentreX(guide) - parentChevronX)).toBeLessThanOrEqual(GUIDE_TOLERANCE_PX);
            });
        }

        expect(rowElement(CHILD.key).querySelector(".guide.is-lineage")).not.toBeNull();
        expect(rowElement("tr:grandchild").querySelector(".guide:not(.is-lineage)")).not.toBeNull();
    },
};

export const ChevronsKeepTheirIndent: Story = {
    play: async ({canvasElement}: {canvasElement: HTMLElement}) => {
        const canvas = within(canvasElement);
        await waitFor(() => expect(canvas.getAllByRole("row")).toHaveLength(ROWS.length));

        for (const row of ROWS.filter(({key}) => key !== "tr:grandchild")) {
            const rowElement = canvasElement.querySelector(`[data-row-key="${row.key}"]`) as HTMLElement;
            const label = rowElement.querySelector(".row-label") as HTMLElement;
            const chevron = rowElement.querySelector("[data-test='gantt-row-chevron']") as HTMLElement;
            const indent = rect(chevron).width;
            const gap = parseFloat(getComputedStyle(label).columnGap);

            expect(rect(chevron).left - rect(label).left).toBeCloseTo(row.ancestors.length * indent + gap, 1);
        }
    },
};
