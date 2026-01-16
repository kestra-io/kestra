package io.kestra.core.runners;

import io.kestra.core.services.NamespaceService;
import io.micronaut.context.ApplicationContext;

import java.util.List;
import java.util.Objects;

class AclCheckerImpl implements AclChecker {
    private final NamespaceService namespaceService;
    private final RunContext.FlowInfo flowInfo;

    AclCheckerImpl(ApplicationContext applicationContext, RunContext.FlowInfo flowInfo) {
        this.namespaceService = applicationContext.getBean(NamespaceService.class);
        this.flowInfo = flowInfo;
    }

    @Override
    public AllowedResources allowAllNamespaces() {
        return new AllowAllNamespaces(flowInfo, namespaceService);
    }

    @Override
    public AllowedResources allowNamespace(String namespace) {
        return new AllowNamespace(flowInfo, namespaceService, namespace);
    }

    @Override
    public AllowedResources allowNamespaces(List<String> namespaces) {
        return new AllowNamespaces(flowInfo, namespaceService, namespaces);
    }


    static class AllowAllNamespaces implements AllowedResources {
        private final RunContext.FlowInfo flowInfo;
        private final NamespaceService namespaceService;

        AllowAllNamespaces(RunContext.FlowInfo flowInfo, NamespaceService namespaceService) {
            this.flowInfo = Objects.requireNonNull(flowInfo);
            this.namespaceService = Objects.requireNonNull(namespaceService);
        }

        @Override
        public void check() {
            this.namespaceService.checkAllowedAllNamespaces(flowInfo.tenantId(), flowInfo.tenantId(), flowInfo.namespace());
        }
    }

    static class AllowNamespace implements AllowedResources {
        private final RunContext.FlowInfo flowInfo;
        private final NamespaceService namespaceService;
        private final String namespace;

        public AllowNamespace(RunContext.FlowInfo flowInfo, NamespaceService namespaceService, String namespace) {
            this.flowInfo = Objects.requireNonNull(flowInfo);
            this.namespaceService = Objects.requireNonNull(namespaceService);
            this.namespace = Objects.requireNonNull(namespace);
        }

        @Override
        public void check() {
            namespaceService.checkAllowedNamespace(flowInfo.tenantId(), namespace, flowInfo.tenantId(), flowInfo.namespace());
        }
    }

    static class AllowNamespaces implements AllowedResources {
        private final RunContext.FlowInfo flowInfo;
        private final NamespaceService namespaceService;
        private final List<String> namespaces;

        AllowNamespaces(RunContext.FlowInfo flowInfo, NamespaceService namespaceService, List<String> namespaces) {
            this.flowInfo = Objects.requireNonNull(flowInfo);
            this.namespaceService = Objects.requireNonNull(namespaceService);
            this.namespaces = Objects.requireNonNull(namespaces);

            if (namespaces.isEmpty()) {
                throw new IllegalArgumentException("At least one namespace must be provided");
            }
        }

        @Override
        public void check() {
            namespaces.forEach(namespace -> namespaceService.checkAllowedNamespace(flowInfo.tenantId(), namespace, flowInfo.tenantId(), flowInfo.namespace()));
        }
    }
}
