<template>
    <div>
        <b-form-group :label="$t('Language')" label-cols-sm="3">
            <b-form-select v-model="lang" :options="langOptions" />
        </b-form-group>
        <b-form-group :label="$t('Fold auto')" label-cols-sm="3">
            <b-checkbox v-model="autofoldTextEditor" value="1" unchecked-value="0" />
        </b-form-group>
        <b-form-group :label="$t('Default route')" label-cols-sm="3">
            <b-row>
                <b-col md="4">
                    <b-form-select v-model="defaultRoute" :options="defaultRouteOptions" />
                </b-col>
                <b-col md="8">
                    <b-input :placeholder="$t('route extra query string (leave empty if not sure)')" v-model="defaultExtraQueryString" />
                </b-col>
            </b-row>
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
                    {value: "en", text: "English"},
                    {value: "fr", text: "Français"}
                ],
            };
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("settings")
                };
            },
            defaultRouteOptions () {
                return[
                    {value: "home", text:this.$t("home")},
                    {value: "flowsList", text:this.$t("flowsList")},
                    {value: "flowsAdd", text:this.$t("flowsAdd")},
                    {value: "flowEdit", text:this.$t("flowEdit")},
                    {value: "executionsList", text:this.$t("executionsList")},
                    {value: "executionEdit", text:this.$t("executionEdit")},
                    {value: "taskRunList", text:this.$t("taskRunList")},
                    {value: "plugin", text:this.$t("plugin")},
                    {value: "pluginView", text:this.$t("pluginView")},
                    {value: "templateList", text:this.$t("templateList")},
                    {value: "templateAdd", text:this.$t("templateAdd")},
                    {value: "templateEdit", text:this.$t("templateEdit")},
                    {value: "logs", text:this.$t("logs")},
                    {value: "settings", text:this.$t("settings")},
                ]
            },
            lang: {
                set(lang) {
                    localStorage.setItem("lang", lang);
                    this.$moment.locale(lang);
                    this.$root.$i18n.locale = lang;
                    this.$toast().saved();
                },
                get() {
                    return localStorage.getItem("lang") || "en";
                }
            },
            defaultRoute: {
                set(route) {
                    localStorage.setItem("defaultRoute", route)
                },
                get() {
                    return localStorage.getItem("defaultRoute") || "home"
                }
            },
            defaultExtraQueryString: {
                set(querySting) {
                    localStorage.setItem("defaultExtraQueryString", querySting)
                },
                get() {
                    return localStorage.getItem("defaultExtraQueryString") || ""
                }
            },
            autofoldTextEditor: {
                set(value) {
                    localStorage.setItem("autofoldTextEditor", value);

                    this.$toast().saved();
                },
                get() {
                    return localStorage.getItem("autofoldTextEditor")
                }
            },

        }
    };
</script>
