package com.sinproxy.android.security;

import com.sinproxy.android.SINProxyConfig;
import com.sinproxy.android.utils.SINLog;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Dynamic Certificate Management using Bouncy Castle
 * Handles Root CA and per-host certificate generation for MITM
 */
public class CertificateHelper {
    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static X509Certificate rootCA;
    private static PrivateKey caPrivateKey;

    /**
     * Initializes the Root CA from memory or creates a new one
     */
    public static void ensureCA() throws Exception {
        if (rootCA != null && caPrivateKey != null) return;

        SINLog.i("Initializing Root CA for SINproxy...");
        KeyPair keyPair = generateKeyPair();
        caPrivateKey = keyPair.getPrivate();

        X500Name issuer = new X500Name("CN=" + SINProxyConfig.CA_COMMON_NAME);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365 * 10); // 10 years

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());

        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC").build(caPrivateKey);

        rootCA = new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(signer));
        
        SINLog.i("Root CA initialized successfully.");
    }

    /**
     * Generates an in-memory certificate for a target host, signed by the Root CA
     */
    public static X509Certificate generateHostCertificate(String host, PublicKey publicKey) throws Exception {
        ensureCA();

        X500Name subject = new X500Name("CN=" + host);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365); // 1 year

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(rootCA.getSubjectX500Principal().getName()),
                serial, notBefore, notAfter, subject, publicKey);

        // Add SAN (Subject Alternative Name)
        GeneralNames subjectAltName = new GeneralNames(new GeneralName(GeneralName.dNSName, host));
        builder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption")
                .setProvider("BC").build(caPrivateKey);

        return new JcaX509CertificateConverter().setProvider("BC")
                .getCertificate(builder.build(signer));
    }

    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(SINProxyConfig.CERT_ALGORITHM, "BC");
        keyGen.initialize(SINProxyConfig.KEY_SIZE, new SecureRandom());
        return keyGen.generateKeyPair();
    }

    public static X509Certificate getRootCA() {
        return rootCA;
    }
}
