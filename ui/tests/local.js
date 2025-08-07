import {mount} from "@vue/test-utils"
import {createStore} from "vuex"
import {createI18n} from "vue-i18n";
import moment from "moment/moment";
import {extendMoment} from "moment-range";
import ElementPlus from "element-plus";
import filters from "../src/utils/filters";
import translations from "../src/translations.json";
import stores from "../src/stores/store"
import "../src/utils/global"


let i18n = createI18n({
    locale: "en",
    messages: translations,
    allowComposition: true,
    legacy: false,
    warnHtmlMessage: false,
});

const store = createStore(stores);

moment.locale("en");

export default (component, options, callback) => {
    const app =  mount(
        component,
        {
            ...{
                global: {
                    plugins: [store, i18n, ElementPlus],
                    config: {
                        globalProperties: {
                            $filters: filters,
                            $moment: extendMoment(moment)
                        }
                    }
                }
            },
            ...options
        }
    )

    callback(store);

    return app;
}