<template>
    <div v-if="showDocId" class="app-id-display-box">
        {{ text }}
        <ContentCopy
            class="copy-button"
            @click="Utils.copy(text)"
        />
    </div>
</template>

<script lang="ts" setup>
    import {computed} from "vue";
    import {useStore} from "vuex";
    import {useRoute} from "vue-router";
    import Utils from "../utils/utils";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";

    const store = useStore();
    const route = useRoute();

    const showDocId = computed(() => route.query["showDocId"] !== undefined);

    const text = computed(() => `docId: ${ store.state.doc.docId }`);
</script>

<style lang="scss" scoped>
.app-id-display-box {
    position: fixed;
    top: 0;
    left: 0;
    padding: .5rem 1rem;
    background-color: hotpink;
    color: black;
    z-index: 2000;
    display: flex;
    gap: .5rem;
    align-items: center;
}

.copy-button {
    cursor: pointer;
}
</style>
