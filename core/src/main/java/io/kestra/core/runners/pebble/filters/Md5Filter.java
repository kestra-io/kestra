package io.kestra.core.runners.pebble.filters;

/**
 * This class implements the 'sha256' filter.
 *
 * @author Silviu Vergoti
 */
public class Md5Filter extends ShaBaseFilter {

    public Md5Filter() {
        super("MD5");
    }
}
