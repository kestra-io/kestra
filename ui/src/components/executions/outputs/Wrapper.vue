<template>
    <el-row class="flex-grow-1 outputs">
        <el-col :xs="24" :sm="24" :md="16" :lg="18" :xl="18" class="d-flex flex-column">
            <el-cascader-panel
                ref="cascader"
                v-model="selected"
                :options="outputs"
                :border="false"
                class="flex-grow-1 overflow-x-auto cascader"
                @expand-change="() => scrollRight()"
            >
                <template #default="{node, data}">
                    <div v-if="data.heading" class="pe-none d-flex fs-5">
                        <component :is="data.component" class="me-2" />
                        <span>{{ data.label }}</span>
                    </div>

                    <div v-else class="w-100 d-flex justify-content-between">
                        <div class="pe-5 d-flex task">
                            <TaskIcon v-if="data.icon" :icons="allIcons" :cls="icons[data.taskId]" only-icon />
                            <span :class="{'ms-3': data.icon}">{{ data.label }}</span>
                        </div>
                        <code>
                            <span v-if="node.isLeaf" class="regular">{{ trim(data.value) }}</span>                            
                            <span v-else>{{ data.children.length }} items</span>                            
                        </code>
                    </div>
                </template>
            </el-cascader-panel>
        </el-col>
        <el-col :xs="24" :sm="24" :md="8" :lg="6" :xl="6" class="d-flex p-3 wrapper">
            <div class="overflow-auto">
                Outputs: {{ selected && selected[selected.length - 1] }}
            </div>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import {ref, computed, shallowRef} from "vue";

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import TextBoxSearchOutline from "vue-material-design-icons/TextBoxSearchOutline.vue";

    const cascader = ref<HTMLElement | { $el: HTMLElement; } | null>(null);
    const scrollRight = () => setTimeout(() => (cascader.value as any).$el.scrollLeft = (cascader.value as any).$el.offsetWidth, 10);

    const selected = ref([]);
    const transform = (o, isFirstPass = true) => {
        const result = Object.keys(o).map(key => {
            const value = o[key];
            const isObject = typeof value === "object" && value !== null;

            // If the value is an array with exactly one element, use that element as the value
            if (Array.isArray(value) && value.length === 1) {
                return {label: key, value: value[0], children: []};
            }

            return {label: key, value: isObject && !Array.isArray(value) ? null : value, children: isObject ? transform(value, false) : []};
        });

        if (isFirstPass) {
            const OUTPUTS = {label: t("outputs"), heading: true, component: shallowRef(TextBoxSearchOutline)};
            result.unshift(OUTPUTS);
        }

        return result;
    };
    const outputs = computed(() => {
        const tasks = store.state.execution.execution.taskRunList.map((task) => {
            return {label: task.taskId, value: task.taskId, ...task, icon: true, children: transform(task.outputs)};
        });

        const HEADING = {label: t("tasks"), heading: true, component: shallowRef(TimelineTextOutline)};
        tasks.unshift(HEADING);

        return tasks;
    });

    const allIcons = computed(() => store.state.plugin.icons);
    const icons = computed(() => {
        const mapped = {};

        store.state.execution.flow?.tasks?.map((task) => mapped[task.id] = task.type);

        return mapped;
    });

    const trim = (value) => typeof value === "string" ? `${value.substring(0, 16)}...` : value;
</script>

<style lang="scss">
.outputs {
    .cascader {
        &::-webkit-scrollbar {
            height: 5px;
        }

        &::-webkit-scrollbar-track {
            background: var(--card-bg);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--bs-primary);
            border-radius: 0px;
        }
    }

    .wrapper {
        background: var(--card-bg);
    }
}

.el-cascader-menu {
    min-width: 300px;

    &:last-child {
        border-right: 1px solid var(--bs-border-color);
    }

    .el-cascader-menu__wrap {
        height: 100%;
    }

    & .el-cascader-node {
        height: 36px;
        line-height: 36px;
        font-size: var(--el-font-size-small);
        color: var(--el-text-color-regular);

        &:hover {
            background-color: var(--bs-border-color);
        }

        &.in-active-path,
        &.is-active {
            background-color: var(--bs-border-color);
            font-weight: normal;
        }

        .el-cascader-node__prefix {
            display: none;
        }

        .task .wrapper {
            align-self: center;
            height: var(--el-font-size-small);
            width: var(--el-font-size-small);
        }

        code span.regular {
            color: var(--el-text-color-regular);
        }
    }
}


.el-scrollbar.el-cascader-menu:nth-of-type(-n+2) {
    ul li:first-child {
        pointer-events: none;
        margin: 0.75rem 0 1.25rem 0;
    }
}
</style>