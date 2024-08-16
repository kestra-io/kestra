<template>
    <el-row class="flex-grow-1 outputs">
        <el-col
            :xs="24"
            :sm="24"
            :md="multipleSelected || selectedValue ? 16 : 24"
            :lg="multipleSelected || selectedValue ? 16 : 24"
            :xl="multipleSelected || selectedValue ? 18 : 24"
            class="d-flex flex-column"
        >
            <el-cascader-panel
                ref="cascader"
                v-model="selected"
                :options="outputs"
                :border="false"
                class="flex-grow-1 overflow-x-auto cascader"
                @expand-change="() => scrollRight()"
            >
                <template #default="{data}">
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
                            <span :class="{regular: processedValue(data).regular}">
                                {{ processedValue(data).label }}
                            </span>
                        </code>
                    </div>
                </template>
            </el-cascader-panel>
        </el-col>
        <el-col
            v-if="multipleSelected || selectedValue"
            :xs="24"
            :sm="24"
            :md="8"
            :lg="8"
            :xl="6"
            class="d-flex p-3 wrapper"
        >
            <div class="w-100 overflow-auto">
                <div class="d-flex justify-content-between pe-none fs-5 values">
                    <code class="d-block">
                        {{ selectedNode()?.label ?? 'Value' }}
                    </code>
                </div>

                <el-collapse class="mb-3 debug bordered">
                    <el-collapse-item>
                        <template #title>
                            <span>{{ t('eval.title') }}</span>
                        </template>

                        <div class="d-flex flex-column p-3 debug">
                            <editor
                                ref="debugEditor"
                                :full-height="false"
                                :input="true"
                                :navbar="false"
                                :model-value="computedDebugValue"
                                @confirm="onDebugExpression($event)"
                                class="w-100"
                            />

                            <el-button
                                type="primary"
                                @click="onDebugExpression(debugEditor.editor.getValue())"
                                class="mt-3"
                            >
                                {{ t('eval.title') }}
                            </el-button>

                            <editor
                                v-if="debugExpression"
                                :read-only="true"
                                :input="true"
                                :full-height="false"
                                :navbar="false"
                                :minimap="false"
                                :model-value="debugExpression"
                                :lang="isJSON ? 'json' : ''"
                                class="mt-3"
                            />
                        </div>
                    </el-collapse-item>
                </el-collapse>

                <el-alert v-if="debugError" type="error" :closable="false" class="overflow-auto">
                    <p><strong>{{ debugError }}</strong></p>
                    <div class="my-2">
                        <CopyToClipboard :text="debugError" label="Copy Error" class="d-inline-block me-2" />
                        <CopyToClipboard :text="debugStackTrace" label="Copy Stack Trace" class="d-inline-block" />
                    </div>
                    <pre class="mb-0" style="overflow: scroll;">{{ debugStackTrace }}</pre>
                </el-alert>

                <VarValue :value="selectedValue" :execution="execution" />
                <SubFlowLink v-if="selectedNode().label === 'executionId'" :execution-id="selectedNode().value" />
            </div>
        </el-col>
    </el-row>
</template>

