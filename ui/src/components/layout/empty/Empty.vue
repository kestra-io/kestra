<template>
    <section class="row empty">
        <div class="col-sm-12 col-md-8 offset-md-2 col-lg-6 offset-lg-3">
            <div class="d-flex flex-column align-items-center gap-2 px-2">
                <img :src>

                <h2>{{ $t(`empty.${props.type}.title`) }}</h2>
                <p v-html="$t(`empty.${props.type}.content`)" />

                <slot name="button" />
            </div>
            <slot name="content" />
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    
    import {images} from "./images";

    const props = defineProps({type: {type: String, required: true}});

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

    p {
        width: 100%;
        max-width: 553px;
        font-size: 1rem;
        color: var(--ks-content-secondary);
        line-height: 1.5rem;
    }

    img {
        max-width: 222px;
    }
}
</style>
