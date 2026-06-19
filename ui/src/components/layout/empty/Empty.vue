<template>
    <section class="row empty">
        <div class="col-sm-12 col-md-8 offset-md-2 col-lg-6 offset-lg-3">
            <KsEmptyState
                :title="resolvedTitle"
                :description="resolvedDescription"
                :image="artwork"
                :video="resolvedVideo"
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
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsButton, KsEmptyState} from "@kestra-io/design-system"

    import artwork from "../../../assets/empty_visuals/generic.svg"
    import {links} from "./links"

    const props = withDefaults(
        defineProps<{
            type: string;
            title?: string;
            description?: string;
            video?: string;
            learnMore?: string;
            demoCta?: boolean;
        }>(),
        {
            demoCta: false,
        },
    )

    const {t, te} = useI18n()

    const typeLinks = computed(() => links[props.type])

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

    const resolvedVideo = computed(() => props.video ?? typeLinks.value?.video)

    const resolvedLearnMore = computed(() => {
        if (props.learnMore !== undefined) return props.learnMore
        if (props.demoCta) return "#"
        return typeLinks.value?.learnMore
    })

    const contactSalesUrl = computed(
        () => `https://kestra.io/demo?utm_source=app&utm_medium=referral&utm_campaign=demo-${props.type}`,
    )
</script>
