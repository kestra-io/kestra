package io.kestra.core.runners.pebble.filters;

/**
 * This class implements the 'sha256' filter.
 *
 * @author Silviu Vergoti
 */
public class Sha512Filter extends ShaBaseFilter {

    public Sha512Filter() {
        super("SHA-512");
    }
}
