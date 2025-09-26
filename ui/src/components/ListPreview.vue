<template>
    <el-table data-component="FILENAME_PLACEHOLDER" :data="value" stripe>
        <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column" :label="column">
            <template #default="scope">
                <template v-if="isComplex(scope.row[column])">
                    <el-input
                        type="textarea"
                        :modelValue="truncate(JSON.stringify(scope.row[column], null, 2))"
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

<script setup lang="ts">
    import {ref, computed} from "vue";

    const props = defineProps({
        value: {
            type: Array as () => Record<string, any>[],
            required: true
        }
    });

    const maxColumnLength = ref(100);

    const generateTableColumns = computed(() => {
        const allKeys = new Set<string>();
        props.value.forEach(item => {
            Object.keys(item).forEach(key => allKeys.add(key));
        });
        return Array.from(allKeys);
    });

    const isComplex = (data: any): boolean => {
        return data instanceof Array || data instanceof Object;
    };

    const truncate = (text: any): string | any => {
        if (typeof text !== "string") return text;
        return text.length > maxColumnLength.value
            ? text.slice(0, maxColumnLength.value) + "..."
            : text;
    };
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