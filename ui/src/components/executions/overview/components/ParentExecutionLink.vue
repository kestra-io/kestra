<template>
    <span v-if="execution.parentId" class="meta-item">
        <ArrowUp aria-hidden="true" />
        <KsButton
            link
            :loading="loading"
            :disabled="loading"
            @click="openParentExecution"
        >
            {{ $t("parent execution") }}: {{ execution.parentId }}
        </KsButton>
    </span>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import ArrowUp from "vue-material-design-icons/ArrowUp.vue"

    import {useExecutionsStore, type Execution} from "../../../../stores/executions"
    import {useToast} from "../../../../utils/toast"

    const props = defineProps<{execution: Execution}>()

    const {t} = useI18n({useScope: "global"})
    const route = useRoute()
    const router = useRouter()
    const executionsStore = useExecutionsStore()
    const toast = useToast()
    const loading = ref(false)

    const openParentExecution = async () => {
        if (!props.execution.parentId || loading.value) return

        loading.value = true

        try {
            const parentExecution = await executionsStore.fetchExecution({id: props.execution.parentId})
            const params: Record<string, string> = {
                namespace: parentExecution.namespace,
                flowId: parentExecution.flowId,
                id: parentExecution.id,
            }
            const tenant = parentExecution.tenantId ?? route.params.tenant

            if (tenant) params.tenant = String(tenant)

            await router.push({
                name: "executions/update/overview",
                params,
            })
        } catch {
            toast.error(t("error"))
        } finally {
            loading.value = false
        }
    }
</script>
