<template>
    <KsDialog
        v-model="visible"
        :title="title"
        formLayout
        scrollable
    >
        <KsForm labelPosition="left" :model="form" ref="formRef">
            <KsFormItem v-if="!editing" :label="$t('namespace')" prop="namespace" required inline class="field-item">
                <NamespaceSelect v-model="namespace" all />
            </KsFormItem>
            <KsFormItem v-else :label="$t('namespace')" inline class="field-item">
                <KsInput :modelValue="namespace" disabled />
            </KsFormItem>

            <KsFormItem :label="$t('credential.name')" prop="name" required inline class="field-item">
                <KsInput v-model="name" :disabled="editing" :placeholder="$t('credential.namePlaceholder')" />
            </KsFormItem>

            <KsFormItem :label="$t('credential.type')" prop="type" required inline class="field-item">
                <KsSelect v-model="form.type" :disabled="editing">
                    <KsOption
                        v-for="option in credentialsStore.types"
                        :key="option.type"
                        :label="option.label"
                        :value="option.type"
                    />
                </KsSelect>
            </KsFormItem>

            <!-- API_KEY and BEARER_TOKEN each carry exactly one secret, so they share a field. -->
            <KsFormItem v-if="uses('singleSecretValue')" :label="secretLabel" prop="singleSecretValue" required inline class="field-item">
                <KsPassword v-model="form.singleSecretValue" :placeholder="$t('credential.valuePlaceholder')" />
            </KsFormItem>

            <KsFormItem v-if="uses('username')" :label="$t('credential.username')" prop="username" required inline class="field-item">
                <KsInput v-model="form.username" autocomplete="off" />
            </KsFormItem>
            <KsFormItem v-if="uses('password')" :label="$t('credential.password')" prop="password" required inline class="field-item">
                <KsPassword v-model="form.password" />
            </KsFormItem>

            <KsFormItem v-if="uses('tokenUrl')" :label="$t('credential.tokenUrl')" prop="tokenUrl" required inline class="field-item">
                <KsInput v-model="form.tokenUrl" placeholder="https://auth.example/oauth/token" autocomplete="off" />
            </KsFormItem>

            <KsFormItem v-if="uses('fhirVersion')" :label="$t('credential.fhirVersion')" prop="fhirVersion" inline class="field-item">
                <KsSelect v-model="form.fhirVersion">
                    <KsOption label="R4" value="R4" />
                </KsSelect>
            </KsFormItem>
            <KsFormItem v-if="uses('sandbox')" :label="$t('credential.sandbox')" prop="sandbox" inline class="field-item">
                <KsSwitch v-model="form.sandbox" inlinePrompt />
            </KsFormItem>

            <!-- In sandbox mode the server supplies the fixed sandbox tenant, so the field goes. -->
            <KsFormItem
                v-if="uses('tenantKey') && !(form.type === 'CERNER_FHIR' && form.sandbox)"
                :label="$t('credential.tenantKey')"
                prop="tenantKey"
                inline
                class="field-item"
            >
                <KsInput v-model="form.tenantKey" autocomplete="off" />
            </KsFormItem>

            <KsFormItem v-if="uses('clientId')" :label="$t('credential.clientId')" prop="clientId" required inline class="field-item">
                <KsInput v-model="form.clientId" autocomplete="off" />
            </KsFormItem>
            <KsFormItem v-if="uses('clientSecret')" :label="$t('credential.clientSecret')" prop="clientSecret" required inline class="field-item">
                <KsPassword v-model="form.clientSecret" />
            </KsFormItem>

            <KsFormItem v-if="uses('scopes')" :label="$t('credential.scopes')" prop="scopes" labelPosition="top">
                <KsInput
                    :modelValue="form.scopes.join('\n')"
                    @update:modelValue="(v: string | number | undefined) => form.scopes = String(v ?? '').split('\n').map(s => s.trim()).filter(Boolean)"
                    type="textarea"
                    :rows="3"
                    resize="vertical"
                    :placeholder="$t('credential.scopesPlaceholder')"
                />
            </KsFormItem>

            <KsFormItem :label="$t('credential.description')" prop="description" labelPosition="top">
                <KsInput v-model="description" type="textarea" :rows="2" resize="vertical" />
            </KsFormItem>
        </KsForm>

        <template #footer>
            <KsButton @click="visible = false">
                {{ $t('cancel') }}
            </KsButton>
            <KsButton :icon="ContentSave" type="primary" :disabled="saving" @click="save">
                {{ $t('save') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"

    import ContentSave from "vue-material-design-icons/ContentSave.vue"
    import {KsPassword} from "@kestra-io/design-system"
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue"

    import {useToast} from "../../utils/toast"
    import {useCredentialsStore, type CredentialSummary} from "override/stores/credentials"
    import {
        FIELDS_BY_TYPE,
        buildCredentialTypeFields,
        credentialToForm,
        emptyCredentialForm,
    } from "./credentialForm"

    const props = defineProps<{
        modelValue: boolean;
        /** The credential being edited, or undefined when creating one. */
        editing?: CredentialSummary;
    }>()

    const emit = defineEmits<{
        "update:modelValue": [value: boolean];
        saved: [];
    }>()

    const {t} = useI18n()
    const toast = useToast()
    const credentialsStore = useCredentialsStore()

    const formRef = ref()
    const saving = ref(false)

    const form = ref(emptyCredentialForm())
    const name = ref("")
    const namespace = ref("")
    const description = ref("")

    const editing = computed(() => props.editing !== undefined)

    const visible = computed({
        get: () => props.modelValue,
        set: (value: boolean) => emit("update:modelValue", value),
    })

    const title = computed(() =>
        editing.value ? t("credential.update", {name: name.value}) : t("credential.add"),
    )

    /** API_KEY and BEARER_TOKEN both use the single-secret field, but not under the same name. */
    const secretLabel = computed(() =>
        form.value.type === "BEARER_TOKEN" ? t("credential.bearerToken") : t("credential.key"),
    )

    const uses = (field: string) => FIELDS_BY_TYPE[form.value.type]?.includes(field) ?? false

    /**
     * Fills the form when the drawer opens. Editing needs a second call: the list carries no
     * material, so the credential has to be read in full before its fields can be shown.
     */
    watch(() => props.modelValue, async (open) => {
        if (!open) return

        if (props.editing) {
            name.value = props.editing.name
            namespace.value = props.editing.namespace
            description.value = props.editing.description ?? ""
            const detail = await credentialsStore.load({
                namespace: props.editing.namespace,
                name: props.editing.name,
            })
            form.value = credentialToForm(detail)
        } else {
            form.value = emptyCredentialForm()
            name.value = ""
            namespace.value = ""
            description.value = ""
        }
    })

    const save = async () => {
        const valid = await formRef.value?.validate?.().catch(() => false)
        if (valid === false) return

        saving.value = true
        try {
            const credential = {
                name: name.value,
                description: description.value || undefined,
                ...buildCredentialTypeFields(form.value),
            }

            if (props.editing) {
                await credentialsStore.update({
                    namespace: props.editing.namespace,
                    name: props.editing.name,
                    credential,
                })
            } else {
                await credentialsStore.create({namespace: namespace.value, credential})
            }

            toast.saved(name.value)
            visible.value = false
            emit("saved")
        } finally {
            saving.value = false
        }
    }
</script>

<style scoped lang="scss">
    .field-item :deep(.kel-form-item__content) {
        flex: 0 0 260px;
        max-width: 260px;
    }

    .field-item :deep(.kel-form-item__content) > * {
        width: 100%;
    }
</style>
