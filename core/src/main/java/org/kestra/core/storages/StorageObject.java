package org.kestra.core.storages;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.io.CharStreams;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

public class StorageObject {
    private String type = StorageObject.class.getName();
    private URI uri;
    private StorageInterface storageInterface;

    public StorageObject(StorageInterface storageInterface, URI uri) {
        this.storageInterface = storageInterface;
        this.uri = uri;
    }

    public String getType() {
        return type;
    }

    public URI getUri() {
        return uri;
    }

    @JsonIgnore
    public String getContent() throws IOException {
        InputStreamReader inputStreamReader = new InputStreamReader(storageInterface.get(this.uri));
        String content = CharStreams.toString(inputStreamReader);
        inputStreamReader.close();;

        return content;
    }

    @JsonIgnore
    public InputStream getInputStream() throws FileNotFoundException {
        return storageInterface.get(this.uri);
    }

    @Override
    public String toString() {
        return this.uri.toString();
    }
}
