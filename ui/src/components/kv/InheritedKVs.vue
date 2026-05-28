<template>
    <KsTable :data="store.inheritedKVs" tableLayout="auto">
        <KsTableColumn prop="namespace" :label="$t('namespace')">
            <template #default="scope">
                <code>{{ scope.row.namespace }}</code>
            </template>
        </KsTableColumn>

        <KsTableColumn prop="key" :label="$t('key')">
            <template #default="scope">
                <code>{{ scope.row.key }}</code>
            </template>
        </KsTableColumn>

        <KsTableColumn prop="description" :label="$t('description')">
            <template #default="scope">
                <span>{{ scope.row.description }}</span>
            </template>
        </KsTableColumn>

        <KsTableColumn prop="updateDate" :label="$t('last modified')">
            <template #default="scope">
                <span>{{ scope.row.updateDate }}</span>
            </template>
        </KsTableColumn>

        <KsTableColumn prop="creationDate" :label="$t('created date')">
            <template #default="scope">
                <span>{{ scope.row.creationDate }}</span>
            </template>
        </KsTableColumn>
    </KsTable>
</template>

<script setup lang="ts">
    import {onMounted} from "vue"

    import {useNamespacesStore} from "override/stores/namespaces"

    interface Props {
        namespace: string;
    }

    const props = defineProps<Props>()

    const store = useNamespacesStore()

    const loadItem = (): void => {
        store.loadInheritedKVs(props.namespace)
    }
    onMounted(() => loadItem())
</script>
