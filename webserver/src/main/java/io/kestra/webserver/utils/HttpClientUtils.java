package io.kestra.webserver.utils;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.http.HttpClient;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;

public class HttpClientUtils {
    public static HttpClient.Builder withPemCertificate(InputStream clientPemIs, InputStream caPem) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, KeyManagementException, UnrecoverableKeyException {
        PrivateKey privateKey = null;
        Certificate clientCertificate = null;

        // Parse the PEM content to extract certificate and private key
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(clientPemIs))) {
            JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter();
            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter();
            Object object;
            while ((object = pemParser.readObject()) != null) {
                if (object instanceof PrivateKeyInfo privateKeyInfo) {
                    privateKey = keyConverter.getPrivateKey(privateKeyInfo);
                } else if (object instanceof X509CertificateHolder) {
                    clientCertificate = certConverter.getCertificate((X509CertificateHolder) object);
                }
            }
        }

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        Certificate[] privateKeyCertificatesChain = new Certificate[]{clientCertificate};

        if (caPem != null) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            keyStore.setCertificateEntry("ca", cf.generateCertificate(caPem));
        }

        keyStore.setKeyEntry("client-key", privateKey, "".toCharArray(), privateKeyCertificatesChain);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return HttpClient.newBuilder().sslContext(sslContext);
    }
}
