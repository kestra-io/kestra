import Vue from "vue";

export default {
    manualComponents: [],
    methods: {
        createManualComponent(parent, component, propsData) {
            const vueComponent = Vue.extend({
                extends: component,
            });

            return new vueComponent({
                parent: parent,
                propsData
            });
        },
        mountManualComponent(vueInstance) {
            const mount = vueInstance.$mount();

            if (this.manualComponents === undefined) {
                this.manualComponents = [];
            }

            this.manualComponents.push(mount);

            return mount;
        }
    },
    destroyed() {
        (this.manualComponents || []).forEach(value => {
            value.$destroy();
        });
    }
}
