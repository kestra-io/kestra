<template>
    <KsDialog
        :modelValue="modelValue"
        :title="$t('setup.change_password.title')"
        destroyOnClose
        :appendToBody="true"
        :dirty="isDirty"
        @update:modelValue="emit('update:modelValue', !!$event)"
        @closed="reset"
    >
        <KsForm ref="form" :model="formData" :rules="rules" labelPosition="top" :showMessage="false" @submit.prevent="handleSubmit">
            <KsFormItem :label="$t('setup.form.username')" prop="username">
                <KsInput v-model="formData.username" type="email" autocomplete="username" :placeholder="$t('email')" />
            </KsFormItem>
            <KsText class="username-hint">
                {{ $t("setup.change_password.username_hint") }}
            </KsText>
            <KsFormItem :label="$t('setup.change_password.current_password')" prop="currentPassword">
                <KsInput v-model="formData.currentPassword" type="password" showPassword autocomplete="current-password" />
            </KsFormItem>
            <KsFormItem :label="$t('setup.change_password.new_password')" prop="newPassword">
                <KsInput v-model="formData.newPassword" type="password" showPassword autocomplete="new-password" />
            </KsFormItem>
            <KsPasswordRequirements :password="formData.newPassword" v-model:valid="isPasswordValid" />
            <KsFormItem :label="$t('confirm password')" prop="confirmPassword">
                <KsInput v-model="formData.confirmPassword" type="password" showPassword autocomplete="new-password" />
            </KsFormItem>
            <KsCheckItem :met="passwordsMatch">
                {{ $t("password_requirements.match") }}
            </KsCheckItem>
        </KsForm>

        <template #footer>
            <KsButton @click="close">
                {{ $t("cancel") }}
            </KsButton>
            <KsButton type="primary" :loading="isSubmitting" :disabled="!isSubmitEnabled" @click="handleSubmit">
                {{ $t("save") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {KsMessage} from "@kestra-io/design-system"
    import {useMiscStore} from "override/stores/misc"

    const props = defineProps<{
        modelValue: boolean;
    }>()

    const emit = defineEmits<{
        (e: "update:modelValue", value: boolean): void;
    }>()

    const {t} = useI18n()
    const miscStore = useMiscStore()

    const EMAIL_REGEX = /^[a-zA-Z0-9_!#$%&'*+/=?`{|}~^.-]+@[a-zA-Z0-9.-]+$/

    const form = ref<{validate: () => Promise<boolean>} | null>(null)
    const isSubmitting = ref(false)
    const isPasswordValid = ref(false)

    const formData = ref({
        username: "",
        currentPassword: "",
        newPassword: "",
        confirmPassword: "",
    })

    const passwordsMatch = computed(() =>
        formData.value.newPassword.length > 0 &&
        formData.value.newPassword === formData.value.confirmPassword,
    )

    const isDirty = computed(() =>
        formData.value.username !== "" ||
        formData.value.currentPassword !== "" ||
        formData.value.newPassword !== "" ||
        formData.value.confirmPassword !== "",
    )

    const isSubmitEnabled = computed(() =>
        !isSubmitting.value &&
        EMAIL_REGEX.test(formData.value.username.trim()) &&
        formData.value.currentPassword.length > 0 &&
        isPasswordValid.value &&
        passwordsMatch.value,
    )

    const rules = computed(() => ({
        username: [{required: true, message: t("setup.validation.email_required"), trigger: "blur"}],
        currentPassword: [{required: true, message: t("setup.validation.password_required"), trigger: "blur"}],
        newPassword: [{required: true, message: t("setup.validation.password_required"), trigger: "blur"}],
        confirmPassword: [{required: true, message: t("setup.validation.password_required"), trigger: "blur"}],
    }))

    watch(() => props.modelValue, (open) => {
        if (open) reset()
    })

    function reset() {
        formData.value = {username: "", currentPassword: "", newPassword: "", confirmPassword: ""}
        isPasswordValid.value = false
        isSubmitting.value = false
    }

    function close() {
        emit("update:modelValue", false)
    }

    async function handleSubmit() {
        if (!isSubmitEnabled.value || isSubmitting.value) return
        if (form.value && !(await form.value.validate().catch(() => false))) return

        isSubmitting.value = true
        try {
            await miscStore.addBasicAuth({
                username: formData.value.username.trim(),
                password: formData.value.newPassword,
                currentPassword: formData.value.currentPassword,
            })
            KsMessage({message: t("setup.change_password.success"), type: "success"})
            close()
        } catch (error: unknown) {
            const status = (error as {response?: {status?: number}, status?: number})?.response?.status
                ?? (error as {status?: number})?.status
            if (status === 422) {
                KsMessage({message: t("setup.change_password.incorrect_current"), type: "error"})
            } else if (status === 401) {
                KsMessage({message: t("setup.validation.incorrect_creds"), type: "error"})
            } else {
                KsMessage({message: t("setup.validation.incorrect_creds"), type: "error"})
            }
        } finally {
            isSubmitting.value = false
        }
    }
</script>

<style scoped lang="scss">
    .username-hint {
        display: block;
        margin-bottom: var(--ks-spacing-3);
    }
</style>
