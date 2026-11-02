<template>
    <KsRow tag="section" class="empty" justify="center">
        <KsCol :md="16" :lg="12">
            <KsEmptyState
                :title="resolvedTitle"
                :description="resolvedDescription"
                :image="images[type] ?? generic"
                :learnMore="resolvedLearnMore"
            >
                <template v-if="$slots.description || $slots.message" #description>
                    <slot name="description">
                        <slot name="message" />
                    </slot>
                </template>
                <template v-if="$slots.button || demoCta" #action>
                    <slot name="button">
                        <KsButton
                            v-if="demoCta"
                            type="primary"
                            tag="a"
                            target="_blank"
                            :href="contactSalesUrl"
                        >
                            {{ $t("demos.contact_sales") }}
                        </KsButton>
                    </slot>
                </template>
            </KsEmptyState>
            <slot name="content" />
        </KsCol>
    </KsRow>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsButton, KsCol, KsEmptyState, KsRow} from "@kestra-io/design-system"

    import generic from "../../../assets/empty_visuals/generic.svg"
    import {images} from "./images"
    import {links} from "./links"

    const props = withDefaults(
        defineProps<{
            type: string;
            title?: string;
            description?: string;
            learnMore?: string;
            demoCta?: boolean;
        }>(),
        {
            demoCta: false,
        },
    )

    const {t, te} = useI18n()

    const typeDocs = computed(() => links[props.type])

    const resolvedTitle = computed(() => {
        if (props.title) return props.title
        const key = `empty.${props.type}.title`
        return te(key) ? t(key) : undefined
    })

    const resolvedDescription = computed(() => {
        if (props.description) return props.description
        const key = `empty.${props.type}.content`
        return te(key) ? t(key) : undefined
    })

    const resolvedLearnMore = computed(() => props.learnMore ?? typeDocs.value)

    const contactSalesUrl = computed(
        () => `https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-${props.type}`,
    )
</script>
