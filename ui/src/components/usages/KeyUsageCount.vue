<template>
    <KsSkeleton v-if="!count || count.status === 'queued' || count.status === 'loading'" animated :rows="0" class="key-usage-count__loading" data-test="key-usage-count-loading" />
    <KsText v-else-if="count.status === 'failed'" type="secondary" size="small" data-test="key-usage-count-failed">
        {{ $t("key_usages.count_failed") }}
    </KsText>
    <KsButton
        v-else
        link
        size="small"
        :type="count.flows === 0 ? undefined : 'primary'"
        data-test="key-usage-count"
        @click="emit('open')"
    >
        {{ label }}
    </KsButton>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"

    import {useKeyUsageCount} from "../../composables/useKeyUsageCount"
    import type {KeyTarget} from "../../composables/useKeyUsages"

    const props = defineProps<{
        target: KeyTarget;
    }>()

    const emit = defineEmits<{
        open: [];
    }>()

    const {t} = useI18n()

    const count = useKeyUsageCount(() => props.target)

    const label = computed(() => {
        const flows = count.value?.flows ?? 0
        if (flows === 0) return t("key_usages.count_none")
        return t("key_usages.count", {count: count.value?.truncated ? `${flows}+` : flows}, flows)
    })
</script>

<style scoped>
.key-usage-count__loading {
    width: 4.5rem;
}
</style>
