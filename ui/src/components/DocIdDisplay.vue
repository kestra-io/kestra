<template>
    <button v-if="showDocId" @click="Utils.copy(text);clipboardSuccess()" class="app-id-display-box">
        {{ text }}
        <CheckCircle
            v-if="copied"
        />
        <ContentCopy
            v-else
            class="copy-button"
        />
    </button>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useRoute} from "vue-router";
    import {useDocStore} from "../stores/doc";
    import Utils from "../utils/utils";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue";

    const docStore = useDocStore();
    const route = useRoute();

    const showDocId = computed(() => route.query["showDocId"] !== undefined);

    const text = computed(() => `docId: ${docStore.docId}`);

    const copied = ref(false);

    function clipboardSuccess() {
        copied.value = true;
        setTimeout(() => {
            copied.value = false;
        }, 2000);
    }
</script>

<style scoped lang="scss">
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
    border: none;
    border-bottom-right-radius: .5rem;
    &:hover {
        background-color: pink;
    }
}
</style>
