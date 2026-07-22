<template>
    <div class="new-flow-landing" data-test="new-flow-landing">
        <div class="landing-header">
            <KsText tag="h1" class="landing-title">{{ $t("new_flow_landing.title") }}</KsText>
            <KsText class="landing-sub">{{ $t("new_flow_landing.subtitle") }}</KsText>
        </div>

        <div class="landing-body">
            <KsCard class="primary-card" shadow="never" data-test="blank-flow-card">
                <div class="primary-card-body">
                    <div class="primary-card-header">
                    <div class="primary-icon">
                        <Plus :size="22" />
                    </div>
                    <div>
                        <KsText tag="h2" class="card-title">{{ $t("new_flow_landing.blank.title") }}</KsText>
                        <KsText class="card-sub">{{ $t("new_flow_landing.blank.subtitle") }}</KsText>
                    </div>
                </div>

                <KsAlert
                    v-if="namespacesError"
                    type="error"
                    :closable="false"
                    class="namespaces-error"
                    data-test="namespaces-error"
                >
                    {{ $t("new_flow_landing.blank.namespaces_error") }}
                </KsAlert>

                <KsForm class="primary-card-fields" labelPosition="top" @submit.prevent>
                    <KsFormItem :label="$t('new_flow_landing.blank.id_label')">
                        <KsInput
                            v-model="flowId"
                            :placeholder="$t('new_flow_landing.blank.id_placeholder')"
                            data-test="blank-flow-id"
                        />
                    </KsFormItem>

                    <KsFormItem :label="$t('namespace')">
                        <KsSelect
                            v-model="selectedNamespace"
                            filterable
                            allowCreate
                            defaultFirstOption
                            :loading="namespacesLoading"
                            :placeholder="$t('new_flow_landing.blank.namespace_placeholder')"
                            data-test="blank-flow-namespace"
                        >
                            <KsOption
                                v-for="ns in namespaceOptions"
                                :key="ns"
                                :label="ns"
                                :value="ns"
                            />
                        </KsSelect>
                    </KsFormItem>
                </KsForm>

                <KsButton
                    type="primary"
                    class="open-editor-btn"
                    :disabled="!flowId || !selectedNamespace"
                    data-test="blank-flow-open-editor"
                    @click="openEditor"
                >
                    {{ $t("new_flow_landing.blank.open_editor") }}
                </KsButton>
                </div>
            </KsCard>

            <div class="secondary-rows">
                <router-link
                    :to="{name: 'blueprints', params: {tenant: route.params.tenant, kind: 'flow', tab: 'community'}}"
                    class="secondary-card"
                    data-test="browse-blueprints-card"
                >
                    <div class="secondary-card-icon">
                        <ViewGridOutline :size="20" />
                    </div>
                    <div class="secondary-card-body">
                        <KsText class="secondary-card-title">{{ $t("new_flow_landing.blueprints.title") }}</KsText>
                        <KsText class="secondary-card-sub">{{ $t("new_flow_landing.blueprints.subtitle") }}</KsText>
                    </div>
                    <ChevronRight :size="16" class="secondary-card-arrow" />
                </router-link>

                <router-link
                    :to="{name: 'namespaces/update', params: {tenant: route.params.tenant, id: systemNamespace}, query: {tab: 'blueprints'}}"
                    class="secondary-card"
                    data-test="system-flow-card"
                >
                    <div class="secondary-card-icon">
                        <CogOutline :size="20" />
                    </div>
                    <div class="secondary-card-body">
                        <KsText class="secondary-card-title">
                            {{ $t("new_flow_landing.system.title") }}
                        </KsText>
                        <KsText class="secondary-card-sub">{{ $t("new_flow_landing.system.subtitle") }}</KsText>
                    </div>
                    <KsTag size="small" class="system-badge">{{ $t("new_flow_landing.system.badge") }}</KsTag>
                    <ChevronRight :size="16" class="secondary-card-arrow" />
                </router-link>

                <button
                    type="button"
                    class="secondary-card"
                    data-test="import-yaml-card"
                    @click="emit('import')"
                >
                    <div class="secondary-card-icon">
                        <TrayArrowDown :size="20" />
                    </div>
                    <div class="secondary-card-body">
                        <KsText class="secondary-card-title">{{ $t("new_flow_landing.import.title") }}</KsText>
                        <KsText class="secondary-card-sub">{{ $t("new_flow_landing.import.subtitle") }}</KsText>
                    </div>
                    <ChevronRight :size="16" class="secondary-card-arrow" />
                </button>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted} from "vue"
    import {useRoute} from "vue-router"
    import {useMiscStore} from "override/stores/misc"
    import useNamespaces from "../../../composables/useNamespaces"
    import Plus from "vue-material-design-icons/Plus.vue"
    import ViewGridOutline from "vue-material-design-icons/ViewGridOutline.vue"
    import CogOutline from "vue-material-design-icons/CogOutline.vue"
    import TrayArrowDown from "vue-material-design-icons/TrayArrowDown.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    const emit = defineEmits<{
        proceed: [{id: string; namespace: string}]
        import: []
    }>()

    const route = useRoute()
    const miscStore = useMiscStore()

    const systemNamespace = computed(() => miscStore.configs?.systemNamespace ?? "system")

    const flowId = ref("")
    const selectedNamespace = ref("")
    const namespaceOptions = ref<string[]>([])
    const namespacesError = ref(false)
    const namespacesLoading = ref(false)

    onMounted(async () => {
        namespacesLoading.value = true
        try {
            const ns = await useNamespaces(500).all()
            namespaceOptions.value = ns.map(n => n.id)
        } catch {
            namespacesError.value = true
        } finally {
            namespacesLoading.value = false
        }
    })

    const openEditor = () => {
        emit("proceed", {id: flowId.value, namespace: selectedNamespace.value})
    }
