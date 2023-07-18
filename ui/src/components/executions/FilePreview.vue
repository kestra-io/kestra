<template>
    <csv v-if="extensionToMonacoLang === 'csv'" :value="formattedContent" />
    <img v-else-if="extensionToMonacoLang === 'image'" :src="formattedContent" alt="Image output preview">
    <editor v-else :model-value="formattedContent" :lang="extensionToMonacoLang" read-only />
</template>
<script>
    import Editor from "../inputs/Editor.vue";
    import * as ion from "ion-js";
    import Papa from "papaparse";
    import Csv from "../Csv.vue";

    export default {
        components: {Csv, Editor},
        props: {
            content: {
                type: String,
                required: true
            },
            extension: {
                type: String,
                required: true
            }
        },
        computed: {
            extensionToMonacoLang() {
                switch(this.extension) {
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
                switch(this.extension) {
                case "json":
                    return JSON.stringify(JSON.parse(this.content), null, 2);
                case "ion":
                    return ion.dumpPrettyText(ion.load(this.content));
                case "csv":
                    return Papa.parse(this.content);
                case "jpg":
                case "jpeg":
                case "png":
                case "svg":
                    return "data:image/" + this.extension + ";base64," + this.content;
                default:
                    return this.content;
                }
            }
        },
        methods: {
        }
    }
</script>