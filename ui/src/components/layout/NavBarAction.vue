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
    <!-- `to` is a declared prop, so it is not part of `$attrs` and has to be forwarded
         explicitly: without it the RouterLink tag resolves an undefined target, renders
         `href` for the current route (plus `aria-current="page"`) and stops being a button. -->
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

    const onClick = (event?: MouseEvent) => {
        // A `to` action renders as a real anchor, so the link owns navigation — including
        // modifier clicks and "open in new tab". Pushing here as well would navigate the
        // current tab on a cmd/middle click, so only push when the click came from
        // something that is not the link (the dropdown item's padding around it).
        const target = event?.target
        const fromLink = target instanceof Element && target.closest("a") !== null

        if (props.to && !fromLink) {
            router.push(props.to)
        }
        emit("click")
    }
</script>
