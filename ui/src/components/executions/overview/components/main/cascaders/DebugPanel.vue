<template>
    <div id="debug">
        <Editor
            v-model="expression"
            :shouldFocus="false"
            :navbar="false"
            input
            class="expression"
        />

        <div class="buttons">
            <el-button type="primary" :icon="Refresh" @click="onRender">
                {{ $t("eval.render") }}
            </el-button>
            <el-button
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

        <el-alert
            v-else-if="error"
            type="error"
            :title="error"
            showIcon
            :closable="false"
        >
            <pre v-if="stackTrace" class="mb-0 stack-trace">{{ stackTrace }}</pre>
        </el-alert>
    </div>
</template>

<script setup lang="ts">
    import {watch, ref} from "vue";

    import Editor from "../../../../../inputs/Editor.vue";
    import VarValue from "../../../../VarValue.vue";

    import {Execution} from "../../../../../../stores/executions";

    import Refresh from "vue-material-design-icons/Refresh.vue";
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue";

    import Utils from "../../../../../../utils/utils";
    import {apiUrl} from "override/utils/route";
    import {useAxios} from "../../../../../../utils/axios";

    const props = defineProps<{
        property?: "outputs" | "trigger";
        execution: Execution;
        path?: string;
    }>();

    const result = ref<{ value: string; type: string } | undefined>(undefined);
    const error = ref<string | undefined>(undefined);
    const stackTrace = ref<string | undefined>(undefined);

    const clearAll = () => {
        result.value = undefined;
        error.value = undefined;
        stackTrace.value = undefined;
    };

    const expression = ref<string>("");
    watch(
        () => [props.property, props.path],
        ([property, path]) => {
            if (property) {
                clearAll();
                expression.value = `{{ ${property}${path ? `.${path}` : ""} }}`;
            }
        },
        {immediate: true},
    );

    const axios = useAxios();
    const onRender = () => {
        if (!props.execution) return;

        clearAll();

        const url = `${apiUrl()}/executions/${props.execution.id}/eval`;
        axios
            .post(url, expression.value, {headers: {"Content-type": "text/plain"}})
            .then((response) => {
                if (response.data.error) {
                    error.value = response.data.error;
                    stackTrace.value = response.data.stackTrace;
                    return;
                }

                try {
                    const parsed = JSON.parse(response.data.result);
                    result.value = {
                        value: JSON.stringify(parsed, null, 2),
                        type: "json",
                    };
                } catch {
                    result.value = {value: response.data.result, type: "text"};
                }
            })
            .catch((err) => {
                error.value = err.message || "Failed to evaluate expression";
            });
    };
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

#debug {
    display: flex;
    flex-direction: column;
    height: 100%;
    margin-top: calc($spacer / 2);
    padding: calc($spacer / 2) $spacer;
    border: 1px solid var(--el-border-color-light);

    :deep(.ks-editor) {
        &.expression {
            height: calc($spacer * 2);
            margin-bottom: $spacer;
        }

        &.result {
            height: calc($spacer * 10);
        }
    }

    .buttons {
        display: inline-flex;

        & :deep(.el-button) {
            margin-bottom: $spacer;
            padding: $spacer;
            font-size: $font-size-sm;
            overflow: hidden;

            span:not(i span) {
                display: block;
                min-width: 0;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }

        & :deep(.el-button:nth-of-type(2)) {
            width: calc($spacer * 4);
        }
    }

    .stack-trace {
        white-space: pre-wrap;
        word-wrap: break-word;
        font-size: $font-size-xs;
        max-height: calc($spacer * 15);
        overflow: auto;
    }
}
</style>
