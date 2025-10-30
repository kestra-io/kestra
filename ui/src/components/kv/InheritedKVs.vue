<template>
    <el-table :data="store.inheritedKVs" tableLayout="auto">
        <el-table-column prop="namespace" :label="$t('namespace')">
            <template #default="scope">
                <code>{{ scope.row.namespace }}</code>
            </template>
        </el-table-column>

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
    import {onMounted} from "vue";

    import {useNamespacesStore} from "override/stores/namespaces";

    interface Props {
        namespace: string;
    }

    const props = defineProps<Props>();

    const store = useNamespacesStore();

    const loadItem = (): void => {
        store.loadInheritedKVs(props.namespace);
    };
    onMounted(() => loadItem());
</script>
