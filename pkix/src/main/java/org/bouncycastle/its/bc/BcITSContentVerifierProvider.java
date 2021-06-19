package org.bouncycastle.its.bc;

import java.io.IOException;
import java.io.OutputStream;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.nist.NISTNamedCurves;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.sec.SECObjectIdentifiers;
import org.bouncycastle.asn1.teletrust.TeleTrusTNamedCurves;
import org.bouncycastle.asn1.teletrust.TeleTrusTObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.signers.DSADigestSigner;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.its.ITSCertificate;
import org.bouncycastle.its.ITSContentVerifierProvider;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.oer.its.EccCurvePoint;
import org.bouncycastle.oer.its.EccP256CurvePoint;
import org.bouncycastle.oer.its.EccP384CurvePoint;
import org.bouncycastle.oer.its.EcdsaP256Signature;
import org.bouncycastle.oer.its.EcdsaP384Signature;
import org.bouncycastle.oer.its.PublicVerificationKey;
import org.bouncycastle.oer.its.Signature;
import org.bouncycastle.oer.its.ToBeSignedCertificate;
import org.bouncycastle.oer.its.VerificationKeyIndicator;
import org.bouncycastle.operator.ContentVerifier;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDefaultDigestProvider;
import org.bouncycastle.util.BigIntegers;

