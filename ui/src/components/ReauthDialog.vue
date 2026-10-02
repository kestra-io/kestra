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
        <p v-if="openedLoginTab" data-test="reauth-tab-hint">{{ $t('sign in in a new tab hint') }}</p>
        <KsAlert v-if="failed" class="reauth-error" type="error" :closable="false" :title="$t('setup.validation.incorrect_creds')" />
        <KsAlert v-if="notConfirmed" class="reauth-error" type="error" :closable="false" :title="$t('sign in not confirmed')" />
        <KsForm v-if="canSignInWithPassword && !openedLoginTab" :model="credentials" @submit.prevent>
            <KsFormItem>
                <KsInput
                    v-model="credentials.username"
                    name="username"
                    :readonly="Boolean(lockedUsername)"
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
        <KsButton v-if="canSignInWithPassword && loginUrl && !openedLoginTab" text type="primary" @click="openLoginTab">
            {{ $t('use another sign-in method') }}
        </KsButton>
        <template #footer>
            <KsButton @click="resolveReauth(false)">
                {{ $t('go to login') }}
            </KsButton>
            <KsButton v-if="openedLoginTab" type="primary" :loading="loading" data-test="reauth-continue" @click="continueAfterSignIn">
                {{ $t('continue after sign in') }}
            </KsButton>
            <KsButton v-else-if="canSignInWithPassword" type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
                {{ $t('setup.login') }}
            </KsButton>
            <KsButton v-else-if="loginUrl" type="primary" data-test="reauth-open-tab" @click="openLoginTab">
                {{ $t('sign in in a new tab') }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script lang="ts" setup>
    import {computed, ref, watch} from "vue"
    import AccountOutline from "vue-material-design-icons/AccountOutline.vue"
    import LockOutline from "vue-material-design-icons/LockOutline.vue"
    import {confirmReauth, resolveReauth, submitReauth, useReauthDialog} from "../composables/useReauthDialog"

    const {visible, loginUrl, lockedUsername, canSignInWithPassword} = useReauthDialog()

    const credentials = ref({username: "", password: ""})
    const loading = ref(false)
    const failed = ref(false)
    const notConfirmed = ref(false)
    const openedLoginTab = ref(false)

    const canSubmit = computed(() => !loading.value && credentials.value.username.trim() !== "" && credentials.value.password !== "")

    watch([visible, lockedUsername], ([isVisible, username]) => {
        credentials.value = {username: isVisible ? username ?? "" : "", password: ""}
        failed.value = false
        notConfirmed.value = false
        openedLoginTab.value = false
    })

    function openLoginTab() {
        window.open(loginUrl.value, "_blank", "noopener")
        openedLoginTab.value = true
    }

    async function continueAfterSignIn() {
        loading.value = true
        notConfirmed.value = false
        try {
            await confirmReauth()
            resolveReauth(true)
        } catch {
            notConfirmed.value = true
        } finally {
            loading.value = false
        }
    }

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
