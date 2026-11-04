<template>
    <div class="curl-wrapper">
        <pre><code>{{ curlCommand }}</code></pre>

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
    import {dayjs} from "@kestra-io/design-system"
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
    }>(),{
        inputs: () => ({}),
        executionLabels: () => [],
        verbose: true,
    })

    const exampleFileName = ref("kestra.json")

    const exampleFileInputUrl = computed(() =>
        `https://huggingface.co/datasets/kestra/datasets/resolve/main/json/${exampleFileName.value}`,
    )

    function addHeader(command: string[], name: string, value: string) {
        command.push("-H", `'${name}: ${value}'`)
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

            command.push("-F")
            if (input.type === "FILE") {
                command.push(`'${input.id}=@${inputValue};filename=${inputValue}'`)
            } else {
                command.push(`'${input.id}=${inputValue}'`)
            }
        })
    }

    function generateExecutionLabel(key: string, value: string) {
        return `labels=${encodeURIComponent(key)}:${encodeURIComponent(value)}`
    }

    const url = computed(() => {
        const queryParams = (props.executionLabels || [])
            .filter((label) => label.key && label.value)
            .map((label) => generateExecutionLabel(label.key, label.value))

        const origin = baseUrl ? apiUrl() : `${location.origin}${basePath()}`
        let ret = `${origin}/executions/${props.flow.namespace}/${props.flow.id}`

        if (queryParams.length > 0) {
            ret += `?${queryParams.join("&")}`
        }

        return ret
    })

    const prefix = computed(() => {
        return ["curl", "-O", `'${exampleFileInputUrl.value}'`]
    })

    function toShell(command: string[]) {
        return command.join(" ")
    }

    const curlCommand = computed(() => {
        const mainCommand = ["curl"]

        if (props.verbose) mainCommand.push("-v")

        mainCommand.push("-X", "POST")
        addHeader(mainCommand, "Content-Type", "multipart/form-data")
        addInputs(mainCommand)
        mainCommand.push(`'${url.value}'`)
        const hasFileInput = leafInputs.value.some((input) => input.type === "FILE")

        if (hasFileInput) {
            return `${toShell(prefix.value)} && \\\n${toShell(mainCommand)}`
        }
        return `${toShell(mainCommand)}`
    })
</script>

<style scoped>
    .curl-wrapper {
        position: relative;
    }

    .curl-alert {
        margin-top: var(--ks-spacing-4);
    }

    pre {
        border-radius: var(--kel-border-radius-base);
        padding-right: var(--ks-spacing-8);
    }

    /* Allow line-wraps */
    code {
        display: block;
        white-space: pre-wrap;
        overflow-wrap: anywhere;
    }
</style>
