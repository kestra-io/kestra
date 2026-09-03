<template>
    <div class="clipboard">
        <KsTooltip
            trigger="click"
            :content="$t('copied')"
            placement="left"
            :autoClose="2000"
        >
            <KsButton :icon="ContentCopy" type="default" :link @click="copyText" :aria-label="$t('copy_to_clipboard')" :class="{'copy-icon-button': !label}">
                <template v-if="label" #default>{{ label }}</template>
            </KsButton>
        </KsTooltip>

        <slot name="right" />
    </div>
</template>

<script setup lang="ts">
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import * as Utils from "../../utils/utils"

    const props = defineProps<{ text: string; label?: string, link?: boolean }>()

    const copyText = () => Utils.copy(props.text)
</script>

<style scoped lang="scss">

.clipboard {
    z-index: 1;
    position: absolute;
    top: 0.5rem;
    right: 0.5rem;
    display: inline-flex;
}

.copy-icon-button {
    width: 2rem;
    min-width: 2rem;
    height: 2rem;
    padding: 0;
}
</style>
