<template>
    <div>
        <b-form-group :label="$t('Language')">
            <b-form-select v-model="lang" :options="langOptions"></b-form-select>
        </b-form-group>
        <b-form-group :label="$t('Text editor line count auto fold')">
            <b-input v-model="autofoldTextEditor" type="number" step="1" min="1"/>
        </b-form-group>
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
        routeInfo() {
            return {
                title: this.$t("settings")
            };
        },
        lang: {
            set(lang) {
                localStorage.setItem("lang", lang);
                this.$moment.locale(lang);
                this.$root.$i18n.locale = lang;
                this.$toast().success();
            },
            get() {
                return localStorage.getItem("lang") || "en";
            }
        },
        autofoldTextEditor: {
            set(value) {
                localStorage.setItem("autofoldTextEditor", value);
            },
            get() {
                return parseInt(localStorage.getItem('autofoldTextEditor') || 3)
            }
        },

    }
};
</script>
