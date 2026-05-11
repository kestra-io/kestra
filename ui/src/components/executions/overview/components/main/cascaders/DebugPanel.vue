<template>
    <div id="debug">
        <Editor
            v-model="expression"
            :shouldFocus="false"
            :navbar="false"
            input
            lang="yaml-pebble"
            class="expression"
        />

        <div class="buttons">
            <KsButton type="primary" :icon="Refresh" @click="onRender">
                {{ $t("eval.render") }}
            </KsButton>
            <KsButton
                :disabled="!result && !error"
                :icon="CloseCircleOutline"
                @click="clearAll"
            />
        </div>

        <template v-if="result">
            <VarValue v-if="Utils.isFile(result.value)" :value="result.value" :execution />

            <Editor
                v-else
                v-model="result.value"
                :shouldFocus="false"
                :navbar="false"
                input
                readOnly
                :lang="result.type"
                class="result"
            />
        </template>

        <KsAlert
            v-else-if="error"
            type="error"
            :title="error"
            showIcon
            :closable="false"
        >
            <pre v-if="stackTrace" class="mb-0 stack-trace">{{ stackTrace }}</pre>
        </KsAlert>
    </div>
</template>

<script setup lang="ts">
    import {watch, ref} from "vue"

    import Editor from "../../../../../inputs/Editor.vue"
    import VarValue from "../../../../VarValue.vue"

    import {Execution} from "../../../../../../stores/executions"
    import {useFlowStore} from "../../../../../../stores/flow"

    import Refresh from "vue-material-design-icons/Refresh.vue"
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"

    import * as Utils from "../../../../../../utils/utils"
    import {apiUrl} from "override/utils/route"
    import {useClient} from "@kestra-io/kestra-sdk"

    const flowStore = useFlowStore()

    const props = defineProps<{
        property?: "outputs" | "trigger";
        execution: Execution;
        path?: string;
    }>()

    // Fetch the flow source and populate flowStore.flowYaml so pebble
    // autocompletion works identically to the flow editor.
    watch(
        () => [props.execution?.namespace, props.execution?.flowId, props.execution?.flowRevision],
        async ([namespace, flowId, revision]) => {
            if (namespace && flowId && !flowStore.flowYaml) {
                try {
                    const flow = await flowStore.loadFlow({namespace: namespace as string, id: flowId as string, revision: revision as string | undefined, store: false})
                    if (flow?.source) {
                        flowStore.flowYaml = flow.source
                        flowStore.flowYamlOrigin = flow.source
                    }
                } catch {
                    // Autocompletion is best-effort; don't block the UI
                }
            }
        },
        {immediate: true},
    )

    const axios = useClient()

    const result = ref<{ value: string; type: string } | undefined>(undefined)
    const error = ref<string | undefined>(undefined)
    const stackTrace = ref<string | undefined>(undefined)

    const clearAll = () => {
        result.value = undefined
        error.value = undefined
        stackTrace.value = undefined
    }

    const expression = ref<string>("")
    watch(
        () => [props.property, props.path],
        ([property, path]) => {
            if (property) {
                clearAll()
                expression.value = `{{ ${property}${path ? `.${path}` : ""} }}`
            }
        },
        {immediate: true},
    )

    const onRender = () => {
        if (!props.execution) return

        clearAll()

        const url = `${apiUrl()}/executions/${props.execution.id}/actions/eval`
        axios
            .post(url, expression.value, {headers: {"Content-type": "text/plain"}})
            .then((response) => {
                if (response.data.error) {
                    error.value = response.data.error
                    stackTrace.value = response.data.stackTrace
                    return
                }

                try {
                    const parsed = JSON.parse(response.data.result)
                    result.value = {
                        value: JSON.stringify(parsed, null, 2),
                        type: "json",
                    }
                } catch {
                    result.value = {value: response.data.result, type: "text"}
                }
            })
            .catch((err) => {
                error.value = err.message || "Failed to evaluate expression"
            })
    }
</script>

<style scoped lang="scss">

#debug {
    display: flex;
    flex-direction: column;
    height: 100%;
    margin-top: calc(1rem / 2);
    padding: calc(1rem / 2) 1rem;
    border: 1px solid var(--kel-border-color-light);

    :deep(.ks-editor) {
        &.expression {
            height: calc(1rem * 2);
            margin-bottom: 1rem;
        }

        &.result {
            height: calc(1rem * 10);
        }
    }

    .buttons {
        display: inline-flex;

        & :deep(.kel-button) {
            margin-bottom: 1rem;
            padding: 1rem;
            font-size: var(--ks-font-size-sm);
            overflow: hidden;

            span:not(i span) {
                display: block;
                min-width: 0;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }

        & :deep(.kel-button:nth-of-type(2)) {
            width: calc(1rem * 4);
        }
    }

    .stack-trace {
        white-space: pre-wrap;
        word-wrap: break-word;
        font-size: var(--ks-font-size-xs);
        max-height: calc(1rem * 15);
        overflow: auto;
    }
}
</style>
