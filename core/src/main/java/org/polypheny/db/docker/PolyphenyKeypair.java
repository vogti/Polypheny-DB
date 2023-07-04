package org.polypheny.db.docker;

import java.io.IOException;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;

final class PolyphenyKeypair {

    private final X509CertificateHolder cert;
    private final AsymmetricKeyParameter key;

    /**
     * Cache value for DER encoded certificate.  This is not done for
     * performance, but rather so that callers of
     * getEncodedCertificate() do not have to handle a potential
     * IOException.
     */
    private final transient byte[] derEncoding;


    public PolyphenyKeypair( X509CertificateHolder cert, AsymmetricKeyParameter key ) throws IOException {
        this.cert = cert;
        this.key = key;
        this.derEncoding = cert.toASN1Structure().getEncoded( "DER" );
    }


    public void saveToDiskOverwrite( String certfile, String keyfile ) throws IOException {
        PolyphenyCertificateUtils.saveAsPemOverwrite( certfile, "CERTIFICATE", cert.toASN1Structure().getEncoded( "DER" ) );

        // XXX: If we would save pkinfo1 directly, it would not be
        // understood by the openssl(1).  With this dance,
        // so openssl(1) can read the key.
        PrivateKeyInfo pkinfo1 = PrivateKeyInfoFactory.createPrivateKeyInfo( key );
        PrivateKeyInfo pkinfo = new PrivateKeyInfo( pkinfo1.getPrivateKeyAlgorithm(), pkinfo1.parsePrivateKey() );
        PolyphenyCertificateUtils.saveAsPemOverwrite( keyfile, "PRIVATE KEY", pkinfo.getEncoded( "DER" ) );
    }


    public static PolyphenyKeypair loadFromDisk( String certfile, String keyfile ) throws IOException {
        byte[] rawKey = PolyphenyCertificateUtils.loadPemFromFile( keyfile, "PRIVATE KEY" );
        AsymmetricKeyParameter sk = PrivateKeyFactory.createKey( rawKey );

        byte[] rawCertificate = PolyphenyCertificateUtils.loadPemFromFile( certfile, "CERTIFICATE" );
        X509CertificateHolder cert = new X509CertificateHolder( rawCertificate );

        return new PolyphenyKeypair( cert, sk );
    }


    public Certificate toASN1Structure() {
        return cert.toASN1Structure();
    }


    /**
     * Returns the DER encoded certificate
     */
    public byte[] getEncodedCertificate() {
        return derEncoding;
    }


    public AsymmetricKeyParameter getPrivateKey() {
        return key;
    }


    /**
     * This exists only because we need it in the TLS client.  If
     * there ever is a way, that the TlsCredentialedSigner can
     * determine this automatically, it should be used.
     */
    public SignatureAndHashAlgorithm getSignatureAndHashAlgorithm() {
        if ( key instanceof Ed25519PrivateKeyParameters ) {
            return SignatureAndHashAlgorithm.ed25519;
        }
        return null;
    }

}
