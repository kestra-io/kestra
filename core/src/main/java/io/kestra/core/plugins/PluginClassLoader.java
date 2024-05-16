package io.kestra.core.plugins;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Enumeration;
import java.util.NoSuchElementException;
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

    // The default list of packages to delegate loading to parent classloader.
    private static Pattern DEFAULT_PACKAGES_TO_IGNORE = Pattern.compile("(?:"
        + "|io.kestra.core"
        + "|io.kestra.plugin.core"
        + "|org.slf4j"
        + ")\\..*$");

    private final ClassLoader parent;

    private final URL pluginLocation;

    private final ClassLoader systemClassLoader;

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
        this.systemClassLoader = getSystemClassLoader();
    }

    public String location() {
        return pluginLocation.toString();
    }

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

            if (loadedClass == null) {
                // protect from impersonation of system classes (e.g: java.*)
                loadedClass = mayLoadFromSystemClassLoader(name);
            }

            if (loadedClass == null && shouldLoadFromUrls(name)) {
                try {
                    // find the class from given jar urls
                    loadedClass = findClass(name);
                } catch (final ClassNotFoundException e) {
                    log.debug(
                        "Class '{}' not found on '{}' for plugin '{}', delegating to parent '{}'",
                        name,
                        this.getName(),
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
        return !DEFAULT_PACKAGES_TO_IGNORE.matcher(name).matches();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<URL> getResources(String name) throws IOException {
        Objects.requireNonNull(name);

        final Enumeration<URL>[] e = (Enumeration<URL>[]) new Enumeration<?>[3];

        // First, load resources from system class loader
        // e[0] = getResourcesFromSystem(name);

        // load resource from this classloader
        e[1] = findResources(name);

        // then try finding resources from parent class-loaders
        // e[2] = getParent().getResources(name);

        return new CompoundEnumeration<>(e);
    }

    private Enumeration<URL> getResourcesFromSystem(final String name) throws IOException {
        if (systemClassLoader != null) {
            return systemClassLoader.getResources(name);
        }
        return Collections.emptyEnumeration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public URL getResource(final String name) {
        Objects.requireNonNull(name);
        URL res = null;

        // if (systemClassLoader != null) {
        //     res = systemClassLoader.getResource(name);
        // }

        if (res == null) {
            res = findResource(name);
        }

        // if (res == null) {
        //     res = getParent().getResource(name);
        // }

        return res;
    }

    private Class<?> mayLoadFromSystemClassLoader(final String name) {
        Class<?> loadedClass = null;
        try {
            if (systemClassLoader != null) {
                loadedClass = systemClassLoader.loadClass(name);
            }
        } catch (final ClassNotFoundException ex) {
            // silently ignored
        }
        return loadedClass;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "PluginClassLoader[location=" + pluginLocation + "] ";
    }

    static final class CompoundEnumeration<E> implements Enumeration<E> {
        private final Enumeration<E>[] enums;
        private int index;

        CompoundEnumeration(Enumeration<E>[] enums) {
            this.enums = enums;
        }

        private boolean next() {
            while (index < enums.length) {
                if (enums[index] != null && enums[index].hasMoreElements()) {
                    return true;
                }
                index++;
            }
            return false;
        }

        public boolean hasMoreElements() {
            return next();
        }

        public E nextElement() {
            if (!next()) {
                throw new NoSuchElementException();
            }
            return enums[index].nextElement();
        }
    }
}
