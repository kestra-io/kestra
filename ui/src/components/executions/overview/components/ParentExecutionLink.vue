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

    /**
     * `trigger.variables.executionId` is the upstream execution for children started by a
     * subflow task or by a flow trigger, which is the lineage #19606 is about. `parentId` is
     * only set by `Execution.childExecution(...)`, so it covers restart and replay lineage.
     */
    const parentId = computed(() => {
        const triggerParentId = (props.execution.trigger?.variables as Record<string, unknown> | undefined)
            ?.executionId

        return typeof triggerParentId === "string" && triggerParentId.length
            ? triggerParentId
            : props.execution.parentId ?? undefined
    })

    const parentLink = computed(() => {
        const id = parentId.value
        const {id: currentId, originalId} = props.execution

        // the banner already renders an `original execution` row, and after a first restart
        // `originalId === parentId`, so skip the duplicate rather than showing the same id twice
        if (!id || id === currentId || id === originalId) return undefined

        return {id, to: createLink("executions", props.execution, id)}
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
