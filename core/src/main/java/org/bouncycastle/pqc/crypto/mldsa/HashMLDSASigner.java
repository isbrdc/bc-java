package org.bouncycastle.pqc.crypto.mldsa;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.security.SecureRandom;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.CryptoException;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.Signer;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.pqc.crypto.DigestUtils;


public class HashMLDSASigner
    implements Signer
{
    private MLDSAPrivateKeyParameters privKey;
    private MLDSAPublicKeyParameters pubKey;

    private SecureRandom random;
    private final Digest digest;
    private final byte[] oidEncoding;



    public HashMLDSASigner(Digest digest, ASN1ObjectIdentifier digestOid) throws IOException
    {
        this.digest = digest;
        this.oidEncoding = digestOid.getEncoded(ASN1Encoding.DER);
    }
    public HashMLDSASigner(Digest digest) throws IOException
    {
        this(digest, DigestUtils.getDigestOid(digest.getAlgorithmName()));
    }

    public void init(boolean forSigning, CipherParameters param)
    {
        if (forSigning)
        {
            if (param instanceof ParametersWithRandom)
            {
                privKey = (MLDSAPrivateKeyParameters)((ParametersWithRandom)param).getParameters();
                random = ((ParametersWithRandom)param).getRandom();
            }
            else
            {
                privKey = (MLDSAPrivateKeyParameters)param;
                random = null;
            }
        }
        else
        {
            pubKey = (MLDSAPublicKeyParameters)param;
        }

        reset();

    }

    public void update(byte b)
    {
        digest.update(b);
    }

    @Override
    public void update(byte[] in, int off, int len)
    {
        digest.update(in, off, len);
    }

    @Override
    public byte[] generateSignature() throws CryptoException, DataLengthException
    {
        MLDSAEngine engine = privKey.getParameters().getEngine(random);

        if (!engine.isPreHash())
        {
            throw new InvalidParameterException("pre-hash ml-dsa must use non \"pure\" parameters.");
        }

        byte[] ctx = privKey.getContext();
        if (ctx.length > 255)
        {
            throw new RuntimeException("Context too long");
        }

        byte[] rnd = new byte[MLDSAEngine.RndBytes];
        if (random != null)
        {
            random.nextBytes(rnd);
        }

        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);

        byte[] ds_message = new byte[1 + 1 + ctx.length + + oidEncoding.length + hash.length];
        ds_message[0] = 1;
        ds_message[1] = (byte)ctx.length;
        System.arraycopy(ctx, 0, ds_message, 2, ctx.length);
        System.arraycopy(oidEncoding, 0, ds_message, 2 + ctx.length, oidEncoding.length);
        System.arraycopy(hash, 0, ds_message, 2 + ctx.length + oidEncoding.length, hash.length);

        return engine.signInternal(ds_message, ds_message.length, privKey.rho, privKey.k, privKey.tr, privKey.t0, privKey.s1, privKey.s2, rnd);
    }

    @Override
    public boolean verifySignature(byte[] signature)
    {
        MLDSAEngine engine = pubKey.getParameters().getEngine(random);

        if (!engine.isPreHash())
        {
            throw new InvalidParameterException("pre-hash ml-dsa must use non \"pure\" parameters");
        }

        byte[] ctx = pubKey.getContext();
        if (ctx.length > 255)
        {
            throw new RuntimeException("Context too long");
        }

        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);

        byte[] ds_message = new byte[1 + 1 + ctx.length + + oidEncoding.length + hash.length];
        ds_message[0] = 1;
        ds_message[1] = (byte)ctx.length;
        System.arraycopy(ctx, 0, ds_message, 2, ctx.length);
        System.arraycopy(oidEncoding, 0, ds_message, 2 + ctx.length, oidEncoding.length);
        System.arraycopy(hash, 0, ds_message, 2 + ctx.length + oidEncoding.length, hash.length);

        return engine.verifyInternal(signature, signature.length, ds_message, ds_message.length, pubKey.rho, pubKey.t1);
    }

    /**
     * reset the internal state
     */
    @Override
    public void reset()
    {
        digest.reset();
    }


    public byte[] internalGenerateSignature(byte[] message, byte[] random)
    {
        MLDSAEngine engine = privKey.getParameters().getEngine(this.random);

        if (!engine.isPreHash())
        {
            throw new InvalidParameterException("pre-hash ml-dsa must use non \"pure\" parameters");
        }
        return engine.signInternal(message, message.length, privKey.rho, privKey.k, privKey.tr, privKey.t0, privKey.s1, privKey.s2, random);
    }

    public boolean internalVerifySignature(byte[] message, byte[] signature)
    {
        MLDSAEngine engine = pubKey.getParameters().getEngine(random);

        if (!engine.isPreHash())
        {
            throw new InvalidParameterException("pre-hash ml-dsa must use non \"pure\" parameters");
        }

        return engine.verifyInternal(signature, signature.length, message, message.length, pubKey.rho, pubKey.t1);
    }
}
