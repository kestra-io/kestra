<template>
    <Action
        v-if="tab === 'flows'"
        :label="t('create_flow')"
        :to="{name: 'flows/create', query: {namespace}}"
    />

    <Action
        v-if="tab === 'kv'"
        :label="t('kv.add')"
        @click="store.commit('namespace/changeKVModalVisibility', true)"
    />
</template>

<script setup lang="ts">
    import {computed, Ref} from "vue";

    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import {useStore} from "vuex";

    import Action from "../../../components/namespaces/components/buttons/Action.vue";

    const route = useRoute();
    const {t} = useI18n({useScope: "global"});
    const store = useStore();

    const tab = computed(() => route.params?.tab);
    const namespace = computed(() => route.params?.id) as Ref<string>;
</script>
