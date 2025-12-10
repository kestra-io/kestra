<template>
    <div :id="`cascader-${props.title}`">
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

        <template v-if="props.includeDebug">
            <el-splitter
                v-if="props.elements"
                :layout="verticalLayout ? 'vertical' : 'horizontal'"
                lazy
            >
                <el-splitter-panel :size="verticalLayout ? '50%' : '70%'">
                    <CascaderPanel :options="filteredOptions" class="debug" />
                </el-splitter-panel>
                <el-splitter-panel>
                    <DebugPanel :property="props.includeDebug" :execution />
                </el-splitter-panel>
            </el-splitter>

            <span v-else class="empty">{{ props.empty }}</span>
        </template>

        <template v-else>
            <CascaderPanel v-if="props.elements" :options="filteredOptions" />
            <span v-else class="empty">{{ props.empty }}</span>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, computed, ref} from "vue";

    import CascaderPanel from "./CascaderPanel.vue";
    import DebugPanel from "./DebugPanel.vue";

    import {Execution} from "../../../../../../stores/executions";

    import {verticalLayout} from "../../../utils/layout";

    import Magnify from "vue-material-design-icons/Magnify.vue";

    export interface Node {
        label: string;
        value: string;
        children?: Node[];
    }

    const props = defineProps<{
        title: string;
        empty: string;
        elements?: Record<string, any>;
        includeDebug?: "outputs" | "trigger";
        execution: Execution;
    }>();

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

    onMounted(() => {
        if (props.elements) formatted.value = format(props.elements);
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
            max-height: calc($spacer * 20);
            border-top-right-radius: 0;
            border-bottom-right-radius: 0;
        }
    }

    .empty {
        font-size: $font-size-sm;
        color: var(--ks-content-secondary);
    }

    :deep(.el-cascader-menu) {
        height: -webkit-fill-available;
        max-height: calc($spacer * 20);
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
