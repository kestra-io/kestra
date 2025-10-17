<template>
  <el-table :data="store.inheritedKVs" table-layout="auto">
    <!-- Key Column -->
    <el-table-column prop="key" :label="$t('key')">
      <template #default="{ row }">
        <code>{{ row.key }}</code>
      </template>
    </el-table-column>

    <!-- Description Column -->
    <el-table-column prop="description" :label="$t('description')">
      <template #default="{ row }">
        <span>{{ row.description }}</span>
      </template>
    </el-table-column>

    <!-- Last Modified Column -->
    <el-table-column prop="updateDate" :label="$t('last modified')">
      <template #default="{ row }">
        <span>{{ row.updateDate }}</span>
      </template>
    </el-table-column>

    <!-- Created Date Column -->
    <el-table-column prop="creationDate" :label="$t('created date')">
      <template #default="{ row }">
        <span>{{ row.creationDate }}</span>
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
 * The component expects a `namespace` string to load inherited key-values.
 */
interface Props {
  namespace: string;
}

const props = defineProps<Props>();

// Access the Namespaces Pinia store
const store = useNamespacesStore();

/**
 * Loads inherited key-values for the given namespace.
 * Executed once when the component mounts.
 */
const loadInheritedKVs = (): void => {
  store.loadInheritedKVs(props.namespace);
};

// Lifecycle: trigger data load on mount
onMounted(loadInheritedKVs);
</script>
