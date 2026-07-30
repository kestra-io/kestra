<template>
    <KsDropdownItem
        v-if="asItem"
        :icon="icon"
        v-bind="$attrs"
        @click="onClick"
    >
        <RouterLink v-if="props.to" :to="props.to">
            <slot>{{ label }}</slot>
        </RouterLink>
        <slot v-else>{{ label }}</slot>
    </KsDropdownItem>
    <KsButton
        v-else
        :type="type ?? 'default'"
        :icon="icon"
        v-bind="$attrs"
        :tag="props.to ? RouterLink : 'button'"
        :to="props.to"
        @click="onClick"
    >
        <slot>{{ label }}</slot>
    </KsButton>
</template>

<script setup lang="ts">
    import {inject, type Component} from "vue"
    import {useRouter, type RouteLocationRaw, RouterLink} from "vue-router"
    import {asItemKey} from "./navBarActionsContext"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        icon?: Component;
        type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | "";
        label?: string;
        to?: RouteLocationRaw;
    }>()

    const emit = defineEmits<{(e: "click"): void}>()

    const asItem = inject(asItemKey, false)

    const router = useRouter()

    const onClick = () => {
        if (props.to) {
            router.push(props.to)
        }
        emit("click")
    }
</script>
