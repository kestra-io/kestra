<template>
    <el-table :data="store.inheritedKVs" tableLayout="auto">
        <el-table-column prop="key" :label="$t('key')">
            <template #default="scope">
                <code>{{ scope.row.key }}</code>
            </template>
        </el-table-column>

        <el-table-column prop="description" :label="$t('description')">
            <template #default="scope">
                <span>{{ scope.row.description }}</span>
            </template>
        </el-table-column>

        <el-table-column prop="updateDate" :label="$t('last modified')">
            <template #default="scope">
                <span>{{ scope.row.updateDate }}</span>
            </template>
        </el-table-column>

        <el-table-column prop="creationDate" :label="$t('created date')">
            <template #default="scope">
                <span>{{ scope.row.creationDate }}</span>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { useNamespacesStore } from "override/stores/namespaces";

/**
 * Props definition
 * ----------------
 * The component receives a single required prop: `namespace`.
 */
interface Props {
  namespace: string;
}

// Define props using TypeScript interface
const props = defineProps<Props>();

// Use the Namespaces store from Pinia
const store = useNamespacesStore();

/**
 * Loads inherited key-values for the given namespace.
 * Called once when the component mounts.
 */
const loadItem = (): void => {
  store.loadInheritedKVs(props.namespace);
};

// Lifecycle hook
onMounted(loadItem);
</script>
