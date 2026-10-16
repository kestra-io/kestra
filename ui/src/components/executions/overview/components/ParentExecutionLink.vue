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

    /**
     * Both lineage rows render from here and they do not always live in the same flow.
     *
     * `trigger.variables` carries the upstream execution for children started by a subflow
     * task, a ForEachItem or a flow trigger, which is the lineage #19606 is about. The
     * backend ships `executionId`, `namespace` and `flowId` together in that bag
     * (`ExecutableUtils.java:222-228`, `trigger/Flow.java:321-324`), so the parent's own
     * flow is already known and needs no lookup. Requiring all three also keeps an
     * unrelated plugin trigger that happens to expose an `executionId` from being read as
     * lineage.
     *
     * `parentId` is set only by `Execution.childExecution(...)`, which copies `namespace`,
     * `flowId` and `tenantId` from the parent (`Execution.java:409-411`), so restart and
     * replay lineage stays on the current execution's flow.
     */
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

        // after a first restart `originalId === parentId` and the original execution row
        // already shows that id, so the parent row skips the duplicate
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
    /*
     * Both lineage rows render from this component, so their styles live here and no
     * parent scoped rule has to reach across the component boundary.
     */
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
