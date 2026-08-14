<template>
    <div class="node-details">
        <KsButton size="small" class="details-back" @click="emit('close')">
            <ArrowLeft />
            {{ $t("back") }}
        </KsButton>

        <KsText size="large" tag="h3" class="details-title">
            {{ shortName }}
        </KsText>

        <Link :node :subtype />

        <dl class="details-grid">
            <template v-for="row in rows" :key="row.label">
                <dt>
                    <KsText size="small" class="details-label">
                        {{ row.label }}
                    </KsText>
                </dt>
                <dd>
                    <KsDateAgo v-if="row.date" :inverted="true" :date="row.value" />
                    <KsText v-else size="small">
                        {{ row.value }}
                    </KsText>
                </dd>
            </template>
        </dl>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"

    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue"

    import Link from "./Link.vue"
    import {ASSET} from "../utils/types"
    import type {Types, Node} from "../utils/types"

    const props = defineProps<{
        node: Node;
        subtype: Types;
    }>()

    const emit = defineEmits<{close: []}>()

    const {t} = useI18n({useScope: "global"})

    const shortName = computed(() => props.node.flow.split(".").pop() || props.node.flow)

    const rows = computed(() => {
        const metadata = props.node.metadata as {subtype: Types; kind?: string; system?: string; updated?: string}

        return [
            {label: t("type"), value: metadata.subtype === ASSET ? t("asset") : t("flow")},
            {label: t("dependency.dag.kind"), value: metadata.kind},
            {label: t("dependency.dag.system"), value: metadata.system},
            {label: t("namespace"), value: props.node.namespace},
            {label: t("dependency.dag.last_update"), value: metadata.updated, date: true},
        ].filter((row) => Boolean(row.value))
    })
</script>

<style scoped lang="scss">
.node-details {
    display: flex;
    flex-direction: column;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-4);
}

.details-back {
    align-self: flex-start;
}

.details-title {
    margin: 0;
    word-break: break-all;
}

.details-grid {
    display: grid;
    grid-template-columns: auto 1fr;
    gap: var(--ks-spacing-2) var(--ks-spacing-4);
    margin: 0;
    padding-top: var(--ks-spacing-2);
    border-top: 1px solid var(--ks-border-subtle);
}

.details-label {
    color: var(--ks-text-secondary);
}

.details-grid dd {
    margin: 0;
    word-break: break-all;
}
</style>
