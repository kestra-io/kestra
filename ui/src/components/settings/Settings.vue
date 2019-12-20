<template>
    <div>
        <h1 class="text-capitalize">{{$t('settings')}}</h1>
        <hr />
        <b-row>
            <b-col sm="12" md="4">
                <b-form-group :label="$t('Set default page')">
                    <b-form-select v-model="defaultRoute" :options="routesOptions"></b-form-select>
                </b-form-group>
            </b-col>
            <b-col sm="12" md="4">
                <b-form-group :label="$t('Language')">
                    <b-form-select v-model="lang" :options="langOptions"></b-form-select>
                </b-form-group>
            </b-col>
        </b-row>
    </div>
</template>

<script>
import RouteContext from "../../mixins/routeContext";
export default {
    mixins: [RouteContext],
    data() {
        return {
            langOptions: [
                { value: "en", text: "English" },
                { value: "fr", text: "Français" }
            ]
        };
    },
    computed: {
        routesOptions() {
            return [
                { value: "flows", text: "flows" },
                { value: "settings", text: this.$t("settings") }
            ];
        },
        defaultRoute: {
            set(route) {
                localStorage.setItem("defaultPage", route);
                this.$bvToast.toast(this.$t("Successfully set"), {
                    title: this.$t("Default page"),
                    autoHideDelay: 5000,
                    toaster: "b-toaster-top-center",
                    variant: "success"
                });
            },
            get() {
                return localStorage.getItem("defaultPage") || "flows";
            }
        },
        lang: {
            set(lang) {
                localStorage.setItem("lang", lang);
                this.$moment.locale(lang);
                this.$root.$i18n.locale = lang;
                this.$bvToast.toast(this.$t("Successfully set"), {
                    title: this.$t("Language"),
                    autoHideDelay: 5000,
                    toaster: "b-toaster-top-center",
                    variant: "success"
                });
            },
            get() {
                return localStorage.getItem("lang") || "en";
            }
        }
    }
};
</script>

<style scoped lang="scss">
</style>