<script setup lang="ts">
    import {ref, computed, shallowRef} from "vue";
    import {ElTree} from "element-plus";

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {apiUrl} from "override/utils/route";

    import CopyToClipboard from "../../layout/CopyToClipboard.vue"

    import Editor from "../../inputs/Editor.vue";
    const debugEditor = ref(null);
    const debugExpression = ref("");
    const computedDebugValue = computed(() => `{{ outputs${selectedTask()?.taskId ? `.${selectedTask().taskId}` : ""} }}`);
    const debugError = ref("");
    const debugStackTrace = ref("");
    const isJSON = ref(false);
    const selectedTask = () => {
        const filter = selected.value.length ? selected.value[0] : (cascader.value as any).menuList?.[0]?.panel?.expandingNode?.label;
        const taskRunList = [...execution.value.taskRunList];
        return taskRunList.find(e => e.taskId === filter);
    };
    const onDebugExpression = (expression) => {
        const taskRun = selectedTask();

        if(!taskRun) return

        const URL = `${apiUrl(store)}/executions/${taskRun?.executionId}/eval/${taskRun.id}`;
        store.$http
            .post(URL, expression, {headers: {"Content-type": "text/plain",}})
            .then(response => {
                try {
                    debugExpression.value = JSON.stringify(JSON.parse(response.data.result), "  ", 2);
                    isJSON.value = true;
                } catch (e) {
                    debugExpression.value = response.data.result;
                }

                debugError.value = response.data.error;
                debugStackTrace.value = response.data.stackTrace;
            });
    };

    import VarValue from "../VarValue.vue";
    import SubFlowLink from "../../flows/SubFlowLink.vue";

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue";
    import TextBoxSearchOutline from "vue-material-design-icons/TextBoxSearchOutline.vue";

    const cascader = ref<InstanceType<typeof ElTree> | null>(null);
    const scrollRight = () => setTimeout(() => (cascader.value as any).$el.scrollLeft = (cascader.value as any).$el.offsetWidth, 10);
    const multipleSelected = computed(() => (cascader.value as any)?.menus?.length > 1);

    const execution = computed(() => store.state.execution.execution);

    function isValidURL(url) {
        try {
            new URL(url);
            return true;
        } catch (e) {
            return false;
        }
    }

    const processedValue = (data) => {
        const regular = false;

        if (!data.value && !data.children?.length) {
            return {label: data.value, regular};
        } else if (data?.children?.length) {
            const message = (length) => ({label: `${length} items`, regular});
            const length = data.children.length;

            return data.children[0].isFirstPass ? message(length - 1) : message(length);
        }

        // Check if the value is a valid URL and not an internal "kestra:///" link
        if (isValidURL(data.value)) {
            return data.value.startsWith("kestra:///") 
                ? {label: "Internal link", regular} 
                : {label: "External link", regular};
        }

        return {label: trim(data.value), regular: true};
    };

    const selected = ref([]);
    const selectedValue = computed(() => {
        if (selected.value.length) return selected.value[selected.value.length - 1];
        return undefined;
    });
    const selectedNode = () => {
        const node = cascader.value?.getCheckedNodes();

        if (!node?.length) return {label: undefined, value: undefined};

        const {label, value} = node[0];

        return {label, value};
    };

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
            const OUTPUTS = {label: t("outputs"), heading: true, component: shallowRef(TextBoxSearchOutline), isFirstPass: true};
            result.unshift(OUTPUTS);
        }

        return result;
    };
    const outputs = computed(() => {
        const tasks = store.state.execution.execution.taskRunList.map((task) => {
            return {label: task.taskId, value: task.taskId, ...task, icon: true, children: task?.outputs ? transform(task.outputs) : []};
        });

        const HEADING = {label: t("tasks"), heading: true, component: shallowRef(TimelineTextOutline)};
        tasks.unshift(HEADING);

        return tasks;
    });

    const allIcons = computed(() => store.state.plugin.icons);
    const icons = computed(() => {
        const getTaskIcons = (tasks, mapped) => {
            tasks.forEach(task => {
                mapped[task.id] = task.type;
                if (task.tasks && task.tasks.length > 0) {
                    getTaskIcons(task.tasks, mapped);
                }
            });
        };

        const mapped = {};

        getTaskIcons(store.state.execution?.flow?.tasks || [], mapped);

        return mapped;
    });

    const trim = (value) => (typeof value !== "string" || value.length < 16) ? value : `${value.substring(0, 16)}...`;
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
    max-width: 300px;

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

        &[aria-haspopup="false"] {
            padding-right: 0.5rem !important;
        }

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


.el-scrollbar.el-cascader-menu:nth-of-type(-n+2) ul li:first-child,
.values {
    pointer-events: none;
    margin: 0.75rem 0 1.25rem 0;
}

.debug {
    background: var(--bs-gray-100);
}

.bordered {
    border: 1px solid var(--bs-border-color)
}
</style>