<template>
    <ul>
        <li>
            <KsButton
                :type="(props.type || 'primary') as any"
                :icon="props.icon ?? Plus"
                @click="emits('click')"
                :tag="to ? 'router-link' : 'button'"
                :to
                :loading="props.loading ?? false"
            >
                {{ props.label }}
            </KsButton>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import type {Component} from "vue"

    import Plus from "vue-material-design-icons/Plus.vue"

    const emits = defineEmits<{ (event: "click"): void }>()
    const props = defineProps<{
        label: string;
        icon?: Component;
        type?: string;
        to?: Record<string, any>;
        loading?: boolean;
    }>()
</script>
