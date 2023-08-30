<template>
    <el-button size="small" type="primary" :icon="EyeOutline" @click="getFilePreview(value)">
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
            <list-preview v-if="filePreview.type === 'LIST'" :value="filePreview.content" />
            <img v-else-if="filePreview.type === 'IMAGE'" :src="imageContent" alt="Image output preview">
            <markdown v-else-if="filePreview.type === 'MARKDOWN'" :source="filePreview.content" />
            <editor v-else :model-value="filePreview.content" :lang="extensionToMonacoLang" read-only />
        </template>
    </el-drawer>
</template>

<script setup>
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
</script>

<script>
    import Editor from "../inputs/Editor.vue";
    import ListPreview from "../ListPreview.vue";
    import {mapState} from "vuex";
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
                selectedPreview: null
            }
        },
        computed: {
            ...mapState("execution", ["filePreview"]),
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
            }
        },
        methods: {
            getFilePreview(path) {
                this.selectedPreview = path;

                this.$store
                    .dispatch("execution/filePreview", {
                        executionId: this.executionId,
                        path: path
                    })
                    .then(() => {
                        this.isPreviewOpen = true;
                    });
            },
        }
    }
</script>