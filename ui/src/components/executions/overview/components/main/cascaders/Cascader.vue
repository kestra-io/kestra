<template>
    <div :id="cascaderID">
        <div class="header">
            <el-text truncated>
                {{ props.title }}
            </el-text>
            <el-input
                v-if="props.elements"
                v-model="filter"
                :placeholder="$t('search')"
                :suffixIcon="Magnify"
            />
        </div>

        <template v-if="props.elements">
            <el-cascader-panel
                :options="filteredOptions"
                @expand-change="onExpandChange"
            >
                <template #default="{data}">
                    <div v-if="data.loadMore" class="load-more">
                        <el-button text size="small" @click.stop="loadMore(data.path, $event)">
                            {{ data.label }}
                        </el-button>
                    </div>

                    <div v-else-if="data.tooLarge" class="node too-large">
                        <el-alert type="warning" :closable="false" showIcon>
                            {{ $t('large_outputs.value_too_large', {size: data.size}) }}
                        </el-alert>
                        <el-button
                            type="primary"
                            size="small"
                            :icon="Download"
                            @click.stop="downloadValue(data)"
                        >
                            {{ $t('large_outputs.download_json') }}
                        </el-button>
                    </div>

                    <template v-else>
                        <div class="node">
                            <div :title="data.label">
                                {{ data.label }}
                            </div>
                            <div v-if="data.value && data.children">
                                <code>{{ itemsCount(data) }}</code>
                            </div>
                        </div>
                        <div v-if="isFile(data.value)" class="node buttons">
                            <VarValue :value="data.value" :execution />
                        </div>
                    </template>
                </template>
            </el-cascader-panel>
        </template>

        <span v-else class="empty">{{ props.empty }}</span>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, nextTick, computed, ref} from "vue";

    import VarValue from "../../../../VarValue.vue";

    import {Execution} from "../../../../../../stores/executions";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Download from "vue-material-design-icons/Download.vue";

    import Utils from "../../../../../../utils/utils";
    import {downloadJson, isTooLargeToRender} from "../../../../largeValues";
    import {useCascaderPaging} from "../../../../outputs/cascaderPaging";
    import {PAGE_SIZE, limitFor} from "../../../../outputs/transformOutputs";

    export interface Node {
        label: string;
        value: string;
        children?: Node[];
        path?: string;
        /** Real number of keys under this node, which `children` may only partly contain. */
        total?: number;
        loadMore?: boolean;
        disabled?: boolean;
        tooLarge?: boolean;
        size?: string;
    }

    type DebugTypes = "outputs" | "trigger";

    export type Element = {
        title: string;
        empty: string;
        elements?: Record<string, any>;
        includeDebug?: DebugTypes | undefined;
    }

    const props = defineProps<
        Element & {
            execution: Execution;
        }
    >();

    const emits = defineEmits<{
        (e: "debugPath", property: string, path: string): void;
    }>();

    const path = ref<string>("");

    const onExpandChange = (p: string[]) => {
        path.value = p.join(".");
        if (props.includeDebug) {
            let debugPath = path.value;
            if (props.includeDebug === "trigger") {
                // id and type are metadata, not Pebble-accessible — map to just "trigger"
                if (debugPath === "id" || debugPath === "type") {
                    debugPath = "";
                }
                // variables.<name> maps to trigger.<name> in Pebble
                else if (debugPath.startsWith("variables.")) {
                    debugPath = debugPath.substring("variables.".length);
                } else if (debugPath === "variables") {
                    debugPath = "";
                }
            }
            emits("debugPath", props.includeDebug, debugPath);
        }
    };

    const isFile = (value: unknown): value is string => {
        return typeof value === "string" && (value.startsWith("kestra:///") || value.startsWith("file://") || value.startsWith("nsfile://"));
    };

    const {limits, loadMore} = useCascaderPaging();

    // A value past the budget is offered as a download: its own text node wedges the column.
    const leaf = (value: any, path: string): Node => {
        const text = typeof value === "string" ? value : String(value ?? "");

        if (isTooLargeToRender(text)) {
            return {label: "", value: text, path, tooLarge: true, size: Utils.humanFileSize(text.length)};
        }

        return {label: value, value, path};
    };

    // Levels are paged: one column of thousands of nodes froze the tab on mount.
    const format = (obj: Record<string, any>, path = ""): Node[] => {
        const entries = Object.entries(obj);
        const limit = limitFor(limits.value, path);

        const nodes = entries.slice(0, limit).map(([k, v]) => {
            const isObject = typeof v === "object" && v !== null;
            const currentPath = path ? `${path}.${k}` : k;

            const children = isObject ? format(v, currentPath) : [leaf(v, currentPath)];
            // An oversized leaf carries no label, so keep it on its own merit or it drops out
            // of the column and the value becomes unreachable.
            const filteredChildren = children.filter((c) => c.tooLarge || (c.label ?? c.value));

            const node: Node = {label: k, value: k, path: currentPath};

            if (isObject) node.total = Object.keys(v).length;
            if (filteredChildren.length) node.children = filteredChildren;

            return node;
        });

        const remaining = entries.length - limit;

        if (remaining > 0) {
            nodes.push({
                label: t("large_outputs.load_more", {count: Math.min(remaining, PAGE_SIZE), remaining}),
                value: `${path}::more`,
                path,
                loadMore: true,
                disabled: true,
            });
        }

        return nodes;
    };

    const formatted = computed<Node[]>(() => (props.elements ? format(props.elements) : []));

    const downloadValue = (node: Node) =>
        downloadJson(node.value, `output-${props.execution?.id || "value"}.json`);

    const filter = ref("");
    const filteredOptions = computed(() => {
        if (filter.value === "") return formatted.value;

        const lowercase = filter.value.toLowerCase();
        return formatted.value.filter((node) => {
            const matchesNode = node.label.toLowerCase().includes(lowercase);

            if (!node.children) return matchesNode;

            const matchesChildren = node.children.some((c) =>
                c.label.toLowerCase().includes(lowercase),
            );

            return matchesNode || matchesChildren;
        });
    });

    const itemsCount = (item: Node) => {
        const length = item.total ?? item.children?.length ?? 0;

        if (!length) return undefined;

        return `${length} ${length === 1 ? t("item") : t("items")}`;
    };

    const cascaderID = `cascader-${props.title.toLowerCase().replace(/\s+/g, "-")}`;
    onMounted(async () => {
        await nextTick(() => {
            // Open first node by default on page mount
            const selector = `#${cascaderID} .el-cascader-node`;
            const nodes = document.querySelectorAll(selector);

            if (nodes.length > 0) (nodes[0] as HTMLElement).click();
        });
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

[id^="cascader-"] {
    overflow: hidden;

    .header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding-bottom: $spacer;

        > .el-text {
            width: 100%;
            display: flex;
            align-items: center;
            font-size: $font-size-xl;
        }

        > .el-input {
            display: flex;
            align-items: center;
            width: calc($spacer * 16);
        }
    }

    .el-cascader-panel {
        overflow: auto;
        width: 100%;
    }

    .empty {
        font-size: $font-size-sm;
        color: var(--ks-content-secondary);
    }

    :deep(.el-cascader-menu) {
        min-width: 300px;
        max-width: 300px;

        &:last-child {
            max-width: none;
        }

        .el-cascader-menu__list {
            padding: 0;
        }

        .el-cascader-menu__wrap {
            height: 100%;
        }

        .load-more {
            width: 100%;
            display: flex;
            justify-content: center;
            pointer-events: auto;
        }

        .node.too-large {
            flex-direction: column;
            align-items: flex-start;
            gap: calc($spacer / 2);
            padding: calc($spacer / 2) 0;
        }

        .node {
            width: 100%;
            display: flex;
            justify-content: space-between;

            &.buttons {
                margin: 0.75rem 0;
            }

            & > div {
                overflow-x: auto;
            }
        }

        & .el-cascader-node {
            height: min-content;
            line-height: 36px;
            font-size: $font-size-sm;
            color: var(--ks-content-primary);
            padding: 0 30px 0 5px;

            &[aria-haspopup="false"] {
                padding-right: 0.5rem !important;
            }

            &:hover {
                background-color: var(--ks-border-primary);
            }

            &.in-active-path,
            &.is-active {
                background-color: var(--ks-border-primary);
                font-weight: normal;
            }

            .el-cascader-node__prefix {
                display: none;
            }

            code span.regular {
                color: var(--ks-content-primary);
            }
        }
    }
}
</style>
