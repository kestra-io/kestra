<template>
    <div class="tabs-wrapper">
        <div class="tabs">
            <button
                v-for="element of tabs"
                :key="element.value"
                :class="{active: openTabs.includes(element.value)}"
                @click="setTabValue(element.value)"
            >
                <component class="tabs-icon" :is="element.button.icon" />
                {{ element.button.label }}
            </button>
        </div>
        <slot />
    </div>
</template>

<script setup lang="ts">
    defineProps<{
        tabs: {
            value: string;
            button: {
                icon: any;
                label: string;
            };
        }[],
        openTabs: string[];
    }>();

    const emit = defineEmits<{
        (e: "update:tabs", tabValue: string): void;
    }>();

    function setTabValue(tabValue: string) {
        emit("update:tabs", tabValue);
    }
</script>

<style lang="scss" scoped>
    @use "@kestra-io/ui-libs/src/scss/color-palette.scss" as colorPalette;
    .tabs-wrapper{
        display:flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid var(--ks-border-primary);
        background-image: linear-gradient(
                to right,
                colorPalette.$base-blue-400 0%,
                colorPalette.$base-blue-500 35%,
                rgba(colorPalette.$base-blue-500, 0) 55%,
                rgba(colorPalette.$base-blue-500, 0) 100%
            );
        .dark & {
            background-image: linear-gradient(
                to right,
                colorPalette.$base-blue-500 0%,
                colorPalette.$base-blue-700 35%,
                rgba(colorPalette.$base-blue-700, .1) 55%,
                rgba(colorPalette.$base-blue-700, 0) 100%
            );
        }
        background-size: 250% 100%;
        background-position: 100% 0;
        transition: background-position .2s;
    }
    .tabs{
        padding: .5rem 1rem;

        > button{
            background: none;
            border: none;
            padding: .5rem;
            font-size: .8rem;
            color: var(--ks-color-text-primary);
            display: inline-flex;
            align-items: center;
            justify-content: center;
            transition: opacity .2s;
            gap: .25rem;
            opacity: .5;

            &:hover{
                color: var(--ks-color-text-secondary);
                opacity: 1;
            }

            &.active{
                color: var(--ks-color-text-primary);
                opacity: 1;
            }
        }
    }

    .tabs-icon {
        margin-right: .25rem;
        vertical-align: bottom;
    }
</style>