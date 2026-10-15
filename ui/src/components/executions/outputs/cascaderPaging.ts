import {nextTick, ref} from "vue";
import {PAGE_SIZE, limitFor} from "./transformOutputs";

/** Per-level page counters and the reveal handler shared by both cascader panels. */
export function useCascaderPaging() {
    const limits = ref<Record<string, number>>({});

    const loadMore = async (path: string, event: MouseEvent) => {
        const wrap = (event.currentTarget as HTMLElement).closest<HTMLElement>(".el-cascader-menu__wrap");
        const panel = wrap?.closest(".el-cascader-panel");
        const column = wrap && panel
            ? [...panel.querySelectorAll(".el-cascader-menu__wrap")].indexOf(wrap)
            : -1;
        const scrollTop = wrap?.scrollTop ?? 0;

        limits.value = {
            ...limits.value,
            [path]: limitFor(limits.value, path) + PAGE_SIZE,
        };

        if (!panel || column < 0) {
            return;
        }

        // The panel rebuilds the column and scrolls its active node into view, so the reader's
        // position has to be put back after that has run, not just after the re-render.
        const restore = () => {
            const target = panel.querySelectorAll<HTMLElement>(".el-cascader-menu__wrap")[column];
            if (target) {
                target.scrollTop = scrollTop;
            }
        };

        await nextTick();
        restore();
        requestAnimationFrame(restore);
    };

    return {limits, loadMore};
}
