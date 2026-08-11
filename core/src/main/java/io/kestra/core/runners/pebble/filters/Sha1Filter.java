package io.kestra.core.runners.pebble.filters;

/**
 * This class implements the 'sha256' filter.
 *
 * @author Silviu Vergoti
 */
public class Sha1Filter extends ShaBaseFilter {

    public Sha1Filter() {
        super("SHA-1");
    }
}
