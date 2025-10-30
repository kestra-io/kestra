<template>
    <div class="row-link" @click.prevent="$emit('click')" :class="{clickable: clickable}">
        <TaskIcon
            v-if="icon"
            class="icon"
            :onlyIcon="true"
            :cls="icon"
            :icons="icons"
        />
        <span class="text">{{ text }}</span>
        <ChevronRight />
    </div>
</template>

<script setup lang="ts">
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";
    import {TaskIcon} from "@kestra-io/ui-libs";

    interface Props {
        icon?: string;
        text: string;
        icons?: Record<string, string>;
        clickable?: boolean;
    }

    withDefaults(defineProps<Props>(), {
        icon: undefined,
        icons: undefined,
        clickable: true
    });

    defineEmits<{
        click: [];
    }>();
</script>

<style scoped lang="scss">
    .row-link {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 0.5rem 1.5rem;
        border-top: 1px solid var(--ks-border-primary);
        background: var(--ks-background-primary);

        &:last-child {
            border-bottom: 1px solid var(--ks-border-primary);
        }

        &.clickable {
            cursor: pointer;
        }

        .icon {
            height: 2.5rem;
            width: 2.5rem;
            flex-shrink: 0;
        }

        .text {
            flex: 1;
            color: var(--ks-content-primary);
            text-transform: capitalize;
            font-size: 1rem;
        }

        .chevron {
            font-size: 1.5rem;
            color: var(--ks-content-tertiary);
        }
    }
</style>
