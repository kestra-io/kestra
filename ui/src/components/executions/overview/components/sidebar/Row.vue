<template>
    <KsRow v-for="(row, rIdx) in props.rows" :key="rIdx">
        <KsCol :span="14" class="label">
            <component :is="row.icon" />
            <KsText truncated>
                {{ row.label }}
            </KsText>
        </KsCol>

        <KsCol v-if="$slots.value" :span="10" class="value">
            <slot name="value" />
        </KsCol>
        <KsCol v-else-if="row.value" :span="10" class="value">
            <KsText truncated>
                <router-link v-if="row.to" :to="row.to">
                    {{ row.value }}
                </router-link>

                <template v-else>
                    {{ row.value }}
                </template>
            </KsText>
        </KsCol>

        <KsCol v-if="$slots.action" :span="10">
            <slot name="action" />
        </KsCol>
    </KsRow>
</template>

<script setup lang="ts">
    import type {Component} from "vue"

    import {RouteLocationRaw} from "vue-router"

    const props = defineProps<{
        rows: {
            icon: Component;
            label: string;
            value?: string | number | Date;
            to?: RouteLocationRaw;
        }[];
    }>()
</script>

<style scoped lang="scss">

.kel-row:not(:last-child) {
    margin-bottom: calc(1rem / 2);
}

.kel-row {
    & :deep(.kel-text),
    & :deep(.kel-button) {
        font-size: var(--ks-font-size-sm);
    }

    & :deep(.label) {
        display: flex;
        align-items: center;

        & span.material-design-icon {
            margin-right: calc(1rem / 2);
        }

        & .kel-text {
            color: var(--ks-content-secondary);
        }
    }

    & :deep(.value) {
        display: flex;
        align-items: center;
        justify-content: end;
    }
}
</style>
