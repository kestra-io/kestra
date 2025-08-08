<template>
    <Action
        v-if="deleted"
        type="default"
        :icon="BackupRestore"
        :label="t('restore')"
        @click="restoreFlow"
    />
    <Action
        v-if="canEdit && !deleted && tab !== 'edit'"
        type="default"
        :icon="Pencil"
        :label="t('edit flow')"
        @click="editFlow"
    />
    <trigger-flow
        v-if="flow && !deleted && tab !== 'apps' && canExecute"
        type="primary"
        :flow-id="flow?.id"
        :namespace="flow?.namespace"
        :flow-source="flow?.source"
    />
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import {useStore} from "vuex";
    import {useCoreStore} from "../../../stores/core";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue";
    import Action from "../../../components/namespaces/components/buttons/Action.vue";
    import TriggerFlow from "../../../components/flows/TriggerFlow.vue";
    import permission from "../../../models/permission";
    import action from "../../../models/action";

    const {t} = useI18n();

    const store = useStore();
    const coreStore = useCoreStore();
    const router = useRouter();
    const route = useRoute();

    const flow = computed(() => store.state.flow.flow);
    const user = computed(() => store.state.auth.user);
    const deleted = computed(() => flow.value?.deleted || false);
    const tab = computed(() => route.params?.tab as string);

    const canExecute = computed(() =>
        flow.value && user.value?.isAllowed(permission.EXECUTION, action.CREATE, flow.value.namespace)
    );

    const canEdit = computed(() =>
        user.value?.isAllowed(permission.FLOW, action.UPDATE, flow.value?.namespace)
    );

    const editFlow = () => {
        router.push({
            name: "flows/update",
            params: {
                namespace: flow.value.namespace,
                id: flow.value.id,
                tab: "edit",
                tenant: route.params.tenant,
            },
        });
    };

    const restoreFlow = () => {
        store.dispatch("flow/createFlow", {
            flow: YAML_UTILS.deleteMetadata(flow.value.source, "deleted"),
        }).then(() => {
            coreStore.unsavedChange = false;
            router.go(0);
        });
    };
</script>