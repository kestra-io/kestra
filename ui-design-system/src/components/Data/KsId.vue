<template>
    <ks-tooltip v-if="hasTooltip" placement="top">
        <template #content>
            <code>{{ value }}</code>
        </template>
        <code :id="uid" @click="emit('click')" class="ks-id text-nowrap" :class="{'ks-id--clickable': hasClickListener}">
            {{ transformValue }}
        </code>
    </ks-tooltip>
    <code v-else :id="uid" class="ks-id text-nowrap" :class="{'ks-id--clickable': hasClickListener}" @click="emit('click')">
        {{ transformValue }}
    </code>
</template>

<script setup lang="ts">
    import {computed, useAttrs, useId, getCurrentInstance} from "vue"
    import KsTooltip from "../Feedback/KsTooltip.vue"

    const props = withDefaults(defineProps<{
        value?: string
        shrink?: boolean
        size?: number
    }>(), {shrink: true})

    const uid = useId()

    const emit = defineEmits<{
        click: []
    }>()

    const attrs = useAttrs()

    const size = computed(() => props.size ?? 8)

    const hasTooltip = computed(() => {
        return props.shrink && props.value && props.value.length > size.value
    })

    const hasClickListener = computed(() => Boolean(getCurrentInstance()?.vnode.props?.onClick))

    const transformValue = computed(() => {
        if (!props.value) {
            return ""
        }

        if (!props.shrink) {
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
            text-decoration: underline;
        }
    }
</style>
