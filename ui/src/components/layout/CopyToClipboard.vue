<template>
    <div class="clipboard">
        <ks-tooltip
            trigger="click"
            :content="$t('copied')"
            placement="left"
            :autoClose="2000"
        >
            <ks-button :icon="ContentCopy" type="default" :link @click="copyText">
                <span v-if="label">{{ label }}</span>
            </ks-button>
        </ks-tooltip>

        <slot name="right" />
    </div>
</template>

<script setup lang="ts">
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Utils from "../../utils/utils";

    const props = defineProps<{ text: string; label?: string, link?: boolean }>();

    const copyText = () => Utils.copy(props.text);
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

.clipboard {
    z-index: 1;
    position: absolute;
    top: $spacer;
    right: $spacer;
    display: inline-flex;
}
</style>
