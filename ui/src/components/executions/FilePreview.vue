<template>
    <el-button size="small" type="primary" :icon="EyeOutline" @click="getFilePreview">
        Preview
    </el-button>
    <drawer
        v-if="selectedPreview === value && filePreview"
        v-model="isPreviewOpen"
    >
        <template #header>
            {{ $t("preview") }}
        </template>
        <template #default>
            <el-alert v-if="filePreview.truncated" show-icon type="warning" :closable="false" class="mb-2">
                {{ $t('file preview truncated') }}
            </el-alert>
            <list-preview v-if="filePreview.type === 'LIST'" :value="filePreview.content" />
            <img v-else-if="filePreview.type === 'IMAGE'" :src="imageContent" alt="Image output preview">
            <pdf-preview v-else-if="filePreview.type === 'PDF'" :source="filePreview.content" />
            <markdown v-else-if="filePreview.type === 'MARKDOWN'" :source="filePreview.content" />
            <editor v-else :full-height="false" :input="true" :navbar="false" :model-value="filePreview.content" :lang="extensionToMonacoLang" read-only />
            <el-form class="ks-horizontal max-size mt-3">
                <el-form-item :label="$t('row count')">
                    <el-select
                        v-model="maxPreview"
                        filterable
                        clearable
                        :required="true"
                        :persistent="false"
                        @change="getFilePreview"
                    >
                        <el-option
                            v-for="item in maxPreviewOptions"
                            :key="item"
                            :label="item"
                            :value="item"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item :label="$t('encoding')">
                    <el-select
                        v-model="encoding"
                        filterable
                        clearable
                        :required="true"
                        :persistent="false"
                        @change="getFilePreview"
                    >
                        <el-option
                            v-for="item in encodingOptions"
                            :key="item.value"
                            :label="item.label"
                            :value="item.value"
                        />
                    </el-select>
                </el-form-item>
            </el-form>
        </template>
    </drawer>
</template>

<script setup>
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
</script>

<script>
    import Editor from "../inputs/Editor.vue";
    import ListPreview from "../ListPreview.vue";
    import PdfPreview from "../PdfPreview.vue";
    import {mapGetters, mapState} from "vuex";
    import Markdown from "../layout/Markdown.vue";
    import Drawer from "../Drawer.vue";

    export default {
        components: {Markdown, ListPreview, PdfPreview, Editor, Drawer},
        props: {
            value: {
                type: String,
                required: true
            },
            executionId: {
                type: String,
                required: true
            }
        },
        data() {
            return {
                isPreviewOpen: false,
                selectedPreview: null,
                maxPreview: undefined,
                encoding: undefined,
                encodingOptions: [
                    {value: "UTF-8", label: "UTF-8"},
                    {value: "ISO-8859-1", label: "ISO-8859-1/Latin-1"},
                    {value: "Cp1250", label: "Windows 1250"},
                    {value: "Cp1251", label: "Windows 1251"},
                    {value: "Cp1252", label: "Windows 1252"},
                    {value: "UTF-16", label: "UTF-16"},
                    {value: "Cp500", label: "EBCDIC IBM-500"},
                ]
            }
        },
        mounted() {
            this.maxPreview = this.configs.preview.initial;
            this.encoding = this.encodingOptions[0];
        },
        computed: {
            ...mapState("execution", ["filePreview"]),
            ...mapGetters("misc", ["configs"]),
            extensionToMonacoLang() {
                switch (this.filePreview.extension) {
                case "json":
                    return "json";
                case "jsonl":
                    return "jsonl";
                case "yaml":
                case "yml":
                case "ion":
                    // little hack to get ion colored with monaco
                    return "yaml";
                case "csv":
                    return "csv";
                case "py":
                    return "python"
                default:
                    return this.filePreview.extension;
                }
            },
            imageContent() {
                return "data:image/" + this.extension + ";base64," + this.filePreview.content;
            },
            maxPreviewOptions() {
                return [10, 25, 100, 500, 1000, 5000, 10000, 25000, 50000].filter(value => value <= this.configs.preview.max)
            }
        },
        methods: {
            getFilePreview() {
                this.selectedPreview = this.value;

                this.$store
                    .dispatch("execution/filePreview", {
                        executionId: this.executionId,
                        path: this.value,
                        maxRows: this.maxPreview,
                        encoding: this.encoding.value
                    })
                    .then(() => {
                        this.isPreviewOpen = true;
                    });
            },
        }
    }
</script>