package org.kestra.core.serializers.helpers;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class FileTemplateLoader extends com.github.jknack.handlebars.io.FileTemplateLoader {
    public FileTemplateLoader(String basedir, String suffix) {
        super(basedir, suffix);
    }

    @Override
    protected URL getResource(final String location) throws IOException {
        File file = new File(location);
        if (!file.exists()) {
            throw new IOException("File not found at location '" + file.getAbsolutePath() + "'");
        }

        return file.toURI().toURL();
    }
}
