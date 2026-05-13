<template>
    <div class="task-drawer">
        <!-- Header -->
        <div class="drawer-header">
            <div class="drawer-header-title">
                {{ creatingTask ? t("no_code.workspace.new_task") : t("no_code.workspace.edit_task") }}
            </div>
            <KsIconButton
                :aria-label="t('close')"
                class="drawer-close-btn"
                @click="emit('close')"
            >
                <CloseIcon />
            </KsIconButton>
        </div>

        <!-- Body: three columns -->
        <div class="drawer-body">
            <!-- Left column: Inputs & Context -->
            <div class="drawer-col">
                <div class="drawer-col-head">
                    <div class="drawer-col-title">{{ t("no_code.workspace.drawer_inputs") }}</div>
                    <div class="drawer-col-sub">{{ t("no_code.workspace.drawer_inputs_hint") }}</div>
                </div>
                <div class="drawer-col-scroll">
                    <template v-if="flowInputs.length > 0">
                        <div class="drawer-section-label">{{ t("no_code.workspace.flow_inputs") }}</div>
                        <div
                            v-for="input in flowInputs"
                            :key="input.id"
                            class="context-card"
                            :title="`{{ inputs.${input.id} }}`"
                        >
                            <code class="context-card-expr">{{ input.id }}</code>
                            <KsTag v-if="input.type" size="small" disableTransitions class="context-card-type">
                                {{ input.type }}
                            </KsTag>
                        </div>
                    </template>

                    <div class="drawer-section-label" :class="{'mt-2': flowInputs.length > 0}">
                        {{ t("no_code.workspace.execution_context") }}
                    </div>
                    <div
                        v-for="ctxVar in EXECUTION_CONTEXT_VARS"
                        :key="ctxVar.expr"
                        class="context-card"
                        :title="ctxVar.expr"
                    >
                        <code class="context-card-expr">{{ ctxVar.label }}</code>
                        <KsTag size="small" disableTransitions class="context-card-type">{{ ctxVar.type }}</KsTag>
                    </div>
                </div>
            </div>

            <!-- Center column: Properties -->
            <div class="drawer-col">
                <div class="drawer-col-head">
                    <div class="drawer-col-title">{{ t("no_code.workspace.drawer_properties") }}</div>
                </div>
                <div class="drawer-col-scroll">
                    <Task />
                </div>
            </div>

            <!-- Right column: Outputs -->
            <div class="drawer-col">
                <div class="drawer-col-head">
                    <div class="drawer-col-title">{{ t("no_code.workspace.drawer_outputs") }}</div>
                    <div class="drawer-col-sub">{{ t("no_code.workspace.drawer_outputs_hint") }}</div>
                </div>
                <div class="drawer-col-scroll">
                    <template v-if="currentTaskOutputs.length > 0">
                        <div class="drawer-section-label">{{ t("no_code.workspace.schema_outputs") }}</div>
                        <div
                            v-for="output in currentTaskOutputs"
                            :key="output.name"
                            class="output-card"
                        >
                            <div class="output-card-name">{{ output.name }}</div>
                            <div v-if="output.type" class="output-card-type">{{ output.type }}</div>
                        </div>
                    </template>
                    <KsEmpty v-else />
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/design-system"
    import CloseIcon from "vue-material-design-icons/Close.vue"
    import Task from "../segments/Task.vue"
    import {FULL_SOURCE_INJECTION_KEY} from "../injectionKeys"
    import {usePluginsStore} from "../../../stores/plugins"

    const {t} = useI18n()

    const props = defineProps<{
        creatingTask?: boolean;
    }>()

    const emit = defineEmits<{
        (e: "close"): void;
    }>()

    const flowSource = inject(FULL_SOURCE_INJECTION_KEY, ref(""))
    const pluginsStore = usePluginsStore()

    const parsedFlow = computed(() => {
        try {
            return YAML_UTILS.parse(flowSource.value) ?? {}
        } catch {
            return {}
        }
    })

    const flowInputs = computed<Array<{id: string; type?: string}>>(() => {
        const inputs = parsedFlow.value?.inputs
        if (!Array.isArray(inputs)) return []
        return inputs.filter(Boolean).map((i: any) => ({id: i.id ?? "", type: i.type}))
    })

    const EXECUTION_CONTEXT_VARS = [
        {label: "execution.id", expr: "{{ execution.id }}", type: "String"},
        {label: "execution.startDate", expr: "{{ execution.startDate }}", type: "DateTime"},
        {label: "flow.id", expr: "{{ flow.id }}", type: "String"},
        {label: "flow.namespace", expr: "{{ flow.namespace }}", type: "String"},
        {label: "trigger.date", expr: "{{ trigger.date }}", type: "DateTime"},
    ]

    const currentTaskOutputs = computed<Array<{name: string; type?: string}>>(() => {
        const outputs = pluginsStore.pluginAllProps?.outputs?.properties
        if (!outputs || typeof outputs !== "object") return []
        return Object.entries(outputs).map(([name, schema]: [string, any]) => ({
            name,
            type: schema?.type ?? schema?.$ref?.split("/").pop(),
        }))
    })
