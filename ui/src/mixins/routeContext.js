export default {
    created() {
        this.onLoad()
    },
    watch: {
        $route() {
            this.onLoad()
        }
    },
    methods: {
        onLoad() {
            const r = this.$route
            const routeInfo = {
                'executionsRaw': {
                    title: this.$t('search'),
                    breadcrumb: [{ label: 'executions', link: { name: 'executionRaw' } }]
                },
                'execution': {
                    title: this.$t('details'),
                    breadcrumb: [
                        {
                            label: this.$t('flows'), link: {
                                name: 'flows',
                                query: {
                                    namespace: r.params.namespace,
                                }
                            }
                        },
                        {
                            label: r.params.flowId, link: {
                                name: 'flow',
                                params: { namespace: r.params.namespace, id: r.params.flowId }
                            },
                        },
                        {
                            label: this.$t('execution'), link: {}
                        },
                        {
                            label: r.params.id, link: {
                                name: 'execution'
                            }
                        }],
                },
                'flows': {
                    title: this.$t('search'),
                    breadcrumb: [{
                        label: this.$t('flows'), link: {
                            name: 'flows', params: {
                                namespace: r.params.namespace,
                            }
                        }
                    }]
                },
                'flow': {
                    title: this.$t('details'),
                    breadcrumb: [
                        {
                            label: this.$t('flows'), link: {
                                name: 'flows',
                                query: {
                                    namespace: r.params.namespace,
                                }
                            }
                        },
                        {
                            label: r.params.id,
                        }
                    ]
                }
            }[this.$route.name]
            this.$store.commit('layout/setTopNavbar', routeInfo)
        }
    }
}