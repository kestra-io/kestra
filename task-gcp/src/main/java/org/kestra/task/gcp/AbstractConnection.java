package org.kestra.task.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class AbstractConnection {
    public GoogleCredentials credentials(String serviceAccount) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(serviceAccount.getBytes());
        try {
            return ServiceAccountCredentials.fromStream(byteArrayInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
