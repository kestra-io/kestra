<template>
    <Teleport to="#topnav-title-slot">
        <slot v-if="$slots.title" name="title" />
    </Teleport>
    <Teleport to="#topnav-description-slot">
        <slot v-if="$slots.description" name="description" />
    </Teleport>
    <Teleport to="#topnav-actions-slot">
        <slot v-if="$slots.actions" name="actions" />
    </Teleport>
</template>

<script setup lang="ts">
    import {nextTick, onMounted, onUnmounted, useSlots, watchEffect} from "vue"
    import {useTopNavStore} from "../../stores/topNav"
    import type {KsBreadcrumbItem} from "@kestra-io/design-system"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        title: string;
        description?: string;
        breadcrumb?: KsBreadcrumbItem[];
        beta?: boolean;
    }>()

    const slots = useSlots()
    const store = useTopNavStore()
    const id = Symbol("topNav-instance")

    watchEffect(() => {
        store.title = props.title
        store.breadcrumb = props.breadcrumb ?? []
        store.description = props.description
        store.beta = !!props.beta
        store.hasTitleSlot = !!slots.title
        store.hasDescriptionSlot = !!slots.description
    })

    onMounted(() => {
        store.ownerId = id
    })

    onUnmounted(() => {
        // Defer the clear so an incoming TopNavBar (mounted in the same tick)
        // has a chance to claim ownership first. If it has, we leave the
        // store alone — the new instance owns the data now.
        nextTick(() => {
            if (store.ownerId === id) {
                store.ownerId = null
            }
        })
    })
</script>
