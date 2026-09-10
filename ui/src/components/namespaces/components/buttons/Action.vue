<template>
    <ul>
        <li>
            <KsButton
                :type="props.type || 'primary'"
                :icon="props.icon ?? Plus"
                @click="emits('click')"
                :tag="to ? 'router-link' : 'button'"
                :to
                :loading="props.loading ?? false"
                :disabled="props.disabled ?? false"
            >
                {{ props.label }}
            </KsButton>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import type {Component} from "vue"
    import type {RouteLocationRaw} from "vue-router"

    import Plus from "vue-material-design-icons/Plus.vue"

    const emits = defineEmits<{ (event: "click"): void }>()
    const props = defineProps<{
        label: string;
        icon?: Component;
        type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | "";
        to?: RouteLocationRaw;
        loading?: boolean;
        disabled?: boolean;
    }>()
</script>
