<template>
    <div class="expression-debugger">
        <h2>{{ $t("eval.expression") }}</h2>
        <KsEditor
            v-bind="editorBindings"
            v-model="editorValue"
            :navbar="false"
            :inline="true"
            :options="{fullHeight: false, customHeight: 5}"
            lang="yaml-pebble"
            class="input"
            @confirm="onDebug"
        />

        <KsButton type="primary" class="button" @click="onDebug">
            {{ $t("eval.title") }}
        </KsButton>

        <KsAlert
            v-if="error"
            type="error"
            :closable="false"
            class="error overflow-auto"
        >
            <p>
                <strong>{{ error }}</strong>
            </p>
            <div v-if="stackTrace" class="my-2">
                <CopyToClipboard
                    :text="`${error}\n\n${stackTrace}`"
                    :label="$t('copy')"
                    class="d-inline-block me-2"
                />
            </div>
            <pre v-if="stackTrace" class="stack mb-0">{{ stackTrace }}</pre>
        </KsAlert>

        <template v-else-if="result !== undefined">
            <h2>{{ $t("eval.preview") }}</h2>
            <VarValue
                v-if="execution && isFileResult"
                :value="result"
                :execution="execution"
            />
            <KsEditor
                v-else
                v-bind="editorBindings"
                :readOnly="true"
                :inline="true"
                :navbar="false"
                :options="{showScroll: true, fullHeight: false, customHeight: 8}"
                :modelValue="result"
                :lang="resultLang"
                class="result"
            />
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"

    import {KsEditor, KsButton, KsAlert} from "@kestra-io/design-system"
    import {evalExpression} from "@kestra-io/kestra-sdk/executions"

    import {useEditorBindings} from "../../../composables/useEditorBindings"
    import * as Utils from "../../../utils/utils"
    import type {Execution} from "../../../stores/executions"

    import CopyToClipboard from "../../layout/CopyToClipboard.vue"
    import VarValue from "../VarValue.vue"

    const props = defineProps<{
        execution?: Execution;
        /** Suggested expression to seed the editor with (e.g. `{{ vars.x }}`). */
        expression?: string;
    }>()

    const editorBindings = useEditorBindings()

    const editorValue = ref(props.expression ?? "")

    // Re-seed the editor whenever the parent suggests a new expression
    // (e.g. when the user selects a different variable).
    watch(
        () => props.expression,
        (value) => {
            editorValue.value = value ?? ""
            clear()
        },
    )

    const result = ref<string | undefined>(undefined)
    const resultLang = ref<"json" | "">("")
    const error = ref<string | undefined>(undefined)
    const stackTrace = ref<string | undefined>(undefined)

    const isFileResult = computed(() => result.value !== undefined && Utils.isFile(result.value))

    function clear() {
        result.value = undefined
        error.value = undefined
        stackTrace.value = undefined
    }

    async function onDebug() {
        const executionId = props.execution?.id
        if (!executionId || !editorValue.value) return

        clear()

        try {
            const response = await evalExpression({executionId, body: editorValue.value})

            if (response.error) {
                error.value = response.error
                stackTrace.value = response.stackTrace
                return
            }

            try {
                result.value = JSON.stringify(JSON.parse(response.result ?? ""), null, 2)
                resultLang.value = "json"
            } catch {
                result.value = response.result ?? ""
                resultLang.value = ""
            }
        } catch (err) {
            error.value = (err as Error).message ?? "Failed to evaluate expression"
        }
    }
</script>

<style scoped lang="scss">
.expression-debugger {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);

    .input {
        min-height: 7rem;
        border-radius: 8px;
        border: 1px solid var(--ks-border-default);
    }

    .button {
        align-self: stretch;
    }

    .error {
        overflow: auto;
    }

    .stack {
        white-space: pre-wrap;
        word-break: break-word;
        font-size: var(--ks-font-size-xs);
        max-height: 15rem;
        overflow: auto;
    }

    h2{
        margin: 0;
        margin-top: 1.5rem;
        font-size: var(--ks-font-size-sm);
    }

    h2:first-of-type {
        margin-top: 0;
    }
}
</style>
