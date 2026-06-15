<template>
    <KsPopover
        trigger="click"
        placement="bottom-start"
        width="fit-content"
        :visible="open"
        popperClass="log-value-actions"
        @update:visible="open = $event"
    >
        <template #reference>
            <span class="log-value-trigger" :class="{'is-action': filterable || to}" role="button" tabindex="0" @keydown.enter.prevent="open = true">
                <slot />
            </span>
        </template>
        <template #default>
            <div class="log-value-menu">
                <button v-if="filterable" type="button" class="log-value-action" @click="apply(false)">
                    <MagnifyPlusOutline :size="16" />
                    <span>{{ t("filter_for") }}</span>
                </button>
                <button v-if="filterable" type="button" class="log-value-action" @click="apply(true)">
                    <MagnifyMinusOutline :size="16" />
                    <span>{{ t("filter_out") }}</span>
                </button>
                <button type="button" class="log-value-action" @click="copyValue">
                    <ContentCopy :size="16" />
                    <span>{{ t("copy_value") }}</span>
                </button>
                <router-link v-if="to" :to="to" class="log-value-action" @click="open = false">
                    <OpenInNew :size="16" />
                    <span>{{ t("open_page") }}</span>
                </router-link>
            </div>
        </template>
    </KsPopover>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {useI18n} from "vue-i18n"
    import MagnifyPlusOutline from "vue-material-design-icons/MagnifyPlusOutline.vue"
    import MagnifyMinusOutline from "vue-material-design-icons/MagnifyMinusOutline.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import * as Utils from "../../utils/utils"

    const props = defineProps<{
        field: string
        value: string
        filterable?: boolean
        to?: any
    }>()

    const emit = defineEmits<{
        filter: [{field: string, value: string, negate: boolean}]
    }>()

    const {t} = useI18n()
    const open = ref(false)

    const apply = (negate: boolean) => {
        emit("filter", {field: props.field, value: props.value, negate})
        open.value = false
    }

    const copyValue = () => {
        Utils.copy(props.value)
        open.value = false
    }
</script>

<style scoped lang="scss">
    .log-value-trigger {
        cursor: pointer;
        color: var(--ks-text-secondary);

        &.is-action {
            color: var(--ks-text-link);
        }
    }

    .log-value-menu {
        display: flex;
        flex-direction: column;
        min-width: 160px;
    }

    .log-value-action {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: transparent;
        border: none;
        border-radius: var(--ks-radius-sm);
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        text-align: left;
        text-decoration: none;
        cursor: pointer;

        &:hover {
            background: var(--ks-bg-hover);
        }
    }
</style>
