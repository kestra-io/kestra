<template>
    <div v-if="checks.length" class="failing-checks" data-test="failing-checks">
        <KsAlert v-if="isCollapsible" :type="summaryType" :data-type="summaryType" :title="$t('checks_failing', {count: checks.length}, checks.length)">
            <template #action>
                <KsButton size="small" :aria-expanded="expanded" data-test="failing-checks-toggle" @click="expanded = !expanded">
                    {{ $t(expanded ? 'collapse' : 'expand') }}
                </KsButton>
            </template>
        </KsAlert>
        <template v-if="!isCollapsible || expanded">
            <KsAlert v-for="(check, index) in checks" :key="index" :type="alertType(check.style)" :data-type="alertType(check.style)">
                {{ check.message }}
            </KsAlert>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import type {Check} from "../../stores/executions"

    type AlertType = "error" | "warning" | "info" | "success"

    const COLLAPSE_THRESHOLD = 3
    const SEVERITY: AlertType[] = ["error", "warning", "info", "success"]

    const props = defineProps<{checks: Check[]}>()

    const expanded = ref(false)

    const isCollapsible = computed(() => props.checks.length > COLLAPSE_THRESHOLD)
    watch(isCollapsible, () => {
        expanded.value = false
    })

    const summaryType = computed(() => {
        const types = props.checks.map(check => alertType(check.style))
        return SEVERITY.find(type => types.includes(type)) ?? "info"
    })

    function alertType(style: Check["style"]): AlertType {
        return (style?.toLowerCase() as AlertType | undefined) ?? "info"
    }
</script>

<style scoped>
    .failing-checks {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding-block: var(--ks-spacing-2);
        max-height: 40vh;
        overflow-y: auto;
    }
</style>
