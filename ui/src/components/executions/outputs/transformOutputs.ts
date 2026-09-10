export interface OutputNode {
    label: string;
    value?: any;
    children: OutputNode[];
    path: string;
    /** Real number of keys under this node, which `children` may only partly contain. */
    total?: number;
    /** The trailing row that reveals the next page instead of selecting a value. */
    loadMore?: boolean;
    disabled?: boolean;
}

// el-cascader-panel builds a Node per option and renders a whole column at once, so a task
// emitting thousands of values wedged the tab. Levels are paged instead.
export const PAGE_SIZE = 200;

/** How many children of `path` to build, given the pages revealed so far. */
export function limitFor(limits: Record<string, number>, path: string): number {
    return limits[path] ?? PAGE_SIZE;
}

/**
 * Turns a task's outputs into cascader options, one node per key, paged per level.
 * `moreLabel` renders the trailing row when a level has more keys than `limits` reveals.
 */
export function buildChildren(
    o: Record<string, any>,
    path: string,
    limits: Record<string, number>,
    moreLabel: (remaining: number) => string,
): OutputNode[] {
    const keys = Object.keys(o);
    const limit = limitFor(limits, path);

    const result: OutputNode[] = keys.slice(0, limit).map((key) => {
        const value = o[key];
        const isObject = typeof value === "object" && value !== null;

        const currentPath = `${path}["${key}"]`;

        // If the value is an array with exactly one element, use that element as the value
        if (Array.isArray(value) && value.length === 1) {
            return {
                label: key,
                value: value[0],
                children: [],
                path: currentPath,
            };
        }

        return {
            label: key,
            value: isObject && !Array.isArray(value) ? key : value,
            children: isObject ? buildChildren(value, currentPath, limits, moreLabel) : [],
            total: isObject ? Object.keys(value).length : undefined,
            path: currentPath,
        };
    });

    if (keys.length > limit) {
        result.push({
            label: moreLabel(keys.length - limit),
            value: `${path}::more`,
            loadMore: true,
            disabled: true,
            children: [],
            path,
        });
    }

    return result;
}
