<template>
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
     * Two lineages end up here and they do not live in the same flow.
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

    const parentLink = computed(() => {
        const target = parent.value
        const {id: currentId, originalId, tenantId} = props.execution

        // the banner already renders an `original execution` row, and after a first restart
        // `originalId === parentId`, so skip the duplicate rather than showing the same id twice
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
     * `Banner.vue`'s scoped `.meta-item .material-design-icon` rule only reaches this
     * component's root element, so the icon is muted here to match the sibling meta rows.
     */
    .meta-item .material-design-icon {
        color: var(--ks-text-muted);
    }
</style>
