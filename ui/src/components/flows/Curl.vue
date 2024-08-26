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
        data() {
            return {
                exampleFileName: "kestra.json"
            }
        },
        computed: {
            curlCommand() {
                const mainCommand = this.generateCurlCommand();

                if (this.flow.inputs && this.flow.inputs.find((input) => input.type === "FILE")) {
                    return `${this.toShell(this.generatePrefix())} && \\\n${this.toShell(mainCommand)}`;
                } else {
                    return `${this.toShell(mainCommand)}`;
                }
            },
            exampleFileInputUrl() {
                return `https://huggingface.co/datasets/kestra/datasets/resolve/main/json/${this.exampleFileName}`;
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
                        inputValue = this.exampleFileName;
                        break;
                    }
                    case "SECRET": {
                        inputValue = this.inputs[input.id] ? "******" : undefined;
                        break;
                    }
                    case "DURATION": {
                        inputValue = this.$moment.duration(this.$moment(this.inputs[input.id]).format("hh:mm:ss")).toJSON();
                        break;
                    }
                    case "DATE": {
                        inputValue = this.$moment(this.inputs[input.id]).format("YYYY-MM-DD");
                        break;
                    }
                    case "TIME": {
                        inputValue = this.$moment(this.inputs[input.id]).format("hh:mm:ss");
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

                return command
            },
            generatePrefix() {
                return ["curl", "-O", `'${this.exampleFileInputUrl}'`];
            },
            toShell(command) {
                return command.join(" ");
            }
        }
    }
</script>

<style lang="scss" scoped>
    /* Allow line-wraps */
    code {
        display: block;
        white-space: pre-wrap;
    }
</style>
