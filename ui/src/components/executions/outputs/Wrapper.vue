<template>
    <div class="outputs">
        <KsSplitter :layout="isMobile ? 'vertical' : 'horizontal'">
            <KsSplitterPanel v-model:size="leftWidth" :min="'30%'" :max="'70%'" class="outputs-top">
                <div class="left-panel">
                    <KsCascaderPanel
                        v-if="tasksWithOutputs"
                        ref="cascader"
                        v-model="selected"
                        :props="cascaderProps"
                        :border="false"
                        class="cascader"
                        @expand-change="() => scrollRight()"
                    >
                        <template #default="{data}">
                            <div
                                v-if="data.heading"
                                @click="expandedValue = data.path"
                                class="cascader-heading"
                            >
                                <component :is="data.component" />
                                <span>{{ data.label }}</span>
                            </div>

                            <div
                                v-else
                                @click="expandedValue = data.path"
                                class="cascader-item"
                            >
                                <div class="task">
                                    <KsTaskIcon
                                        v-if="data.icon"
                                        :icons="pluginsStore.icons"
                                        :cls="icons[data.taskId]"
                                        onlyIcon
                                        class="output-task-icon"
                                    />
                                    <span :class="{'ms-3': data.icon}" class="task-label">
                                        <span>{{ data.label }}&nbsp;</span>
                                        <code v-if="data.iterationValue != null" class="task-iteration-value">
                                            {{ data.iterationValue }}
                                        </code>
                                    </span>
                                </div>
                                <code>
                                    <span
                                        :class="{
                                            regular: processedValue(data).regular,
                                        }"
                                    >
                                        {{ processedValue(data).label }}
                                    </span>
                                </code>
                            </div>
                        </template>
                    </KsCascaderPanel>
                </div>
            </KsSplitterPanel>
            <KsSplitterPanel>
                <div class="right-panel wrapper">
                    <div
                        v-if="multipleSelected || selectedValue"
                        class="content-container"
                    >
                        <div class="values">
                            <code class="d-block">
                                {{ selectedNode()?.label ?? "Value" }}
                            </code>
                        </div>

                        <KsCollapse
                            v-model="debugCollapse"
                            class="mb-3 debug bordered"
                        >
                            <KsCollapseItem name="debug">
                                <template #title>
                                    <span>{{ $t("eval.title") }}</span>
                                </template>

                                <div class="d-flex flex-column p-3 debug">
                                    <Editor
                                        ref="debugEditor"
                                        :fullHeight="false"
                                        :customHeight="20"
                                        :input="true"
                                        :navbar="false"
                                        :modelValue="computedDebugValue"
                                        @update:model-value="editorValue = $event"
                                        @confirm="onDebugExpression($event)"
                                        class="w-100"
                                    />

                                    <KsButton
                                        type="primary"
                                        @click="
                                            onDebugExpression(
                                                editorValue.length > 0 ? editorValue : computedDebugValue,
                                            )
                                        "
                                        class="mt-3 kel-button--wrap"
                                    >
                                        {{ $t("eval.title") }}
                                    </KsButton>

                                    <Editor
                                        v-if="debugExpression"
                                        :readOnly="true"
                                        :input="true"
                                        :showScroll="true"
                                        :fullHeight="false"
                                        :customHeight="20"
                                        :navbar="false"
                                        :modelValue="debugExpression"
                                        :lang="isJSON ? 'json' : ''"
                                        class="mt-3"
                                    />
                                </div>
                            </KsCollapseItem>
                        </KsCollapse>

                        <KsAlert
                            v-if="debugError"
                            type="error"
                            :closable="false"
                            class="overflow-auto"
                        >
                            <p>
                                <strong>{{ debugError }}</strong>
                            </p>
                            <div class="my-2">
                                <CopyToClipboard
                                    :text="`${debugError}\n\n${debugStackTrace}`"
                                    label="Copy Error"
                                    class="d-inline-block me-2"
                                />
                            </div>
                            <pre class="mb-0" style="overflow: scroll">{{
                                debugStackTrace
                            }}</pre>
                        </KsAlert>

                        <VarValue
                            v-if="displayVarValue()"
                            :value="typeof selectedValue === 'object' && selectedValue?.uri ? selectedValue?.uri : selectedValue"
                            :execution="execution"
                        />
                        <SubFlowLink
                            v-if="selectedNode().label === 'executionId'"
                            :executionId="selectedNode().value"
                        />
                    </div>
                </div>
            </KsSplitterPanel>
        </KsSplitter>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, shallowRef, onMounted, watch} from "vue"
    import {CascaderOption, CascaderProps}from "element-plus"
    import {useExecutionsStore} from "../../../stores/executions"
    import {usePluginsStore} from "../../../stores/plugins"

    import {useI18n} from "vue-i18n"
    import {apiUrl} from "override/utils/route"

    import {KsTaskIcon, KsSplitter, KsSplitterPanel, KsCascaderPanel, KsCollapse, KsCollapseItem, KsAlert, KsButton} from "@kestra-io/design-system"

    import CopyToClipboard from "../../layout/CopyToClipboard.vue"
    import Editor from "../../inputs/Editor.vue"
    import VarValue from "../VarValue.vue"
    import SubFlowLink from "../../flows/SubFlowLink.vue"
    import TimelineTextOutline from "vue-material-design-icons/TimelineTextOutline.vue"
    import TextBoxSearchOutline from "vue-material-design-icons/TextBoxSearchOutline.vue"
    import {useClient} from "@kestra-io/kestra-sdk"
    import {useMediaQuery} from "@vueuse/core"
    import * as Utils from "../../../utils/utils"

    const {t} = useI18n({useScope: "global"})

    const editorValue = ref<string>("")
    const debugCollapse = ref<string>("")
    const debugExpression = ref<string>("")

    function isValidVariable(path: string){
        return /^[a-zA-Z][a-zA-Z0-9_]*$/.test(path)
    }

    const formatTask = (tsk: string) => {
        return isValidVariable(tsk) ? `.${tsk}` : `["${tsk}"]`
    }

    const computedDebugValue = computed(() => {
        let task = selectedTask.value?.taskId
        if (!task) return ""

        let path = expandedValue.value
        if (!path) return `{{ outputs${formatTask(task)} }}`

        return `{{ outputs${path} }}`
    })

    const debugError = ref("")
    const debugStackTrace = ref("")
    const isJSON = ref(false)
    const selectedTask = computed(() => {
        const filter = selected.value?.length
            ? selected.value[0]
            : (cascader.value?.cascader?.getCheckedNodes(false)?.[0]?.label as string | undefined)
        const taskRunList = [...execution.value?.taskRunList ?? []]
        return taskRunList.find((e) => e.taskId === filter)
    })

    async function getTaskRunOutputs(id?: string, path?: string): Promise<TransformedTask[]> {
        if(!id || !execution.value?.id) {
            return []
        }
        const {data, status} = await axios.get(`${apiUrl()}/outputs/${execution.value.id}/${id}`, {
            validateStatus: (s) => s === 200 || s === 404,
        })
        if(status === 200) {
            return transform(data, true, path)
        } else {
            return []
        }

    }

    const cascaderProps: CascaderProps = {
        lazy: true,
        lazyLoad(node, resolve) {
            const {level} = node
            const data = node.data as TransformedTask
            if(level === 0) {
                resolve(outputs.value)
                return
            }

            if(level === 1) {
                getTaskRunOutputs(data.id, data.path).then((outputs) => {
                    resolve(outputs)
                })
                return
            }

            if(level > 1) {
                resolve(data.children || data.value ? [
                    {
                        label: data.value,
                        value: data.value,
                        leaf: true,
                    },
                ] : [])
                return
            }

            resolve([])
        },
    }

    const axios = useClient()
    const onDebugExpression = (expression?: string) => {
        const taskRun = selectedTask.value

        if (!taskRun) return

        const URL = `${apiUrl()}/executions/${taskRun?.executionId ?? execution.value?.id}/actions/eval/${taskRun.id}`
        axios
            .post(URL, expression, {headers: {"Content-type": "text/plain"}})
            .then((response) => {
                try {
                    const parsedResult = JSON.parse(response.data.result)
                    const debugOutput = JSON.stringify(parsedResult, null, 2)
                    debugExpression.value = debugOutput

                    if (response.status === 200 && debugOutput !== null && debugOutput !== undefined) {
                        selected.value.push(debugOutput)
                    }
                    isJSON.value = true
                } catch {
                    debugExpression.value = response.data.result

                    // Parsing failed, therefore, copy raw result
                    if (response.status === 200 && response.data.result !== null && response.data.result !== undefined)
                        selected.value.push(response.data.result)
                }

                debugError.value = response.data.error
                debugStackTrace.value = response.data.stackTrace
            })
    }

    const cascader = ref<InstanceType<typeof KsCascaderPanel> | null>(null)
    const scrollRight = () =>
        setTimeout(
            () =>
                ((cascader.value as any).$el.scrollLeft = (
                    cascader.value as any
                ).$el.offsetWidth),
            10,
        )
    const multipleSelected = computed(
        () => (cascader.value as any)?.menus?.length > 1,
    )

    const executionsStore = useExecutionsStore()

    const execution = computed(() => executionsStore.execution)

    function isValidURL(url: string) {
        try {
            URL.canParse(url)
            return true
        } catch {
            return false
        }
    }

    const processedValue = (data: TransformedTask) => {
        const regular = false

        if(!data.leaf || data.taskId) {
            return {label: "", regular}
        }

        // Check if the value is a valid URL and not an internal "kestra:///" link
        if (isValidURL(data.value)) {
            return data.value.startsWith("kestra:///")
                ? {label: "Internal link", regular}
                : {label: "External link", regular}
        }

        return {label: trim(data.value), regular: true}
    }

    const expandedValue = ref("")
    const selected = ref<(string | {uri: string})[]>([])

    onMounted(() => {
        debugCollapse.value = "debug"
    })

    const selectedValue = computed(() => {
        if (selected.value?.length)
            return selected.value[selected.value.length - 1]
        return undefined
    })

    watch(selectedValue, () => {
        debugError.value = ""
        debugStackTrace.value = ""
    })

    const selectedNode = () => {
        const node = cascader.value?.cascader?.getCheckedNodes(false)

        if (!node?.length) return {label: undefined, value: undefined}

        const {label, value} = node[0]

        return {label, value: value as string}
    }

    interface TransformedTask extends CascaderOption{
        component?: any;
        isFirstPass?: boolean;
        children?: TransformedTask[];
        path?: string;
        id?: string;
        value?: any;
    }

    const transform = (o: any, isFirstPass: boolean, path = "") => {
        const result: TransformedTask[] = Object.keys(o).map((key) => {
            const value = o[key]
            const isObject = typeof value === "object" && value !== null

            const keyStep = isValidVariable(key) ? `.${key}` : `["${key}"]`
            const currentPath = `${path}${keyStep}`

            // If the value is an array with exactly one element, use that element as the value
            if (Array.isArray(value) && value.length === 1) {
                return {
                    label: key,
                    value: value[0],
                    children: [],
                    leaf: true,
                    path: currentPath,
                }
            }

            return {
                label: key,
                value: isObject && !Array.isArray(value) ? key : value,
                children: isObject ? transform(value, false, currentPath) : [],
                leaf: !isObject,
                path: currentPath,
            }
        })

        if (isFirstPass) {
            const OUTPUTS: TransformedTask = {
                label: t("outputs"),
                heading: true,
                component: shallowRef(TextBoxSearchOutline),
                isFirstPass: true,
                path: path,
                leaf: true,
            }
            result.unshift(OUTPUTS)
        }

        return result
    }

    const tasksWithOutputs = ref<string[] | undefined>(undefined)

    watch(
        () => executionsStore.execution?.id,
        async (id) => {
            if(id) {
                const {data, status} = await axios.get(`${apiUrl()}/outputs/${id}`)
                if(status === 200 && data) {
                    tasksWithOutputs.value = []
                    for(const task of data){
                        if(task.taskRunId){
                            tasksWithOutputs.value?.push(task.taskRunId)
                        }
                    }
                }

            }
        },
        {immediate: true},
    )

    const outputs = computed<TransformedTask[] | undefined>(() => {
        const tasks = executionsStore?.execution?.taskRunList
            ?.filter((task) => tasksWithOutputs.value?.includes(task.id))
            ?.map((task) => {
                return {
                    label: task.taskId,
                    value: task.taskId,
                    ...task,
                    iterationValue: task.value, // For ForEach tasks, store the iteration value separately to display like Gantt view
                    icon: true,
                    leaf: false,
                    path: isValidVariable(task.taskId) ? `.${task.taskId}` : `["${task.taskId}"]`,
                }
            })

        if(!tasks?.length) {
            return undefined
        };

        const HEADING = {
            label: t("tasks"),
            heading: true,
            component: shallowRef(TimelineTextOutline),
            leaf: true,
        } as any

        tasks.unshift(HEADING)

        return tasks
    })

    watch(outputs, (o) => {
        if(o?.some(item => item.leaf === false)) {
            const task = o?.filter(item => item.leaf === false)[0]
            if (!task) return

            const selectedLocal = [task.value]
            let expandedValueLocal = task.path

            getTaskRunOutputs(task.id, task.path).then((children) => {
                let child: TransformedTask | undefined = children.filter(item => item.leaf === false)[0]

                do {
                    selectedLocal.push(child.value)
                    if(child?.path) {
                        expandedValueLocal = child.path
                    }

                    child = child.children?.filter(item => !item.heading)[0]
                } while(child?.path)

                selected.value = selectedLocal
                if(expandedValueLocal){
                    expandedValue.value = expandedValueLocal
                }
            })
        }
    })

    const pluginsStore = usePluginsStore()

    const icons = computed(() => {
        // TODO: https://github.com/kestra-io/kestra/issues/5643
        const getTaskIcons = (tasks: {
            id: string;
            type: string;
            tasks?: any[];
        }[], mapped: Record<string, string>) => {
            tasks.forEach((task) => {
                mapped[task.id] = task.type
                if (task.tasks && task.tasks.length > 0) {
                    getTaskIcons(task.tasks, mapped)
                }
            })
        }

        const mapped:Record<string, string> = {}

        getTaskIcons(executionsStore?.flow?.tasks || [], mapped)
        getTaskIcons(executionsStore?.flow?.errors || [], mapped)
        getTaskIcons(executionsStore?.flow?.finally || [], mapped)

        return mapped
    })

    const trim = (value: any) =>
        typeof value !== "string" || value.length < 16
            ? value
            : `${value.substring(0, 16)}...`

    const displayVarValue = () =>
        Utils.isFile(selectedValue.value) ||
        selectedValue.value !== debugExpression.value

    const leftWidth = ref("70%")
    const isMobile = useMediaQuery("(max-width: 768px)")
