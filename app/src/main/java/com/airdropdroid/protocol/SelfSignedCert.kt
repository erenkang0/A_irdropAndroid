package com.airdropdroid.protocol

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/** Oturum başına üretilen geçici self-signed RSA sertifika çifti. */
class SelfSignedCert private constructor(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {
    companion object {
        fun generate(): SelfSignedCert {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply {
                initialize(2048)
            }.generateKeyPair()

            val now = System.currentTimeMillis()
            val notBefore = Date(now - 60_000)
            val notAfter = Date(now + 365L * 24 * 60 * 60 * 1000)
            val subject = X500Name("CN=AirDropAndroid")

            val builder = JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                notBefore,
                notAfter,
                subject,
                keyPair.public,
            )
            val signer = JcaContentSignerBuilder("SHA256WithRSA").build(keyPair.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            return SelfSignedCert(keyPair.private, cert)
        }
    }
}
