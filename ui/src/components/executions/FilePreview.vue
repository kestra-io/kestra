<template>
    <EyeOutline role="button" @click="getFilePreview(value)" />
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
            <csv v-if="extensionToMonacoLang === 'csv'" :value="formattedContent" />
            <img v-else-if="extensionToMonacoLang === 'image'" :src="formattedContent" alt="Image output preview">
            <editor v-else :model-value="formattedContent" :lang="extensionToMonacoLang" read-only />
        </template>
    </el-drawer>
</template>
<script>
    import Editor from "../inputs/Editor.vue";
    import * as ion from "ion-js";
    import Papa from "papaparse";
    import Csv from "../Csv.vue";
    import EyeOutline from "vue-material-design-icons/EyeOutline.vue";
    import {mapState} from "vuex";

    export default {
        components: {EyeOutline, Csv, Editor},
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
        watch: {
            filePreview() {
                this.isPreviewOpen = true;
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
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "svg":
                case "bpm":
                case "webp":
                    return "image";
                default:
                    return "text";
                }
            },
            formattedContent() {
                switch (this.filePreview.extension) {
                case "json":
                    return JSON.stringify(JSON.parse(this.filePreview.content), null, 2);
                case "ion":
                    return ion.dumpPrettyText(ion.load(this.filePreview.content));
                case "csv":
                    return Papa.parse(this.filePreview.content);
                case "jpg":
                case "jpeg":
                case "png":
                case "svg":
                    return "data:image/" + this.extension + ";base64," + this.filePreview.content;
                default:
                    return this.filePreview.content;
                }
            }
        },
        methods: {
            getFilePreview(path) {
                this.$store.dispatch("execution/filePreview", {
                    executionId: this.executionId,
                    path: path
                })
                this.selectedPreview = path
            },
        }
    }
</script>