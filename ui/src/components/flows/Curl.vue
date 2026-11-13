<template>
    <div class="curl-wrapper">
        <div class="code">
            <KsEditor
                v-bind="editorBindings"
                :modelValue="curlCommand"
                lang="shell"
                inline
                :navbar="false"
                readOnly
                :options="{
                    fullHeight: false,
                    customHeight: 24,
                    editor: {
                        padding: {top: 6, bottom: 6},
                        guides: {indentation: false},
                        wrappingIndent: 'indent',
                    },
                }"
            />
        </div>

        <CopyToClipboard :text="curlCommand" />

        <KsAlert class="curl-alert" type="info" :closable="false">
            {{ $t('curl.note') }}
        </KsAlert>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {baseUrl, basePath, apiUrl} from "override/utils/route"
    import CopyToClipboard from "../layout/CopyToClipboard.vue"
    import {dayjs, KsEditor} from "@kestra-io/design-system"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {Flow} from "../../stores/flow"
    import {Label} from "../../stores/executions"
    import {flattenInputs} from "../../utils/inputs"

    type DateInput = Parameters<typeof dayjs>[0]

    const DATE_LIKE_TYPES = ["DATE", "TIME", "DATETIME"]

    const props = withDefaults(defineProps<{
        flow: Flow;
        inputs?: Record<string, unknown>;
        executionLabels?: Label[];
        verbose?: boolean;
        replay?: {executionId: string; taskRunId?: string; revision?: number};
    }>(),{
        replay: undefined,
        inputs: () => ({}),
        executionLabels: () => [],
        verbose: true,
    })

    const editorBindings = useEditorBindings()

    const exampleFileName = ref("kestra.json")

    const exampleFileInputUrl = computed(() =>
        `https://huggingface.co/datasets/kestra/datasets/resolve/main/json/${exampleFileName.value}`,
    )

    function addHeader(command: string[], name: string, value: string) {
        command.push(`-H '${name}: ${value}'`)
    }

    const leafInputs = computed(() => flattenInputs(props.flow.inputs))

    function addInputs(command: string[]) {
        leafInputs.value.forEach((input) => {
            let inputValue: string | undefined
            const value = props.inputs?.[input.id]

            // An untouched or cleared picker holds undefined, null or "", which Execute leaves out
            // (normalizeInputValues), and dayjs() would turn into the current date or "Invalid Date".
            if (DATE_LIKE_TYPES.includes(input.type) && (value === undefined || value === null || value === "")) return

            switch (input.type) {
            case "FILE":
                inputValue = exampleFileName.value
                break
            case "SECRET":
                inputValue = value ? "******" : undefined
                break
            case "DATE":
                inputValue = dayjs(value as DateInput).format("YYYY-MM-DD")
                break
            case "TIME":
                inputValue = dayjs(value as DateInput).format("HH:mm:ss")
                break
            case "DATETIME":
            {
                // The picker holds a Date, and String(date) is not a value the API can parse.
                const date = dayjs(value as DateInput)
                inputValue = date.isValid() ? date.toISOString() : String(value)
                break
            }
            default:
                inputValue = value === undefined ? undefined : String(value)
            }

            if (inputValue === undefined) return

            if (input.type === "FILE") {
                command.push(`-F '${input.id}=@${inputValue};filename=${inputValue}'`)
            } else {
                command.push(`-F '${input.id}=${inputValue}'`)
            }
        })
    }

    function generateExecutionLabel(key: string, value: string) {
        return `labels=${encodeURIComponent(key)}:${encodeURIComponent(value)}`
    }

    const url = computed(() => {
        const origin = baseUrl ? apiUrl() : `${location.origin}${basePath()}`

        if (props.replay) {
            const replayParams = [
                props.replay.taskRunId ? `taskRunId=${encodeURIComponent(props.replay.taskRunId)}` : undefined,
                props.replay.revision === undefined ? undefined : `revision=${props.replay.revision}`,
            ].filter(Boolean)
            const replayUrl = `${origin}/executions/${props.replay.executionId}/actions/replay-with-inputs`
            return replayParams.length > 0 ? `${replayUrl}?${replayParams.join("&")}` : replayUrl
        }

        const queryParams = (props.executionLabels || [])
            .filter((label) => label.key && label.value)
            .map((label) => generateExecutionLabel(label.key, label.value))

        let ret = `${origin}/executions/${props.flow.namespace}/${props.flow.id}`

        if (queryParams.length > 0) {
            ret += `?${queryParams.join("&")}`
        }

        return ret
    })

    const prefix = computed(() => {
        return `curl -O '${exampleFileInputUrl.value}'`
    })

    function toShell(command: string[]) {
        return command.join(" \\\n  ")
    }

    const curlCommand = computed(() => {
        const mainCommand = [props.verbose ? "curl -v -X POST" : "curl -X POST"]

        addHeader(mainCommand, "Content-Type", "multipart/form-data")
        addInputs(mainCommand)
        mainCommand.push(`'${url.value}'`)
        const hasFileInput = leafInputs.value.some((input) => input.type === "FILE")

        if (hasFileInput) {
            return `${prefix.value} && \\\n${toShell(mainCommand)}`
        }
        return `${toShell(mainCommand)}`
    })
</script>

<style scoped>
    .curl-wrapper {
        position: relative;
    }

    .code {
        padding-right: var(--ks-spacing-8);
    }

    .curl-alert {
        margin-top: var(--ks-spacing-4);
    }
</style>
