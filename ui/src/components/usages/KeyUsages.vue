<template>
    <section class="key-usages" data-test="key-usages">
        <div class="key-usages__header">
            <KsText tag="h3" class="key-usages__title" data-test="key-usages-title">
                {{ title }}
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
                <li v-for="usage in visibleUsages" :key="`${usage.namespace}/${usage.id}:${usage.line}`" class="key-usages__row" data-test="key-usages-row">
                    <router-link :to="flowTarget(usage)" class="key-usages__flow" data-test="key-usages-flow">
                        <FileTreeOutline class="key-usages__icon" />
                        <span class="key-usages__name">{{ usage.namespace }} / <strong>{{ usage.id }}</strong></span>
                    </router-link>
                    <KsText type="secondary" size="small" class="key-usages__line">{{ $t("key_usages.line", {line: usage.line}) }}</KsText>
                    <code v-if="!compact" class="key-usages__snippet"><template v-for="(segment, index) in highlight(usage.snippet)" :key="index"><mark v-if="segment.matched">{{ segment.text }}</mark><template v-else>{{ segment.text }}</template></template></code>
                </li>
                <li v-if="hiddenCount > 0" class="key-usages__more">
                    <KsButton link data-test="key-usages-show-more" @click="expanded = true">
                        {{ $t("key_usages.show_more", {count: hiddenCount}, hiddenCount) }}
                    </KsButton>
                </li>
            </ul>
            <KsText v-if="truncated" tag="p" type="secondary" size="small" class="key-usages__note" data-test="key-usages-truncated">
                {{ $t("key_usages.incomplete") }}
            </KsText>
            <KsText v-if="target.kind === 'secret' && usages.length > 0" tag="p" type="secondary" size="small" class="key-usages__note" data-test="key-usages-rotation">
                {{ $t("key_usages.rotation", {count: usages.length}, usages.length) }}
            </KsText>
        </template>

        <KsText tag="p" type="secondary" size="small" class="key-usages__note" data-test="key-usages-caveat">
            {{ $t("key_usages.caveat") }}
        </KsText>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"

    import {useKeyUsages, type KeyTarget, type KeyUsage} from "../../composables/useKeyUsages"
    import {buildHighlightSegments} from "../../utils/crossResourceSearch"

    const COLLAPSED_COUNT = 3

    const props = defineProps<{
        target: KeyTarget;
        compact?: boolean;
    }>()

    const route = useRoute()
    const {t} = useI18n()

    const {status, usages, truncated, errorMessage, pattern} = useKeyUsages(() => props.target)

    const expanded = ref(false)
    watch(() => `${props.target.kind}:${props.target.namespace}:${props.target.key}`, () => expanded.value = false)

    const visibleUsages = computed(() => expanded.value ? usages.value : usages.value.slice(0, COLLAPSED_COUNT))
    const hiddenCount = computed(() => usages.value.length - visibleUsages.value.length)

    const title = computed(() => {
        const count = usages.value.length
        if (status.value !== "done" || count === 0) return t("key_usages.title")
        return t("key_usages.title_count", {count: truncated.value ? `${count}+` : count}, count)
    })

    const sourceSearchTarget = computed(() => ({
        name: "flows/search",
        params: {tenant: route.params.tenant},
        query: {q: pattern.value, regex: "true", caseSensitive: "true", types: "flows"},
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

.key-usages__link {
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-link);
    text-decoration: none;

    &:hover {
        text-decoration: underline;
    }
}

.key-usages__note {
    align-self: stretch;
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
    align-items: center;
    gap: var(--ks-spacing-1) var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
}

.key-usages__row + .key-usages__row,
.key-usages__more {
    border-top: 1px solid var(--ks-border-subtle);
}

.key-usages__flow {
    display: flex;
    align-items: center;
    gap: var(--ks-spacing-2);
    min-width: 0;
    font-size: var(--ks-font-size-sm);
    color: var(--ks-text-primary);
    text-decoration: none;

    &:hover .key-usages__name {
        color: var(--ks-text-link);
        text-decoration: underline;
    }
}

.key-usages__icon {
    display: inline-flex;
    flex-shrink: 0;
    color: var(--ks-icon-muted);
}

.key-usages__name {
    min-width: 0;
    overflow-wrap: anywhere;

    strong {
        font-weight: var(--ks-font-weight-semibold);
    }
}

.key-usages__line {
    font-family: var(--ks-font-family-mono);
}

.key-usages__more {
    display: flex;
    justify-content: center;
    padding: var(--ks-spacing-1);
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
    }
}
</style>
