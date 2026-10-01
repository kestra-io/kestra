<template>
    <KsDialog
        :modelValue="visible"
        :title="$t('session expired')"
        width="min(360px, 92vw)"
        alignCenter
        :closeOnClickModal="false"
        :closeOnPressEscape="false"
        :showClose="false"
        data-test="reauth-dialog"
    >
        <p>{{ $t('session expired description') }}</p>
        <KsAlert v-if="failed" class="reauth-error" type="error" :closable="false" :title="$t('setup.validation.incorrect_creds')" />
        <KsForm :model="credentials" @submit.prevent="submit">
            <KsFormItem>
                <KsInput
                    v-model="credentials.username"
                    name="username"
                    :placeholder="$t('email')"
                    autocomplete="username"
                >
                    <template #prefix><KsIcon><AccountOutline /></KsIcon></template>
                </KsInput>
            </KsFormItem>
            <KsFormItem>
                <KsInput
                    v-model="credentials.password"
                    name="password"
                    showPassword
                    :placeholder="$t('password')"
                    autocomplete="current-password"
                    @keyup.enter="submit"
                >
                    <template #prefix><KsIcon><LockOutline /></KsIcon></template>
                </KsInput>
            </KsFormItem>
        </KsForm>
        <template #footer>
            <KsButton @click="resolveReauth(false)">
                {{ $t('go to login') }}
            </KsButton>
            <KsButton type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
                {{ $t('setup.login') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue"
    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import {resolveReauth, submitReauth, useReauthDialog} from "../composables/useReauthDialog"

    const {visible} = useReauthDialog()

    const credentials = ref({username: "", password: ""})
    const loading = ref(false)
    const failed = ref(false)

    const canSubmit = computed(() => !loading.value && credentials.value.username.trim() !== "" && credentials.value.password !== "")

    watch(visible, (isVisible) => {
        if (!isVisible) {
            credentials.value = {username: "", password: ""}
            failed.value = false
        }
    })

    async function submit() {
        if (!canSubmit.value) return
        loading.value = true
        failed.value = false
        try {
            await submitReauth(credentials.value)
            resolveReauth(true)
        } catch {
            failed.value = true
        } finally {
            loading.value = false
        }
    }
</script>

<style lang="scss" scoped>
    .reauth-error {
        margin-bottom: var(--ks-spacing-3);
    }
</style>
