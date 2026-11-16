<template>
    <section class="key-usages" data-test="key-usages">
        <div class="key-usages__header">
            <KsText tag="h3" class="key-usages__title">
                {{ $t("key_usages.title") }}
            </KsText>
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
            <KsText v-if="usages.length === 0" tag="p" type="secondary" size="small" class="key-usages__note" data-test="key-usages-empty">
                {{ $t("key_usages.empty") }}
            </KsText>
            <ul v-else class="key-usages__list">
                <li v-for="usage in usages" :key="`${usage.namespace}/${usage.id}:${usage.line}`" class="key-usages__row" data-test="key-usages-row">
                    <router-link :to="flowTarget(usage)" class="key-usages__flow" data-test="key-usages-flow">
                        {{ usage.namespace }} / {{ usage.id }}
                    </router-link>
                    <KsText type="secondary" size="small" class="key-usages__line">{{ $t("key_usages.line", {line: usage.line}) }}</KsText>
                    <code class="key-usages__snippet"><template v-for="(segment, index) in highlight(usage.snippet)" :key="index"><mark v-if="segment.matched">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template></code>
                </li>
            </ul>
            <KsText v-if="truncated" tag="p" type="secondary" size="small" class="key-usages__note" data-test="key-usages-truncated">
                {{ $t("key_usages.truncated", {shown, total}) }}
            </KsText>
        </template>

        <KsText tag="p" type="secondary" size="small" class="key-usages__note" data-test="key-usages-caveat">
            {{ $t("key_usages.caveat") }}
        </KsText>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute} from "vue-router"

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
    font-weight: var(--ks-font-weight-semibold);
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
