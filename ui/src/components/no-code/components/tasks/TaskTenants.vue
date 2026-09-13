<template>
    <KsSelect
        v-model="modelValue"
        multiple
        filterable
        allowCreate
        clearable
        :placeholder="$t('tenant.names')"
    >
        <template #tag>
            <KsTag
                v-for="value in visibleTags"
                :key="value"
                closable
                type="info"
                @close="modelValue = modelValue.filter(v => v !== value)"
            >
                {{ value }}
            </KsTag>
            <KsTooltip v-if="hiddenTags.length > 0" placement="top">
                <template #content>
                    <div v-for="value in hiddenTags" :key="value">{{ value }}</div>
                </template>
                <KsTag>
                    +{{ hiddenTags.length }}
                </KsTag>
            </KsTooltip>
        </template>
        <KsOption
            v-for="tenant in tenants"
            :key="tenant.id"
            :label="tenant.name ?? tenant.id"
            :value="tenant.id"
        />
    </KsSelect>
</template>

<script lang="ts" setup>
    import {computed, inject} from "vue"
    import {TENANTS_INJECTION_KEY} from "../../injectionKeys"

    const modelValue = defineModel<string[]>({default: () => []})
    const tenants = inject(TENANTS_INJECTION_KEY, computed(() => []))

    const visibleTags = computed(() => modelValue.value.slice(0, 3))
    const hiddenTags = computed(() => modelValue.value.slice(3))
</script>
