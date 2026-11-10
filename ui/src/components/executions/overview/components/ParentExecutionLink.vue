<template>
    <router-link
        v-if="originalLink"
        class="meta-item"
        data-test="execution-original-link"
        :to="originalLink"
    >
        <History />
        <span>
            {{ $t("original execution") }}:
            <span class="meta-item__link">{{ execution.originalId }}</span>
        </span>
    </router-link>

    <router-link
        v-if="parentLink"
        class="meta-item"
        data-test="execution-parent-link"
        :to="parentLink.to"
    >
        <History />
        <span>
            {{ $t("parent execution") }}:
            <span class="meta-item__link">{{ parentLink.id }}</span>
        </span>
    </router-link>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import History from "vue-material-design-icons/History.vue"

    import {type Execution} from "../../../../stores/executions"
    import {createLink} from "../utils/links"

    const props = defineProps<{execution: Execution}>()

    const text = (value: unknown): string | undefined =>
        typeof value === "string" && value.length ? value : undefined

    const parent = computed(() => {
        const variables = props.execution.trigger?.variables as
            | Record<string, unknown>
            | undefined

        const id = text(variables?.executionId)
        const namespace = text(variables?.namespace)
        const flowId = text(variables?.flowId)

        if (id && namespace && flowId) return {id, namespace, flowId}

        const parentId = text(props.execution.parentId)

        return parentId
            ? {
                id: parentId,
                namespace: props.execution.namespace,
                flowId: props.execution.flowId,
            }
            : undefined
    })

    const originalLink = computed(() => {
        const {originalId, id} = props.execution
        return originalId && originalId !== id
            ? createLink("executions", props.execution, originalId)
            : undefined
    })

    const parentLink = computed(() => {
        const target = parent.value
        const {id: currentId, originalId, tenantId} = props.execution

        if (!target || target.id === currentId || target.id === originalId) return undefined

        return {
            id: target.id,
            to: createLink(
                "executions",
                {
                    id: currentId,
                    namespace: target.namespace,
                    flowId: target.flowId,
                    tenantId,
                },
                target.id,
            ),
        }
    })
</script>

<style scoped>
    .meta-item {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-xs);
        color: var(--ks-text-secondary);
    }

    .meta-item .material-design-icon {
        color: var(--ks-text-muted);
        display: inline-flex;
    }

    .meta-item .meta-item__link {
        color: var(--ks-text-link);
    }

    .meta-item:hover {
        background: var(--ks-bg-hover);
        color: var(--ks-text-primary);
    }

    a.meta-item:hover span {
        color: var(--ks-text-link);
    }
</style>
