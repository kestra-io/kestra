<template>
    <el-button size="small" type="primary" :icon="EyeOutline" @click="getFilePreview">
        Preview
    </el-button>
    <el-drawer
        v-if="selectedPreview === value && filePreview"
        v-model="isPreviewOpen"
        destroy-on-close
        lock-scroll
        size=""
        :append-to-body="true"
    >
        <template #header>
            <h3>{{ $t("preview") }}</h3>
        </template>
        <template #default>
            <el-alert v-if="filePreview.truncated"  show-icon type="warning" :closable="false" class="mb-2">
                {{ $t('file preview truncated') }}
            </el-alert>
            <list-preview v-if="filePreview.type === 'LIST'" :value="filePreview.content" />
            <img v-else-if="filePreview.type === 'IMAGE'" :src="imageContent" alt="Image output preview">
            <markdown v-else-if="filePreview.type === 'MARKDOWN'" :source="filePreview.content" />
            <editor v-else :full-height="false" :input="true" :navbar="false" :model-value="filePreview.content" :lang="extensionToMonacoLang" read-only />
            <el-form class="ks-horizontal max-size mt-3">
                <el-form-item :label="$t('show')">
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
            </el-form>
        </template>
    </el-drawer>
</template>

<script setup>
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
</script>

<script>
    import Editor from "../inputs/Editor.vue";
    import ListPreview from "../ListPreview.vue";
    import {mapGetters, mapState} from "vuex";
    import Markdown from "../layout/Markdown.vue";

    export default {
        components: {Markdown, ListPreview, Editor},
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
            }
        },
        mounted() {
            this.maxPreview = this.configs.preview.initial;
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
                        maxRows: this.maxPreview
                    })
                    .then(() => {
                        this.isPreviewOpen = true;
                    });
            },
        }
    }
</script>