<template>
    <div class="position-relative">
        <pre><code>{{ curlCommand }}</code></pre>

        <CopyToClipboard :text="curlCommand" />

        <el-alert class="mt-3" type="info" showIcon :closable="false">
            {{ $t('curl.note') }}
        </el-alert>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {baseUrl, basePath, apiUrl} from "override/utils/route";
    import CopyToClipboard from "../layout/CopyToClipboard.vue";
    import moment from "moment";
    import {Flow} from "../../stores/flow";
    import {Label} from "../../stores/executions";

    const props = withDefaults(defineProps<{
        flow: Flow;
        inputs?: Record<string, any>;
        executionLabels?: Label[];
        verbose?: boolean;
    }>(),{
        inputs: () => ({}),
        executionLabels: () => [],
        verbose: true
    });

    const exampleFileName = ref("kestra.json");

    const exampleFileInputUrl = computed(() =>
        `https://huggingface.co/datasets/kestra/datasets/resolve/main/json/${exampleFileName.value}`
    );

    function addHeader(command: string[], name: string, value: string) {
        command.push("-H", `'${name}: ${value}'`);
    }

    function addInputs(command: string[]) {
        if (!props.flow.inputs) return;

        props.flow.inputs.forEach((input) => {
            let inputValue: string | undefined;

            switch (input.type) {
            case "FILE":
                inputValue = exampleFileName.value;
                break;
            case "SECRET":
                inputValue = props.inputs?.[input.id] ? "******" : undefined;
                break;
            case "DATE":
                inputValue = moment(props.inputs?.[input.id]).format("YYYY-MM-DD");
                break;
            case "TIME":
                inputValue = moment(props.inputs?.[input.id]).format("hh:mm:ss");
                break;
            default:
                inputValue = props.inputs?.[input.id];
            }

            if (inputValue === undefined) return;

            command.push("-F");
            if (input.type === "FILE") {
                command.push(`'${input.id}=@${inputValue};filename=${inputValue}'`);
            } else {
                command.push(`'${input.id}=${inputValue}'`);
            }
        });
    }

    function generateExecutionLabel(key: string, value: string) {
        return `labels=${encodeURIComponent(key)}:${encodeURIComponent(value)}`;
    }

    const url = computed(() => {
        const queryParams = (props.executionLabels || [])
            .filter((label) => label.key && label.value)
            .map((label) => generateExecutionLabel(label.key, label.value));

        const origin = baseUrl ? apiUrl() : `${location.origin}${basePath()}`;
        let ret = `${origin}/executions/${props.flow.namespace}/${props.flow.id}`;

        if (queryParams.length > 0) {
            ret += `?${queryParams.join("&")}`;
        }

        return ret;
    })

    const prefix = computed(() => {
        return ["curl", "-O", `'${exampleFileInputUrl.value}'`];
    });

    function toShell(command: string[]) {
        return command.join(" ");
    }

    const curlCommand = computed(() => {
        const mainCommand = ["curl"];

        if (props.verbose) mainCommand.push("-v");

        mainCommand.push("-X", "POST");
        addHeader(mainCommand, "Content-Type", "multipart/form-data");
        addInputs(mainCommand);
        mainCommand.push(`'${url.value}'`);
        const hasFileInput = props.flow.inputs?.some((input) => input.type === "FILE");

        if (hasFileInput) {
            return `${toShell(prefix.value)} && \\\n${toShell(mainCommand)}`;
        }
        return `${toShell(mainCommand)}`;
    });
</script>

<style scoped lang="scss">
    pre {
        border-radius: var(--bs-border-radius);
    }

    /* Allow line-wraps */
    code {
        display: block;
        white-space: pre-wrap;
    }
</style>
