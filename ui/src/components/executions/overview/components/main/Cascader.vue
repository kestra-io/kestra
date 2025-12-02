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

        <el-cascader-panel
            v-if="props.elements"
            ref="cascader"
            :options="filteredOptions"
        >
            <template #default="{data}">
                <VarValue
                    v-if="isFile(data.value)"
                    :value="data.value"
                    :execution="props.execution"
                    class="node"
                />
                <div v-else class="node">
                    <div :title="data.label">
                        {{ data.label }}
                    </div>
                    <div v-if="data.value && data.children">
                        <code>
                            {{ data.children.length }}
                            {{
                                $t(
                                    data.children.length === 1
                                        ? "item"
                                        : "items",
                                )
                            }}
                        </code>
                    </div>
                </div>
            </template>
        </el-cascader-panel>

        <span v-else class="empty">{{ props.empty }}</span>
    </div>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue";

    import VarValue from "../../../VarValue.vue";

    import {Execution} from "src/stores/executions";

    import Magnify from "vue-material-design-icons/Magnify.vue";

    const props = defineProps<{
        title: string;
        empty: string;
        elements?: Record<string, any>;
        execution: Execution;
    }>();

    const isFile = (data: any) => {
        if (typeof data !== "string") return false;

        const prefixes = ["kestra:///", "file://", "nsfile://"];
        return prefixes.some((prefix) => data.startsWith(prefix));
    };

    interface Node {
        label: string;
        value: string;
        children?: Node[];
    }

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

    const cascader = ref<any>(null);
    onMounted(() => {
        if (props.elements) formatted.value = format(props.elements);

        // Open first node by default on page mount
        if (cascader?.value) {
            const nodes = cascader.value.$el.querySelectorAll(".el-cascader-node");
            if (nodes.length > 0) (nodes[0] as HTMLElement).click();
        }
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
