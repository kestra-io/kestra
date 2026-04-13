<template>
    <span v-if="description">
        <a @click="open()">
            <HelpCircle
                title=""
                :id="'tooltip-desc-' + id"
            />
        </a>

        <ks-drawer
            v-if="isOpen"
            v-model="isOpen"
            :title="title"
        >
            <KsMarkdown class="markdown-tooltip" :content="description" />
        </ks-drawer>
    </span>
</template>

<script setup lang="ts">
    import {ref} from "vue";
    import HelpCircle from "vue-material-design-icons/HelpCircle.vue";
    import {KsMarkdown} from "@kestra-io/ui-design-system";

    defineProps({
        id: {
            type: String,
            required: true
        },
        title: {
            type: String,
            default: ""
        },
        description: {
            type: String,
            default: ""
        }
    });

    const isOpen = ref(false);

    const open = (): void => {
        isOpen.value = true;
    };
</script>

<style scoped lang="scss">
    a {
        cursor: pointer;
    }
</style>