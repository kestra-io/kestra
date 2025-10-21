<template>
    <section class="empty">
        <div class="row">
            <div class="col-sm-12 col-md-8 offset-md-2 col-lg-6 offset-lg-3">
                <img :src="src" :alt="t(`empty.${props.type}.title`)" class="empty-visual">

                <h2>{{ t(`empty.${props.type}.title`) }}</h2>
                <p class="empty-description" v-html="t(`empty.${props.type}.content`)" />

                <slot name="button" />
                <slot name="content" />
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps({type: {type: String, required: true}});

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {images} from "./images";
    const src = computed((): string => images[props.type]);
</script>

<style scoped lang="scss">
.empty {
    width: 100%;
    height: 100%;
    padding: 3rem 0;
    text-align: center;
    background: top center / auto no-repeat
        url("./assets/background/light.svg#file");

    html.dark & {
        background-image: url("./assets/background/dark.svg#file");
    }

    h2 {
        font-size: 1.5rem;
        color: var(--ks-content-primary);
        font-weight: 600;
    }

    .empty-visual {
        max-width: clamp(180px, 20vw, 240px);
        width: 100%;
        height: auto;
        margin: 0 auto 1rem;
    }

    .empty-description {
        width: 100%;
        max-width: 553px;
        font-size: 1rem;
        color: var(--ks-content-secondary);
        line-height: 1.5rem;
        margin: 0 auto;
        text-align: center;
    }
}
</style>
