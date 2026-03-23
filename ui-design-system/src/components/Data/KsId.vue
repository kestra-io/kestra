<template>
    <ks-tooltip v-if="hasTooltip" transition="" placement="top" effect="light">
        <template #content>
            <code>{{ value }}</code>
        </template>
        <code :id="uid" @click="emit('click')" class="ks-id text-nowrap" :class="{'ks-id--clickable': hasClickListener}">
            {{ transformValue }}
        </code>
    </ks-tooltip>
    <code v-else :id="uid" class="ks-id text-nowrap" @click="emit('click')">
        {{ transformValue }}
    </code>
</template>

<script setup lang="ts">
    import {computed, useAttrs, useId} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"

    const props = defineProps<{
        value?: string
        shrink?: boolean
        size?: number
    }>()

    const uid = useId()

    const emit = defineEmits<{
        click: []
    }>()

    const attrs = useAttrs()

    const shrink = computed(() => props.shrink ?? true)
    const size = computed(() => props.size ?? 8)

    const hasTooltip = computed(() => {
        return shrink.value && props.value && props.value.length > size.value
    })

    const hasClickListener = computed(() => Boolean(attrs.onClick))

    const transformValue = computed(() => {
        if (!props.value) {
            return ""
        }

        if (!shrink.value) {
            return props.value
        }

        return props.value.toString().substring(0, size.value) +
            (props.value.length > size.value && size.value !== 8 ? "…" : "")
    })
</script>

<style scoped lang="scss">
    code.ks-id--clickable {
        cursor: pointer;
        &:hover {
            color: rgba(var(--bs-link-color-rgb), var(--bs-link-opacity, 1));
        }
    }
</style>