</script>

<style scoped lang="scss">
.outputs {
    display: flex;
    width: 100%;
    height: 100%;
    min-height: 0;
    overflow: hidden;
}

:deep(.kel-splitter) {
    height: 100%;
    min-height: 0;
}

:deep(.kel-splitter-panel) {
    display: flex;
    min-height: 0;
    overflow: hidden;
}

:deep(.kel-splitter-bar) {
    width: 3px !important;
    background-color: var(--ks-border-default);

    &:hover {
        background-color: var(--ks-border-focus);
    }
}

.cascader {
    flex-grow: 1;
}

.cascader-heading {
    pointer-events: none;
    display: flex;
    gap: .5rem;
    font-size: var(--ks-font-size-md);
}

.cascader-item {
    display: flex;
    justify-content: space-between;
    width: 100%;
}

:deep(.kel-scrollbar.kel-cascader-menu:nth-of-type(-n + 2) ul li:first-child),
.values {
    pointer-events: none;
    margin: 0.75rem 0 1.25rem 0;
    display: flex;
    justify-content: space-between;
    font-size: var(--ks-font-size-md);
    pointer-events: none;
}

:deep(.kel-cascader-menu__list) {
    /* Let the cascader list be constrained by its parent container
       so it can scroll independently instead of forcing page height */
    min-height: 0;
    height: 100%;
}

