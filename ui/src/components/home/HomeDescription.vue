<template>
    <el-card
        class="description"
        v-if="flow?.description"
        :header="$t('description')"
    >
        <div>
            <markdown class="markdown-tooltip" v-if="flow.description" :source="flow.description" />
        </div>
    </el-card>
</template>
<script>
    import Markdown from "../layout/Markdown.vue";
    import {mapGetters} from "vuex";

    export default {
        components: {
            Markdown
        },
        computed: {
            ...mapGetters("flow", ["flow"]),
        }
    };
</script>

<style lang="scss" scoped>
    .description {
        font-size: var(--font-size-sm);
        .card-body > div {
            max-height: 300px;
            padding-right: 0.25rem;
            overflow: auto;
        }
    }
</style>