<template>
    <el-table data-component="FILENAME_PLACEHOLDER" :data="value" stripe>
        <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column" :label="column">
            <template #default="scope">
                <template v-if="isComplex(scope.row[column])">
                    <editor
                        :full-height="false"
                        :input="true"
                        :navbar="false"
                        :model-value="JSON.stringify(scope.row[column])"
                        lang="json"
                        read-only
                    />
                </template>
                <template v-else>
                    {{ scope.row[column] }}
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>
<script>
    import Editor from "./inputs/Editor.vue";

    export default {
        name: "ListPreview",
        components: {Editor},
        props: {
            value: {
                type: Array,
                required: true
            }
        },
        computed: {
            generateTableColumns() {
                return Object.keys(this.value[0]);
            }
        },
        methods: {
            isComplex(data) {
                return data instanceof Array || data instanceof Object;
            }
        }
    }
</script>

<style scoped lang="scss">
    :deep(.ks-editor) {
        .editor-container {
            box-shadow: none;
            background-color: transparent !important;
            padding: 0;

            .monaco-editor, .monaco-editor-background {
                background-color: transparent;
            }
        }
    }
</style>