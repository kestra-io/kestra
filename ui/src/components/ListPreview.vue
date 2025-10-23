<template>
    <el-table :data="value" stripe>
        <el-table-column v-for="(column, index) in generateTableColumns" :key="index" :prop="column" :label="column">
            <template #default="scope">
                <span v-if="isComplex(scope.row[column])">
                    <el-tooltip :content="JSON.stringify(scope.row[column], null, 2)">
                        <span class="preview-row">{{ truncate(JSON.stringify(scope.row[column])) }}</span>
                    </el-tooltip>
                </span>
                <span v-else>
                    {{ scope.row[column] }}
                </span>
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

    .preview-row {
        height: 24px;
        overflow: hidden;
        white-space: pre;
        text-overflow: ellipsis;
        display: inline-block;
        max-width: 100%;
    }
</style>