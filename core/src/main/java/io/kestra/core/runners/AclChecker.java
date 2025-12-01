package io.kestra.core.runners;

import javax.annotation.CheckReturnValue;
import java.util.List;

/**
 * Check if the current taskrun has access to the requested resources.
 *
 * <p>
 * IMPORTANT: remember to call the <code>check()</code> method to check the ACL.
 *
 * @see AllowedResources
 */
public interface AclChecker {

    /**Tasks that need to access resources outside their namespace should use this interface to check ACL (Allowed namespaces in EE).
     * Allow all namespaces.
     * <p>
     * IMPORTANT: remember to call the <code>check()</code> method to check the ACL.
     */
    @CheckReturnValue
    AllowedResources allowAllNamespaces();

    /**
     * Allow only the given namespace.
     * <p>
     * IMPORTANT: remember to call the <code>check()</code> method to check the ACL.
     */
    @CheckReturnValue
    AllowedResources allowNamespace(String namespace);

    /**
     * Allow only the given namespaces.
     * <p>
     * IMPORTANT: remember to call the <code>check()</code> method to check the ACL.
     */
    @CheckReturnValue
    AllowedResources allowNamespaces(List<String> namespaces);

    /**
     * Represents a set of allowed resources.
     * Tasks that need to access resources outside their namespace should call the <code>check()</code> method to check the ACL (Allowed namespaces in EE).
     */
    interface AllowedResources {
        /**
         * Check if the current taskrun has access to the requested resources.
         */
        void check();
    }
}