</script>

<style scoped lang="scss">
.task-drawer {
    width: 860px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--ks-background-card);
    border-left: 1px solid var(--ks-border-primary);
    flex-shrink: 0;
}

.drawer-header {
    display: flex;
    align-items: center;
    gap: 0.625rem;
    padding: 0 1.25rem;
    height: 50px;
    flex-shrink: 0;
    border-bottom: 1px solid var(--ks-border-primary);
    background: var(--ks-background-card);
}

.drawer-header-title {
    font-size: 0.9375rem;
    font-weight: 600;
    color: var(--ks-content-primary);
    flex: 1;
}

.drawer-close-btn {
    margin-left: auto;
}

.drawer-body {
    display: grid;
    grid-template-columns: 260px 1fr 260px;
    flex: 1;
    overflow: hidden;
    min-height: 0;
}

.drawer-col {
    display: flex;
    flex-direction: column;
    overflow: hidden;
    border-right: 1px solid var(--ks-border-primary);

    &:last-child {
        border-right: none;
    }
}

.drawer-col-head {
    padding: 0.75rem 1.125rem 0.5rem;
    border-bottom: 1px solid var(--ks-border-secondary);
    flex-shrink: 0;
}

.drawer-col-title {
    font-size: 0.8125rem;
    font-weight: 600;
    color: var(--ks-content-primary);
    margin-bottom: 2px;
}

.drawer-col-sub {
    font-size: 0.6875rem;
    color: var(--ks-content-secondary);
}

.drawer-col-scroll {
    flex: 1;
    overflow-y: auto;
    padding: 0.875rem 1.125rem;
}

.drawer-section-label {
    font-size: 0.625rem;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.07em;
    color: var(--ks-content-secondary);
    margin-bottom: 0.5rem;

    &.mt-2 {
        margin-top: 1rem;
    }
}

.context-card {
    display: flex;
    align-items: center;
    gap: 0.3rem;
    background: var(--ks-background-default);
    border: 1px solid var(--ks-border-secondary);
    border-radius: 6px;
    padding: 0.3rem 0.5rem;
    margin-bottom: 0.25rem;
    cursor: default;
    transition: border-color 0.1s;

    &:hover {
        border-color: var(--ks-border-focus);
    }
}

.context-card-expr {
    flex: 1;
    min-width: 0;
    font-size: 0.65625rem;
    color: var(--ks-content-link);
    font-family: var(--ks-font-monospace, ui-monospace, monospace);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.context-card-type {
    flex-shrink: 0;
}

.output-card {
    background: var(--ks-background-default);
    border: 1px solid var(--ks-border-secondary);
    border-radius: 7px;
    padding: 0.5625rem 0.6875rem;
    margin-bottom: 0.375rem;
}

.output-card-name {
    font-size: 0.75rem;
    font-weight: 500;
    color: var(--ks-content-primary);
    font-family: var(--ks-font-monospace, ui-monospace, monospace);
}

.output-card-type {
    font-size: 0.625rem;
    color: var(--ks-content-secondary);
    margin-top: 1px;
}
</style>
