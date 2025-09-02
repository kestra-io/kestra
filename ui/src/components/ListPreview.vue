<template>
    <el-table data-component="FILENAME_PLACEHOLDER" :data="value" stripe>
        <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column" :label="column">
            <template #default="scope">
                <template v-if="isComplex(scope.row[column])">
                    <el-input
                        type="textarea"
                        :model-value="truncate(JSON.stringify(scope.row[column], null, 2))"
                        readonly
                        :rows="3"
                        autosize
                        class="ks-editor"
                        resize="none"
                    />
                </template>
                <template v-else>
                    {{ truncate(scope.row[column]) }}
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>
<script>
    export default {
        name: "ListPreview",
        props: {
            value: {
                type: Array,
                required: true
            }
        },
        data() {
            return {
                maxColumnLength: 100
            }
        },
        computed: {
            generateTableColumns() {
                const allKeys = new Set();
                this.value.forEach(item => {
                    Object.keys(item).forEach(key => allKeys.add(key));
                });
                return Array.from(allKeys);
            }
        },
        methods: {
            isComplex(data) {
                return data instanceof Array || data instanceof Object;
            },
            truncate(text) {
                if (typeof text !== "string") return text;
                return text.length > this.maxColumnLength
                    ? text.slice(0, this.maxColumnLength) + "..."
                    : text;
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