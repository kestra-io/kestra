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
        <a v-else-if="props.href" :href="props.href" :download="props.download">
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
    import {asItemKey} from "./navBarActionsContext"

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

    const router = useRouter()

    const tag = computed(() => props.to ? RouterLink : props.href ? "a" : "button")

    // An `href: undefined` falling through onto RouterLink overrides the href it computes itself.
    const linkAttrs = computed(() => props.to || !props.href ? {} : {href: props.href, download: props.download})

    const onClick = () => {
        if (props.to) {
            router.push(props.to)
        }
        emit("click")
    }
</script>