:deep(.kel-cascader-panel) {
    height: 100%;
}

.debug {
    background: var(--ks-bg-base);
}

.bordered {
    border: 1px solid var(--ks-border-default);
}

.bordered > :deep(.kel-collapse-item) {
    margin-bottom: 0px !important;
}

/* Left column container: take full splitter-panel height and scroll internally */
.outputs .left-panel {
    height: 100%;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
}

/* Right panel: make wrapper fill height and allow content to scroll independently */
.outputs .right-panel{
    background: var(--ks-bg-surface);
    position: relative;
    z-index: 1;
    width: 100%;
    height: 100%;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

:deep(.kel-cascader-menu) {
    min-width: 300px;
    max-width: 300px;

    &:last-child {
        border-right: 1px solid var(--ks-border-default);
    }

    .kel-cascader-menu__wrap {
        height: 100%;
    }

    & .kel-cascader-node {
        height: 36px;
        line-height: 36px;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);

        &[aria-haspopup="false"] {
            padding-right: 0.5rem !important;
        }

        &:hover {
            background-color: var(--ks-border-default);
        }

        &.in-active-path,
        &.is-active {
            background-color: var(--ks-border-default);
            font-weight: normal;
        }

        .kel-cascader-node__prefix {
            display: none;
        }

        .task {
            display: flex;
            padding-right: .25rem;
            width: 100%;
            max-width: 100%;

            & .task-label {
                width: 100%;
                max-width: 100%;

                & .task-iteration-value {
                    display: inline-block;
                    width: 80px;
                    max-width: 80px;
                    overflow-x: clip;
                    text-overflow: ellipsis;
                    color: var(--ks-text-primary);
                }
            }
        }

        .task .output-task-icon {
            align-self: center;
            height: var(--ks-font-size-sm);
            width: var(--ks-font-size-sm);
        }

        code span.regular {
            color: var(--ks-text-primary);
        }
    }
}
.content-container {
    flex: 1 1 0;
    min-height: 0;
    overflow-y: auto;
    overflow-x: hidden;
    scrollbar-gutter: stable;
    word-wrap: break-word;
    word-break: break-word;
    position: relative;
    z-index: 0;
    width: 100%;
    padding: 1rem;
}

:deep(.kel-collapse) {
    .kel-collapse-item__wrap {
        max-height: none !important;
    }

    .kel-collapse-item__content {
        word-wrap: break-word;
        word-break: break-word;
    }
}

:deep(.var-value) {
    word-wrap: break-word;
    word-break: break-word;
}

:deep(pre) {
    white-space: pre-wrap !important;
    word-wrap: break-word !important;
    word-break: break-word !important;
    overflow-wrap: break-word !important;
}

:deep(.monaco-editor),
:deep(.editor-container),
:deep(.complex-value-editor) {
    position: relative !important;
    z-index: auto !important;
}

//Mobile Version
@media (max-width: 768px) {
    :deep(.kel-splitter) {
        .outputs-top {
            margin: 10px;
            border: 2px solid var(--ks-border-default);
            box-sizing: border-box;
            overflow: auto;
            flex: 1 1 0 !important;
            min-height: 0 !important;
        }
    }
    :deep(.kel-splitter-bar){
        height: 4px !important;
        width: auto !important;

    }
}
</style>
