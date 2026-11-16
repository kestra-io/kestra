<template>
    <section class="key-usages" data-test="key-usages">
        <div class="key-usages__header">
            <h3 class="key-usages__title">
                {{ $t("key_usages.title") }}
            </h3>
            <router-link :to="sourceSearchTarget" class="key-usages__link" data-test="key-usages-source-search">
                {{ $t("key_usages.open_source_search") }}
            </router-link>
        </div>

        <KsSkeleton v-if="status === 'loading'" animated :rows="2" data-test="key-usages-loading" />

        <KsAlert
            v-else-if="status === 'failed'"
            type="error"
            :closable="false"
            :title="$t('key_usages.error')"
            data-test="key-usages-error"
        >
            {{ errorMessage }}
        </KsAlert>

        <template v-else-if="status === 'done'">
            <p v-if="usages.length === 0" class="key-usages__note" data-test="key-usages-empty">
                {{ $t("key_usages.empty") }}
            </p>
            <ul v-else class="key-usages__list">
                <li v-for="usage in usages" :key="`${usage.namespace}/${usage.id}:${usage.line}`" class="key-usages__row" data-test="key-usages-row">
                    <router-link :to="flowTarget(usage)" class="key-usages__flow" data-test="key-usages-flow">
                        {{ usage.namespace }} / {{ usage.id }}
                    </router-link>
                    <span class="key-usages__line">{{ $t("key_usages.line", {line: usage.line}) }}</span>
                    <code class="key-usages__snippet"><HighlightedText :segments="highlight(usage.snippet)" /></code>
                </li>
            </ul>
            <p v-if="truncated" class="key-usages__note" data-test="key-usages-truncated">
                {{ $t("key_usages.truncated", {shown, total}) }}
            </p>
        </template>

        <p class="key-usages__note" data-test="key-usages-caveat">
            {{ $t("key_usages.caveat") }}
        </p>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute} from "vue-router"

    import HighlightedText from "../layout/HighlightedText.vue"
    import {useKeyUsages, type KeyTarget, type KeyUsage} from "../../composables/useKeyUsages"
    import {buildHighlightSegments} from "../../utils/crossResourceSearch"

    const props = defineProps<{
        target: KeyTarget;
    }>()

    const route = useRoute()

    const {status, usages, shown, total, truncated, errorMessage, pattern} = useKeyUsages(() => props.target)

    const sourceSearchTarget = computed(() => ({
        name: "flows/search",
        params: {tenant: route.params.tenant},
        query: {q: pattern.value, regex: "true", types: "flows"},
    }))

    const flowTarget = (usage: KeyUsage) => ({
        name: "flows/update/edit",
        params: {tenant: route.params.tenant, namespace: usage.namespace, id: usage.id},
    })

    const highlight = (snippet: string) => buildHighlightSegments(snippet, props.target.key, true)
</script>

<style scoped>
.key-usages {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
    margin-top: var(--ks-spacing-6);
}

.key-usages__header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: var(--ks-spacing-3);
}

.key-usages__title {
    margin: 0;
    font-size: var(--ks-font-size-md);
    font-weight: var(--ks-font-weight-semibold);
    color: var(--ks-text-primary);
}

.key-usages__link,
.key-usages__flow {
    color: var(--ks-text-link);
    text-decoration: none;

    &:hover {
        text-decoration: underline;
    }
}

.key-usages__link {
    font-size: var(--ks-font-size-sm);
}

.key-usages__note {
    margin: 0;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-muted);
}

.key-usages__list {
    display: flex;
    flex-direction: column;
    margin: 0;
    padding: 0;
    list-style: none;
    border: 1px solid var(--ks-border-subtle);
    border-radius: var(--ks-radius-base);
}

.key-usages__row {
    display: grid;
    grid-template-columns: 1fr auto;
    gap: var(--ks-spacing-1) var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);

    & + & {
        border-top: 1px solid var(--ks-border-subtle);
    }
}

.key-usages__flow {
    min-width: 0;
    overflow-wrap: anywhere;
    font-size: var(--ks-font-size-sm);
}

.key-usages__line {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-muted);
}

.key-usages__snippet {
    grid-column: 1 / -1;
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    white-space: pre-wrap;
    overflow-wrap: anywhere;
    color: var(--ks-text-dim);

    mark {
        background-color: var(--ks-status-background-warning);
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-xs);
        padding: 0 var(--ks-spacing-1);
    }
}
</style>
