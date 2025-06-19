<template>
    <div class="basic-auth-login">
        <div class="d-flex justify-content-center">
            <Logo class="logo" />
        </div>

        <el-form @submit.prevent :model="credentials" ref="form">
            <input type="hidden" name="from" :value="redirectPath">
            <el-form-item>
                <el-input
                    name="username"
                    size="large"
                    id="input-username"
                    v-model="credentials.username"
                    :placeholder="$t('email')"
                    required
                    prop="username"
                >
                    <template #prepend>
                        <Account />
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item>
                <el-input
                    v-model="credentials.password"
                    size="large"
                    name="password"
                    id="input-password"
                    :placeholder="$t('password')"
                    type="password"
                    show-password
                    required
                    prop="password"
                >
                    <template #prepend>
                        <Lock />
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item>
                <el-button
                    type="primary"
                    class="w-100"
                    size="large"
                    native-type="submit"
                    @click="handleSubmit"
                    :disabled="isLoginDisabled"
                    :loading="isLoading"
                >
                    {{ $t("setup.login") }}
                </el-button>
            </el-form-item>
        </el-form>
    </div>
</template>

<script setup lang="ts">
    import Account from "vue-material-design-icons/Account.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
    import Logo from "../home/Logo.vue";
    import {ref, computed} from "vue";
    import {useRouter, useRoute} from "vue-router";
    import {useStore} from "vuex";
    import type {FormInstance} from "element-plus";

    interface Credentials {
        username: string;
        password: string;
    }

    const router = useRouter();
    const route = useRoute();
    const store = useStore();

    const form = ref<FormInstance>();
    const isLoading = ref(false);
    const credentials = ref<Credentials>({
        username: "",
        password: ""
    });

    const redirectPath = computed(() => (route.query.from as string) ?? "/welcome");

    const isLoginDisabled = computed(() => 
        !credentials.value.username.trim() || 
        !credentials.value.password.trim() || 
        isLoading.value
    );

    const handleSubmit = (event: Event) => {
        event.preventDefault();
        if (!form.value || isLoading.value) return;

        form.value.validate((valid: boolean) => {
            if (!valid) return;
        
            isLoading.value = true;
        
            const base64Credentials = btoa(`${credentials.value.username.trim()}:${credentials.value.password}`);
            localStorage.setItem("basicAuthCredentials", base64Credentials);
            localStorage.removeItem("basicAuthSetupInProgress");
            store.$http.defaults.headers.common["Authorization"] = `Basic ${base64Credentials}`;
        
            router.push(redirectPath.value);
            isLoading.value = false;
        });
    };
</script>

<style lang="scss" scoped>
    .basic-auth-login {
        flex-shrink: 1;
        width: 400px;

        .logo {
            width: 250px;
            margin-bottom: 40px;
        }

        .el-button.el-button--default {
            background: var(--bs-gray-200);
            height: 54px;

            html.dark & {
                background: var(--input-bg);

                &.el-button {
                    border: 0;
                }
            }
        }

        .el-form-item {
            .el-input {
                height: 54px;
            }

            .el-input-group__prepend {
                .material-design-icon {
                    .material-design-icon__svg {
                        width: 1.5em;
                        height: 1.5em;
                        bottom: -0.250em;
                    }
                }
            }
        }
    }
</style>
