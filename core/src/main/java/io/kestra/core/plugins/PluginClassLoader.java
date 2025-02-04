package io.kestra.core.plugins;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Enumeration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Default ClassLoader for loading plugins using a 'child-first strategy'. In other words, this ClassLoader
 * attempts to find the class in its own context before delegating to the parent ClassLoader.
 */
@Slf4j
public class PluginClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    // The list of package to exclude from classloader isolation.
    // IMPORTANT - This list must contain system and common libraries to delegate loading to parent classloader to:
    // - protect from impersonation of system classes (e.g: java.*)
    // - avoid experiencing java.lang.LinkageError: loader constraint violation.
    private static final Pattern EXCLUDES = Pattern.compile("^(?:"
        + "java"
        + "|javax"
        + "|jakarta"
        + "|io.kestra.core"
        + "|io.kestra.plugin.core"
        + "|org.slf4j"
        + "|ch.qos.logback"
        + "|com.fasterxml.jackson.core"
        + "|com.fasterxml.jackson.annotation"
        + "|com.fasterxml.jackson.module"
        + "|com.fasterxml.jackson.databind"
        + "|com.fasterxml.jackson.dataformat.ion"
        + "|com.fasterxml.jackson.dataformat.yaml"
        + "|com.fasterxml.jackson.dataformat.xml"
        + "|org.reactivestreams"
        + "|dev.failsafe"
        + ")\\..*$");

    private final ClassLoader parent;

    private final URL pluginLocation;

    @SuppressWarnings("removal")
    public static PluginClassLoader of(final URL pluginLocation, final URL[] urls, final ClassLoader parent) {
        return AccessController.doPrivileged(
            (PrivilegedAction<PluginClassLoader>) () -> new PluginClassLoader(pluginLocation, urls, parent)
        );
    }

    /**
     * Creates a new {@link PluginClassLoader} instance.
     *
     * @param pluginLocation the top-level plugin location.
     * @param urls the URLs from which to load classes and resources.
     * @param parent the parent {@link ClassLoader}.
     */
    private PluginClassLoader(final URL pluginLocation, final URL[] urls, final ClassLoader parent) {
        super(urls, parent);
        this.parent = parent;
        this.pluginLocation = pluginLocation;
    }

    public String location() {
        return pluginLocation.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // First, check if the class has already been loaded
            Class<?> loadedClass = findLoadedClass(name);

            if (loadedClass == null && shouldLoadFromUrls(name)) {
                try {
                    loadedClass = findClass(name);
                }
                catch (final ClassNotFoundException e) {
                    log.debug(
                        "Class '{}' not found on '{}' for plugin '{}', delegating to parent '{}'",
                        name,
                        this.getName(),
                        pluginLocation,
                        this.parent.getName()
                    );
                } catch (LinkageError e){
                    log.debug(
                        "Class '{}'already in classpath for plugin '{}', delegating to parent '{}'",
                        name,
                        pluginLocation,
                        this.parent.getName()
                    );
                }
            }

            if (loadedClass == null) {
                // If still not found, then delegate to parent classloader.
                loadedClass = super.loadClass(name, resolve);
            }

            if (resolve) { // marked to resolve
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }

    private static boolean shouldLoadFromUrls(final String name) {
        return !EXCLUDES.matcher(name).matches();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);
        return findResources(name); // Only find resources locally.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getResource(final String name) {
        Objects.requireNonNull(name);
        return findResource(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "PluginClassLoader[location=" + pluginLocation + "] ";
    }
}
