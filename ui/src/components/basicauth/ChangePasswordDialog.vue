<template>
    <KsDialog
        v-model="visible"
        :title="$t('change_password.title')"
        :dirty="isDirty"
        data-test="change-password-dialog"
    >
        <KsAlert
            v-if="isManagedByConfig"
            type="info"
            :closable="false"
            :title="$t('change_password.managed_by_config')"
            data-test="change-password-managed-by-config"
        />

        <KsForm v-else labelPosition="top" :model="form" @submit.prevent="submit">
            <KsFormItem :label="$t('setup.form.username')">
                <KsInput v-model="form.username" type="email" autocomplete="username" data-test="change-password-username" />
            </KsFormItem>
            <KsFormItem :label="$t('change_password.current_password')">
                <KsInput
                    v-model="form.currentPassword"
                    type="password"
                    showPassword
                    autocomplete="current-password"
                    data-test="change-password-current"
                />
            </KsFormItem>
            <KsFormItem :label="$t('change_password.new_password')">
                <KsInput
                    v-model="form.password"
                    type="password"
                    showPassword
                    autocomplete="new-password"
                    data-test="change-password-new"
                />
            </KsFormItem>
            <KsPasswordRequirements v-model:valid="isPasswordValid" :password="form.password" />
            <KsFormItem :label="$t('confirm password')">
                <KsInput
                    v-model="confirmPassword"
                    type="password"
                    showPassword
                    autocomplete="new-password"
                    data-test="change-password-confirm"
                />
            </KsFormItem>
            <KsCheckItem :met="passwordsMatch">
                {{ $t('password_requirements.match') }}
            </KsCheckItem>

            <KsAlert
                v-for="error in serverErrors"
                :key="error"
                type="error"
                :closable="false"
                :title="error"
                data-test="change-password-error"
            />
        </KsForm>

        <template #footer>
            <KsButton @click="visible = false">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton
                v-if="!isManagedByConfig"
                type="primary"
                :disabled="!canSubmit"
                :loading="submitting"
                data-test="change-password-submit"
                @click="submit"
            >
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {asProblem} from "@kestra-io/kestra-sdk"
    import {useMiscStore} from "override/stores/misc"
    import {useToast} from "../../utils/toast"
    import {problemDetail, problemFieldMessage} from "../../utils/problem"

    const visible = defineModel<boolean>({required: true})

    const {t, te} = useI18n()
    const toast = useToast()
    const miscStore = useMiscStore()

    const emptyForm = () => ({username: "", currentPassword: "", password: ""})

    const form = ref(emptyForm())
    const confirmPassword = ref("")
    const isPasswordValid = ref(false)
    const submitting = ref(false)
    const serverErrors = ref<string[]>([])

    const isManagedByConfig = computed(() => miscStore.configs?.isBasicAuthManagedByConfig === true)

    const isDirty = computed(() =>
        Object.values(form.value).some(Boolean) || confirmPassword.value.length > 0,
    )

    const passwordsMatch = computed(() =>
        form.value.password.length > 0 && form.value.password === confirmPassword.value,
    )

    const canSubmit = computed(() =>
        form.value.username.trim().length > 0 &&
        form.value.currentPassword.length > 0 &&
        isPasswordValid.value &&
        passwordsMatch.value,
    )

    watch(visible, (open) => {
        if (open) {
            form.value = emptyForm()
            confirmPassword.value = ""
            serverErrors.value = []
        }
    })

    async function submit() {
        if (!canSubmit.value || submitting.value) return

        submitting.value = true
        serverErrors.value = []
        try {
            await miscStore.changeBasicAuth({...form.value, username: form.value.username.trim()})
            toast.success(t("change_password.success"))
            visible.value = false
        } catch (error) {
            const problem = asProblem(error)
            serverErrors.value = problem?.errors?.length
                ? problem.errors.map((item) => problemFieldMessage(item, t, te))
                : [problemDetail(problem, t, te) || t("errors.generic.content")]
        } finally {
            submitting.value = false
        }
    }
</script>
