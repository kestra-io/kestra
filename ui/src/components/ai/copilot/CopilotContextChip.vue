<template>
    <!-- The resources the copilot is focused on (sent as `additionalContext`), one dismissible pill
         each: the type word (Flow / Execution / Namespace / …) plus the value as a code-styled,
         link-coloured `KsId` token — the same treatment ids get in tables. Removing a pill drops only
         that resource from the focus for the next turn. -->
    <div v-if="pills.length" class="copilot-context" data-test="copilot-context-chip">
        <KsTag
            v-for="pill in pills"
            :key="pill.part"
            closable
            size="small"
            :data-test="`copilot-context-${pill.part}`"
            @close="emit('remove', pill.part)"
        >
            <i18n-t :keypath="pill.keypath" scope="global" tag="span">
                <template #[pill.slot]>
                    <KsId :value="pill.value" :shrink="false" />
                </template>
            </i18n-t>
        </KsTag>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import type {ScopeBinding, ContextPart} from "./types"
    import {CONTEXT_PART_I18N, CONTEXT_PRIMARY} from "./routeScope"

    const props = defineProps<{scope: ScopeBinding}>()
    const emit = defineEmits<{remove: [part: ContextPart]}>()

    interface Pill {
        part: ContextPart
        keypath: string
        slot: string
        value: string
    }

    // One pill per present field — the resource first, then its namespace (deduped for a namespace
    // scope, whose primary already is the namespace). Absent fields are skipped.
    const pills = computed<Pill[]>(() => {
        const scope = props.scope
        const parts: ContextPart[] = [CONTEXT_PRIMARY[scope.kind]]
        if (!parts.includes("namespace")) parts.push("namespace")
        return parts
            .filter((part): part is ContextPart => Boolean(scope[part]))
            .map((part) => ({part, ...CONTEXT_PART_I18N[part], value: scope[part] as string}))
    })
</script>

<style scoped>
    .copilot-context {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
        margin-bottom: var(--ks-spacing-2);
    }
</style>
