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
            <el-splitter
                v-if="props.includeDebug"
                :layout="verticalLayout ? 'vertical' : 'horizontal'"
                lazy
            >
                <el-splitter-panel :size="verticalLayout ? '50%' : '70%'">
                    <el-cascader-panel
                        :options="filteredOptions"
                        @expand-change="(p: string[]) => (path = p.join('.'))"
                        class="debug"
                    >
                        <template #default="{data}">
                            <div class="node">
                                <div :title="data.label">
                                    {{ data.label }}
                                </div>
                                <div v-if="data.value && data.children">
                                    <code>{{ itemsCount(data) }}</code>
                                </div>
                            </div>
                        </template>
                    </el-cascader-panel>
                </el-splitter-panel>
                <el-splitter-panel>
                    <DebugPanel
                        :property="props.includeDebug"
                        :execution
                        :path
                    />
                </el-splitter-panel>
            </el-splitter>

            <el-cascader-panel v-else :options="filteredOptions">
                <template #default="{data}">
                    <div class="node">
                        <div :title="data.label">
                            {{ data.label }}
                        </div>
                        <div v-if="data.value && data.children">
                            <code>{{ itemsCount(data) }}</code>
                        </div>
                    </div>
                </template>
            </el-cascader-panel>
        </template>

        <span v-else class="empty">{{ props.empty }}</span>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, nextTick, computed, ref} from "vue";

    import DebugPanel from "./DebugPanel.vue";

    import {Execution} from "../../../../../../stores/executions";

    import {verticalLayout} from "../../../utils/layout";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import Magnify from "vue-material-design-icons/Magnify.vue";

    export interface Node {
        label: string;
        value: string;
        children?: Node[];
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

    const path = ref<string>("");

    const formatted = ref<Node[]>([]);
    const format = (obj: Record<string, any>): Node[] => {
        return Object.entries(obj).map(([k, v]) => {
            const isObject = typeof v === "object" && v !== null;

            const children = isObject
                ? Object.entries(v).map(([ck, cv]) => format({[ck]: cv})[0])
                : [{label: v, value: v}];

            const filteredChildren = children.filter((c) => c.label ?? c.value);

            const node: Node = {label: k, value: k};

            if (filteredChildren.length) node.children = filteredChildren;

            return node;
        });
    };

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
        const length = item.children?.length ?? 0;

        if (!length) return undefined;

        return `${length} ${length === 1 ? t("item") : t("items")}`;
    };

    const cascaderID = `cascader-${props.title.toLowerCase().replace(/\s+/g, "-")}`;
    onMounted(async () => {
        if (props.elements) formatted.value = format(props.elements);

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

        &.debug {
            min-height: -webkit-fill-available;
            border-top-right-radius: 0;
            border-bottom-right-radius: 0;
        }
    }

    .empty {
        font-size: $font-size-sm;
        color: var(--ks-content-secondary);
    }

    :deep(.el-cascader-menu) {
        min-width: 300px;
        max-width: 300px;

        .el-cascader-menu__list {
            padding: 0;
        }

        .el-cascader-menu__wrap {
            height: 100%;
        }

        .node {
            width: 100%;
            display: flex;
            justify-content: space-between;
        }

        & .el-cascader-node {
            height: 36px;
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
