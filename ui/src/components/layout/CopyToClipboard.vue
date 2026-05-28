<template>
    <div class="clipboard">
        <KsTooltip
            trigger="click"
            :content="$t('copied')"
            placement="left"
            :autoClose="2000"
        >
            <KsButton :icon="ContentCopy" type="default" :link @click="copyText">
                <span v-if="label">{{ label }}</span>
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
    top: 1rem;
    right: 1rem;
    display: inline-flex;
}
</style>
