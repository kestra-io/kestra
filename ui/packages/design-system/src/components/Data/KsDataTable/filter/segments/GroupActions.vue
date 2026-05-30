<template>
    <KsTooltip
        v-if="canUnwrap"
        placement="top"
        :content="$t('filter.unwrap_group')"
    >
        <KsButton
            link
            class="remove-group-btn"
            :icon="Ungroup"
            :aria-label="$t('filter.unwrap_group')"
            @click="$emit('unwrap')"
        />
    </KsTooltip>
    <KsTooltip
        v-if="canRemove"
        placement="top"
        :content="$t('filter.remove_condition_group')"
    >
        <KsButton
            link
            class="remove-group-btn"
            :class="{'filters-hidden': hidden}"
            :icon="Close"
            :aria-label="$t('filter.remove_condition_group')"
            @click="$emit('remove')"
        />
    </KsTooltip>
</template>

<script setup lang="ts">
    import KsTooltip from "../../../../Feedback/KsTooltip.vue"
    import {Close, Ungroup} from "../utils/icons"

    withDefaults(defineProps<{
        canRemove: boolean;
        canUnwrap?: boolean;
        hidden?: boolean;
    }>(), {
        canUnwrap: false,
        hidden: false,
    })

    defineEmits<{
        unwrap: [];
        remove: [];
    }>()
</script>

<style lang="scss" scoped>
.remove-group-btn {
    margin: 0 !important;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    flex-shrink: 0;
    padding: 0 0.25rem;

    &:hover {
        color: var(--ks-text-primary);
        text-decoration: underline;
    }

    &.filters-hidden {
        display: none;
    }
}
</style>
