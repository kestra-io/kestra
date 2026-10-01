<template>
    <KsDropdownItem
        v-if="asItem"
        :icon="icon"
        v-bind="$attrs"
        @click="onClick"
    >
        <RouterLink
            v-if="props.to"
            class="nav-bar-action-link"
            :to="props.to"
            @auxclick="closeDropdown?.()"
            @contextmenu="closeDropdown?.()"
        >
            <slot>{{ label }}</slot>
        </RouterLink>
        <a
            v-else-if="props.href"
            class="nav-bar-action-link"
            :href="props.href"
            :download="props.download"
            @auxclick="closeDropdown?.()"
            @contextmenu="closeDropdown?.()"
        >
            <slot>{{ label }}</slot>
        </a>
        <slot v-else>{{ label }}</slot>
    </KsDropdownItem>
    <KsButton
        v-else
        :type="type ?? 'default'"
        :icon="icon"
        v-bind="{...$attrs, ...linkAttrs}"
        :tag="tag"
        :to="props.to"
        @click="onClick"
    >
        <slot>{{ label }}</slot>
    </KsButton>
</template>

<script setup lang="ts">
    import {computed, inject, type Component} from "vue"
    import {useRouter, type RouteLocationRaw, RouterLink} from "vue-router"
    import {asItemKey, closeDropdownKey} from "./navBarActionsContext"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        icon?: Component;
        type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | "";
        label?: string;
        to?: RouteLocationRaw;
        href?: string;
        download?: string;
    }>()

    const emit = defineEmits<{(e: "click"): void}>()

    const asItem = inject(asItemKey, false)
    const closeDropdown = inject(closeDropdownKey, undefined)

    const router = useRouter()

    const tag = computed(() => props.to ? RouterLink : props.href ? "a" : "button")

    // An `href: undefined` falling through onto RouterLink overrides the href it computes itself.
    const linkAttrs = computed(() => props.to || !props.href ? {} : {href: props.href, download: props.download})

    // Mirrors vue-router's guardEvent: the clicks RouterLink handles come back preventDefault-ed,
    // and it leaves a modifier click alone so the browser can open its new tab.
    const isLinkOwnedClick = (event?: MouseEvent) =>
        !!event && (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0)

    const onClick = (event?: MouseEvent) => {
        if (props.to && !isLinkOwnedClick(event)) {
            router.push(props.to)
        }
        closeDropdown?.()
        emit("click")
    }
</script>

<style scoped lang="scss">
    /* The link fills the dropdown row so a modifier click anywhere on it reaches the anchor. */
    .nav-bar-action-link {
        flex: 1;
    }
</style>
