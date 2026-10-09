<template>
    <div class="loop-scope-bar" data-test="loop-scope-bar">
        <KsText size="small" class="loop-scope-title">{{ $t("topology-graph.loop.scope") }}</KsText>
        <span class="loop-scope-trail" data-test="loop-scope-trail">
            <template v-if="entries.length">
                <KsTag v-for="entry in entries" :key="entry.taskId" size="small">
                    {{ entry.taskId }}: #{{ entry.number }}
                </KsTag>
            </template>
            <KsTag v-else size="small">{{ $t("topology-graph.loop.whole-execution") }}</KsTag>
        </span>
        <KsButton v-if="entries.length" link size="small" data-test="loop-scope-clear" @click="emit('clear')">
            {{ $t("topology-graph.loop.clear-scopes") }}
        </KsButton>
        <KsButton v-if="canJumpToFailure" link size="small" data-test="loop-scope-jump" @click="emit('jumpToFailure')">
            {{ $t("topology-graph.loop.jump-to-first-failure") }}
        </KsButton>
        <KsButton
            link
            size="small"
            :class="{'loop-scope-active': failuresOnly}"
            :aria-pressed="failuresOnly"
            data-test="loop-scope-failures-only"
            @click="emit('toggleFailuresOnly')"
        >
            {{ $t("topology-graph.loop.loops-with-failures") }}
        </KsButton>
    </div>
</template>

<script setup lang="ts">
    import type {LoopScopeEntry} from "../../utils/loopScope"

    defineProps<{
        entries: LoopScopeEntry[];
        canJumpToFailure: boolean;
        failuresOnly: boolean;
    }>()

    const emit = defineEmits<{
        clear: [];
        jumpToFailure: [];
        toggleFailuresOnly: [];
    }>()
</script>

<style scoped lang="scss">
    .loop-scope-bar {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        box-shadow: 0 2px 4px var(--ks-shadow-surface);
    }

    .loop-scope-title {
        font-weight: 600;
    }

    .loop-scope-trail {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .loop-scope-active {
        font-weight: 600;
        text-decoration: underline;
    }
</style>
