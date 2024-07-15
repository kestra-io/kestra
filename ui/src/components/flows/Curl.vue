<template>
    <div class="position-relative">
        <code>
            {{ curlCommand }}
        </code>

        <copy-to-clipboard class="position-absolute" :text="curlCommand" />

        <el-alert class="mt-3" type="warning" show-icon :closable="false">
            {{ $t('curl.note') }}
        </el-alert>
    </div>
</template>

<script>
    import {baseUrl, basePath, apiUrl} from "override/utils/route";
    import CopyToClipboard from "../layout/CopyToClipboard.vue";

    export default {
        components: {CopyToClipboard},
        props: {
            flow: {
                type: Object,
                required: true
            },
            inputs: {
                type: Object,
                default: () => {}
            },
            executionLabels: {
                type: Array,
                default: () => []
            },
            verbose: {
                type: Boolean,
                default: true
            }
        },
        computed: {
            curlCommand() {
                return this.generateCurlCommand();
            }
        },
        methods: {
            addHeader(command, name, value) {
                command.push("-H", `'${name}: ${value}'`);
            },
            addInputs(command) {
                if (!this.flow.inputs) {
                    return;
                }

                this.flow.inputs.forEach((input) => {
                    let inputValue;

                    switch (input.type) {
                    case "FILE": {
                        const fileInput = this.inputs[input.id];
                        if (fileInput) {
                            inputValue = fileInput.name;
                        }
                        break;
                    }
                    case "SECRET": {
                        inputValue = this.inputs[input.id] ? "******" : undefined;
                        break;
                    }
                    default:
                        inputValue = this.inputs[input.id];
                    }

                    if (inputValue === undefined) {
                        return;
                    }

                    command.push("-F");

                    if (input.type === "FILE") {
                        command.push(`'files=@${inputValue};filename=${input.id}'`);
                    } else {
                        command.push(`'${input.id}=${inputValue}'`);
                    }
                });
            },
            generateExecutionLabel(key, value) {
                return `labels=${encodeURIComponent(key)}:${encodeURIComponent(value)}`;
            },
            generateUrl() {
                const queryParams = this.executionLabels
                    .filter((label) => label.key !== null && label.value !== null && label.key !== "" && label.value !== "")
                    .map((label) => this.generateExecutionLabel(label.key, label.value));

                const origin = baseUrl ? apiUrl(this.$store) : `${location.origin}${basePath()}`;

                var url = `${origin}/executions/${this.flow.namespace}/${this.flow.id}`;

                if (queryParams.length > 0) {
                    url += `?${queryParams.join("&")}`;
                }

                return url;
            },
            generateCurlCommand() {
                const command = ["curl"];

                if (this.verbose) {
                    command.push("-v");
                }

                command.push("-X", "POST");

                this.addHeader(command, "Content-Type", "multipart/form-data");

                this.addInputs(command);

                command.push(`'${this.generateUrl()}'`);

                return command.join(" ");
            }
        }
    }
</script>