</script>

<style scoped lang="scss">
    .new-flow-landing {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: var(--ks-spacing-8) var(--ks-spacing-4);
        min-height: 100%;
    }

    .landing-header {
        text-align: center;
        margin-bottom: var(--ks-spacing-6);
    }

    .landing-title {
        margin: 0 0 var(--ks-spacing-2);
        font-size: var(--ks-font-size-2xl);
        font-weight: var(--ks-font-weight-semibold);
    }

    .landing-sub {
        display: block;
        color: var(--ks-text-secondary);
    }

    .landing-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
        width: 100%;
        max-width: 36rem;
    }

    .primary-card-body {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);
    }

    .open-editor-btn {
        align-self: flex-start;
    }

    .primary-card-header {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
    }

    .primary-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-7);
        height: var(--ks-spacing-7);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-tag);
        flex-shrink: 0;
        color: var(--ks-text-primary);
    }

    .card-title {
        display: block;
        font-weight: var(--ks-font-weight-semibold);
        font-size: var(--ks-font-size-md);
        margin-bottom: var(--ks-spacing-1);
    }

    .card-sub {
        display: block;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .primary-card-fields {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-3);
        margin-bottom: 0;
    }

    .namespaces-error {
        margin: 0;
    }

    .secondary-rows {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .secondary-card {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-4);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-surface);
        cursor: pointer;
        text-decoration: none;
        color: inherit;
        transition: border-color var(--ks-duration-fast) var(--ks-ease-standard),
            background-color var(--ks-duration-fast) var(--ks-ease-standard);
        width: 100%;
        text-align: left;

        &:hover {
            border-color: var(--ks-border-strong);
            background-color: var(--ks-bg-hover);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 2px;
        }
    }

    .secondary-card-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-6);
        height: var(--ks-spacing-6);
        border-radius: var(--ks-radius-sm);
        background-color: var(--ks-bg-tag);
        flex-shrink: 0;
        color: var(--ks-text-primary);
    }

    .secondary-card-body {
        flex: 1;
        min-width: 0;
    }

    .secondary-card-title {
        display: block;
        font-weight: var(--ks-font-weight-medium);
    }

    .secondary-card-sub {
        display: block;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .secondary-card-arrow {
        flex-shrink: 0;
        color: var(--ks-icon-muted);
    }

    .system-badge {
        flex-shrink: 0;
    }
</style>
