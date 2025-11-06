
<template>
    <h2 class="big-title">
        {{ title }}
    </h2>
    <div class="big-card-grid">
        <ContextDocsLink :href="item.path" class="big-card" v-for="item in protectedNavigation" :key="item.path">
            <h4 class="card-title">
                {{ item.title }}
            </h4>
            <p class="card-text">
                {{ item.description }}
            </p>
        </ContextDocsLink>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useDocStore} from "../../stores/doc";
    import ContextDocsLink from "../docs/ContextDocsLink.vue";

    const docStore = useDocStore();

    const props = defineProps<{
        directory: string
        title: string
    }>()

    let navigation = await docStore.children(props.directory) as Record<string, any>;

    // avoid null values in navigation
    const protectedNavigation = computed(() => {
        return Object.entries(navigation ?? {})
            .filter(a => a[1] && a[1].title && a[1].description && a[0] !== props.directory.slice(1))
            .map(a => ({
                path: a[0].slice(5),
                title: a[1].title,
                description: a[1].description,
            }))
    })
</script>

<style scoped lang="scss">
.big-card-grid{
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 1rem;
}

h2.big-title {
    padding: 0;
    font-size: 1.5rem;
    border: none;
    margin-top: 3rem;
    margin-bottom: 1rem;
    font-weight: 400;
}

.big-card{
    border-radius: 0.5rem;
    text-decoration: none;
    background: linear-gradient(180deg, #3a4051 0%, #272a36 100%);
    color: white;
    border: 1px solid #21242E;
    border-image-source: linear-gradient(180deg, #2B313E 0%, #131725 100%);
    transition: all 0.3s;
    padding: 1rem;
    h4.card-title {
        padding-top: 0;
        font-size: 1.4rem;
        font-weight: normal;
    }
    p.card-text{
        font-size: .875rem;
        line-height: 1.5em;
    }
    &:hover{
        background: linear-gradient(180deg, rgba(#3a4051, .9) 0%, rgba(#272a36,.9) 100%), #9ca4ce;
    }
}
</style>