public class BcITSContentVerifierProvider
    implements ITSContentVerifierProvider
{
    private final ITSCertificate issuer;
    private final byte[] parentData;

    public BcITSContentVerifierProvider(ITSCertificate issuer)
        throws IOException
    {
        this.issuer = issuer;
        this.parentData = issuer.getEncoded();
    }

    public ITSCertificate getAssociatedCertificate()
    {
        return issuer;
    }

    public boolean hasAssociatedCertificate()
    {
        return issuer != null;
    }

    @Override
    public ContentVerifier get(final AlgorithmIdentifier verifierAlgorithmIdentifier)
        throws OperatorCreationException
    {

        // Get Issuer public key

        ToBeSignedCertificate toBeSignedCertificate =
            issuer.toASN1Structure().getCertificateBase().getToBeSignedCertificate();
        VerificationKeyIndicator vki = toBeSignedCertificate.getVerificationKeyIndicator();
        final AlgorithmIdentifier digestAlgo;
        final ECPublicKeyParameters pubParams;

        if (vki.getObject() instanceof PublicVerificationKey)
        {
            PublicVerificationKey pvi = PublicVerificationKey.getInstance(vki.getObject());
            X9ECParameters params;

            switch (pvi.getChoice())
            {
            case PublicVerificationKey.ecdsaNistP256:
                params = NISTNamedCurves.getByOID(SECObjectIdentifiers.secp256r1);
                digestAlgo = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
                break;
            case PublicVerificationKey.ecdsaBrainpoolP256r1:
                params = TeleTrusTNamedCurves.getByOID(TeleTrusTObjectIdentifiers.brainpoolP256r1);
                digestAlgo = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256);
                break;
            case PublicVerificationKey.ecdsaBrainpoolP384r1:
                params = TeleTrusTNamedCurves.getByOID(TeleTrusTObjectIdentifiers.brainpoolP384r1);
                digestAlgo = new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha384);
                break;
            default:
                throw new IllegalStateException("unknown key type");
            }
            ECCurve curve = params.getCurve();
            final EccCurvePoint itsPoint = pvi.getCurvePoint();
            byte[] key;

            if (itsPoint instanceof EccP256CurvePoint)
            {
                byte[] originalKey = itsPoint.getKeyBytes();
                key = new byte[originalKey.length + 1];
                System.arraycopy(originalKey, 0, key, 1, originalKey.length);
                switch (((EccP256CurvePoint)itsPoint).getChoice())
                {
                case EccP256CurvePoint.compressedY0:
                    key[0] = 0x02;
                    break;
                case EccP256CurvePoint.compressedY1:
                    key[0] = 0x03;
                    break;
                case EccP256CurvePoint.uncompressedP256:
                    key[0] = 0x04;
                    break;
                case EccP256CurvePoint.xOnly:
                    throw new IllegalStateException("x Only not implemented");
                }
            }
            else if (itsPoint instanceof EccP384CurvePoint)
            {
                byte[] originalKey = itsPoint.getKeyBytes();
                key = new byte[originalKey.length + 1];
                System.arraycopy(originalKey, 0, key, 1, originalKey.length);
                switch (((EccP384CurvePoint)itsPoint).getChoice())
                {
                case EccP384CurvePoint.compressedY0:
                    key[0] = 0x02;
                    break;
                case EccP384CurvePoint.compressedY1:
                    key[0] = 0x03;
                    break;
                case EccP384CurvePoint.uncompressedP384:
                    key[0] = 0x04;
                    break;
                case EccP384CurvePoint.xOnly:
                    throw new IllegalStateException("x Only not implemented");
                }
            }
            else
            {
                throw new IllegalStateException("unknown key type");
            }

            ECPoint point = curve.decodePoint(key).normalize();
            pubParams = new ECPublicKeyParameters(point,
                new ECDomainParameters(
                    params.getCurve(),
                    params.getG(),
                    params.getN(),
                    params.getH(),
                    params.getSeed()));

            final Digest digest = BcDefaultDigestProvider.INSTANCE.get(digestAlgo);

            final byte[] parentDigest = new byte[digest.getDigestSize()];

            digest.update(parentData, 0, parentData.length);

            digest.doFinal(parentDigest, 0);

            final OutputStream os = new OutputStream()
            {
                @Override
                public void write(int b)
                    throws IOException
                {
                    digest.update((byte)b);
                }

                @Override
                public void write(byte[] b)
                    throws IOException
                {
                    digest.update(b, 0, b.length);
                }

                @Override
                public void write(byte[] b, int off, int len)
                    throws IOException
                {
                    digest.update(b, off, len);
                }
            };

            return new ContentVerifier()
            {
                final DSADigestSigner signer = new DSADigestSigner(new ECDSASigner(),
                    BcDefaultDigestProvider.INSTANCE.get(digestAlgo));

                public AlgorithmIdentifier getAlgorithmIdentifier()
                {
                    return verifierAlgorithmIdentifier;
                }

                public OutputStream getOutputStream()
                {
                    return os;
                }
                
                public boolean verify(byte[] expected)
                {
                    Signature signature = Signature.getInstance(expected);

                    byte[] r;
                    byte[] s;
                    if (signature.getChoice() == Signature.ecdsaNistP256Signature || signature.getChoice() == Signature.ecdsaBrainpoolP256r1Signature)
                    {
                        EcdsaP256Signature sig = EcdsaP256Signature.getInstance(signature.getValue());
                        r = ASN1OctetString.getInstance(sig.getrSig().getValue()).getOctets();
                        s = sig.getsSig().getOctets();
                    }
                    else
                    {
                        EcdsaP384Signature sig = EcdsaP384Signature.getInstance(signature.getValue());
                        r = ASN1OctetString.getInstance(sig.getrSig().getValue()).getOctets();
                        s = sig.getsSig().getOctets();
                    }
                    byte[] enc;
                    try
                    {
                        enc = new DERSequence(new ASN1Encodable[] { new ASN1Integer(BigIntegers.fromUnsignedByteArray(r)),
                            new ASN1Integer(BigIntegers.fromUnsignedByteArray(s)) }).getEncoded();
                    }
                    catch (IOException ioException)
                    {
                        throw new RuntimeException("der encoding r & s");
                    }

                    byte[] clientCertDigest = new byte[digest.getDigestSize()];

                    digest.doFinal(clientCertDigest, 0);

                    signer.init(false, pubParams);

                    signer.update(clientCertDigest, 0, clientCertDigest.length);
                    
                    signer.update(parentDigest, 0, parentDigest.length);

                    return signer.verifySignature(enc);
                }
            };
        }
        else
        {
            throw new IllegalStateException("not public verification key");
        }
    }
}