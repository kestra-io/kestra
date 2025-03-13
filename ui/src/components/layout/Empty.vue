<template>
    <section class="row empty">
        <div class="col-sm-12 col-md-8 offset-md-2 col-lg-6 offset-lg-3">
            <img :src>

            <h2>{{ t(`empty.${props.type}.title`) }}</h2>
            <p v-html="t(`empty.${props.type}.content`)" />

            <slot name="button" />
            <slot name="content" />
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";

    const props = defineProps({type: {type: String, required: true}});

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const LOCATION: string = `../../assets/empty/visuals/${props.type}.png`;
    const src = computed((): string => new URL(LOCATION, import.meta.url).href);
</script>

<style scoped lang="scss">
.empty {
    width: 100%;
    height: 100%;
    padding: 3rem 0;
    text-align: center;
    background: top center / auto no-repeat url("../../assets/empty/background/light.svg#file");

    html.dark & {
        background-image: url("../../assets/empty/background/dark.svg#file");
    }

    h2 {
        font-size: 1.5rem;
        color: var(--ks-content-primary);
        font-weight: bold;
    }

    p {
        font-size: 1rem;
        color: var(--ks-content-secondary);
    }
}
</style>
