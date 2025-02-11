/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package sun.security.util;

import jdk.internal.access.SharedSecrets;

import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Objects;

public final class ECUtil {

    // Used by SunEC
    public static byte[] sArray(BigInteger s, ECParameterSpec params) {
        byte[] arr = s.toByteArray();
        ArrayUtil.reverse(arr);
        int byteLength = (params.getOrder().bitLength() + 7) / 8;
        byte[] arrayS = new byte[byteLength];
        int length = Math.min(byteLength, arr.length);
        System.arraycopy(arr, 0, arrayS, 0, length);
        return arrayS;
    }

    // Used by SunPKCS11 and SunJSSE.
    public static ECPoint decodePoint(byte[] data, EllipticCurve curve)
            throws IOException {
        if ((data.length == 0) || (data[0] != 4)) {
            throw new IOException("Only uncompressed point format supported");
        }
        // Per ANSI X9.62, an encoded point is a 1 byte type followed by
        // ceiling(log base 2 field-size / 8) bytes of x and the same of y.
        int n = (data.length - 1) / 2;
        if (n != ((curve.getField().getFieldSize() + 7 ) >> 3)) {
            throw new IOException("Point does not match field size");
        }

        byte[] xb = Arrays.copyOfRange(data, 1, 1 + n);
        byte[] yb = Arrays.copyOfRange(data, n + 1, n + 1 + n);

        return new ECPoint(new BigInteger(1, xb), new BigInteger(1, yb));
    }

    // Used by SunPKCS11 and SunJSSE.
    public static byte[] encodePoint(ECPoint point, EllipticCurve curve) {
        // get field size in bytes (rounding up)
        int n = (curve.getField().getFieldSize() + 7) >> 3;
        byte[] xb = trimZeroes(point.getAffineX().toByteArray());
        byte[] yb = trimZeroes(point.getAffineY().toByteArray());
        if ((xb.length > n) || (yb.length > n)) {
            throw new RuntimeException
                ("Point coordinates do not match field size");
        }
        byte[] b = new byte[1 + (n << 1)];
        b[0] = 4; // uncompressed
        System.arraycopy(xb, 0, b, n - xb.length + 1, xb.length);
        System.arraycopy(yb, 0, b, b.length - yb.length, yb.length);
        return b;
    }

    public static byte[] trimZeroes(byte[] b) {
        int i = 0;
        while ((i < b.length - 1) && (b[i] == 0)) {
            i++;
        }
        if (i == 0) {
            return b;
        }

        return Arrays.copyOfRange(b, i, b.length);
    }

    private static KeyFactory getKeyFactory() {
        try {
            return KeyFactory.getInstance("EC", "SunEC");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    public static ECPublicKey decodeX509ECPublicKey(byte[] encoded)
            throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);

        return (ECPublicKey)keyFactory.generatePublic(keySpec);
    }

    public static byte[] x509EncodeECPublicKey(ECPoint w,
            ECParameterSpec params) throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        ECPublicKeySpec keySpec = new ECPublicKeySpec(w, params);
        Key key = keyFactory.generatePublic(keySpec);

        return key.getEncoded();
    }

    public static ECPrivateKey decodePKCS8ECPrivateKey(byte[] encoded)
            throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
        try {
            return (ECPrivateKey) keyFactory.generatePrivate(keySpec);
        } finally {
            SharedSecrets.getJavaSecuritySpecAccess().clearEncodedKeySpec(keySpec);
        }
    }

    public static ECPrivateKey generateECPrivateKey(BigInteger s,
            ECParameterSpec params) throws InvalidKeySpecException {
        KeyFactory keyFactory = getKeyFactory();
        ECPrivateKeySpec keySpec = new ECPrivateKeySpec(s, params);

        return (ECPrivateKey)keyFactory.generatePrivate(keySpec);
    }

    public static AlgorithmParameters getECParameters() {
        try {
            return AlgorithmParameters.getInstance("EC");
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException(nsae);
        }
    }

    public static byte[] encodeECParameterSpec(ECParameterSpec spec) {
        AlgorithmParameters parameters = getECParameters();

        try {
            parameters.init(spec);
        } catch (InvalidParameterSpecException ipse) {
            throw new RuntimeException("Not a known named curve: " + spec);
        }

        try {
            return parameters.getEncoded();
        } catch (IOException ioe) {
            // it is a bug if this should happen
            throw new RuntimeException(ioe);
        }
    }

    public static ECParameterSpec getECParameterSpec(ECParameterSpec spec) {
        AlgorithmParameters parameters = getECParameters();

        try {
            parameters.init(spec);
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }
    }

    public static ECParameterSpec getECParameterSpec(byte[] params)
            throws IOException {
        AlgorithmParameters parameters = getECParameters();

        parameters.init(params);

        try {
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }
    }

    public static ECParameterSpec getECParameterSpec(String name) {
        AlgorithmParameters parameters = getECParameters();

        try {
            parameters.init(new ECGenParameterSpec(name));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }
    }

    public static ECParameterSpec getECParameterSpec(int keySize) {
        AlgorithmParameters parameters = getECParameters();

        try {
            parameters.init(new ECKeySizeParameterSpec(keySize));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }

    }

    public static String getCurveName(ECParameterSpec spec) {
        ECGenParameterSpec nameSpec;
        AlgorithmParameters parameters = getECParameters();

        try {
            parameters.init(spec);
            nameSpec = parameters.getParameterSpec(ECGenParameterSpec.class);
        } catch (InvalidParameterSpecException ipse) {
            return null;
        }

        if (nameSpec == null) {
            return null;
        }

        return nameSpec.getName();
    }

    public static boolean equals(ECParameterSpec spec1, ECParameterSpec spec2) {
        if (spec1 == spec2) {
            return true;
        }

        if (spec1 == null || spec2 == null) {
            return false;
        }
        return (spec1.getCofactor() == spec2.getCofactor() &&
                spec1.getOrder().equals(spec2.getOrder()) &&
                spec1.getCurve().equals(spec2.getCurve()) &&
                spec1.getGenerator().equals(spec2.getGenerator()));
    }


    // Convert the concatenation R and S in into their DER encoding
    public static byte[] encodeSignature(byte[] signature) throws SignatureException {

        try {

            int n = signature.length >> 1;
            byte[] bytes = new byte[n];
            System.arraycopy(signature, 0, bytes, 0, n);
            BigInteger r = new BigInteger(1, bytes);
            System.arraycopy(signature, n, bytes, 0, n);
            BigInteger s = new BigInteger(1, bytes);

            DerOutputStream out = new DerOutputStream(signature.length + 10);
            out.putInteger(r);
            out.putInteger(s);
            DerValue result =
                    new DerValue(DerValue.tag_Sequence, out.toByteArray());

            return result.toByteArray();

        } catch (Exception e) {
            throw new SignatureException("Could not encode signature", e);
        }
    }

    // Convert the DER encoding of R and S into a concatenation of R and S
    public static byte[] decodeSignature(byte[] sig) throws SignatureException {

        try {
            // Enforce strict DER checking for signatures
            DerInputStream in = new DerInputStream(sig, 0, sig.length, false);
            DerValue[] values = in.getSequence(2);

            // check number of components in the read sequence
            // and trailing data
            if ((values.length != 2) || (in.available() != 0)) {
                throw new IOException("Invalid encoding for signature");
            }

            BigInteger r = values[0].getPositiveBigInteger();
            BigInteger s = values[1].getPositiveBigInteger();

            // trim leading zeroes
            byte[] rBytes = trimZeroes(r.toByteArray());
            byte[] sBytes = trimZeroes(s.toByteArray());
            int k = Math.max(rBytes.length, sBytes.length);
            // r and s each occupy half the array
            byte[] result = new byte[k << 1];
            System.arraycopy(rBytes, 0, result, k - rBytes.length,
                    rBytes.length);
            System.arraycopy(sBytes, 0, result, result.length - sBytes.length,
                    sBytes.length);
            return result;

        } catch (Exception e) {
            throw new SignatureException("Invalid encoding for signature", e);
        }
    }

    /**
     * Check an ECPrivateKey to make sure the scalar value is within the
     * range of the order [1, n-1].
     *
     * @param prv the private key to be checked.
     *
     * @return the private key that was evaluated.
     *
     * @throws InvalidKeyException if the key's scalar value is not within
     *      the range 1 <= x < n where n is the order of the generator.
     */
    public static ECPrivateKey checkPrivateKey(ECPrivateKey prv)
            throws InvalidKeyException {
        // The private key itself cannot be null, but if the private
        // key doesn't divulge the parameters or more importantly the S value
        // (possibly because it lives on a provider that prevents release
        // of those values, e.g. HSM), then we cannot perform the check and
        // will allow the operation to proceed.
        Objects.requireNonNull(prv, "Private key must be non-null");
        ECParameterSpec spec = prv.getParams();
        if (spec != null) {
            BigInteger order = spec.getOrder();
            BigInteger sVal = prv.getS();

            if (order != null && sVal != null) {
                if (sVal.compareTo(BigInteger.ZERO) <= 0 ||
                        sVal.compareTo(order) >= 0) {
                    throw new InvalidKeyException("The private key must be " +
                            "within the range [1, n - 1]");
                }
            }
        }

        return prv;
    }

    // Partial Public key validation as described in NIST SP 800-186 Appendix D.1.1.1.
    // The extra step in the full validation (described in Appendix D.1.1.2) is implemented
    // as sun.security.ec.ECOperations#checkOrder inside the jdk.crypto.ec module.
    public static void validatePublicKey(ECPoint point, ECParameterSpec spec)
            throws InvalidKeyException {
        BigInteger p;
        if (spec.getCurve().getField() instanceof ECFieldFp f) {
            p = f.getP();
        } else {
            throw new InvalidKeyException("Only curves over prime fields are supported");
        }

        // 1. If Q is the point at infinity, output REJECT
        if (point.equals(ECPoint.POINT_INFINITY)) {
            throw new InvalidKeyException("Public point is at infinity");
        }
        // 2. Verify that x and y are integers in the interval [0, p-1]. Output REJECT if verification fails.
        BigInteger x = point.getAffineX();
        if (x.signum() < 0 || x.compareTo(p) >= 0) {
            throw new InvalidKeyException("Public point x is not in the interval [0, p-1]");
        }
        BigInteger y = point.getAffineY();
        if (y.signum() < 0 || y.compareTo(p) >= 0) {
            throw new InvalidKeyException("Public point y is not in the interval [0, p-1]");
        }
        // 3. Verify that (x, y) is a point on the W_a,b by checking that (x, y) satisfies the defining
        // equation y^2 = x^3 + a x + b where computations are carried out in GF(p). Output REJECT
        // if verification fails.
        BigInteger left = y.modPow(BigInteger.TWO, p);
        BigInteger right = x.pow(3).add(spec.getCurve().getA().multiply(x)).add(spec.getCurve().getB()).mod(p);
        if (!left.equals(right)) {
            throw new InvalidKeyException("Public point is not on the curve");
        }
    }

    private ECUtil() {}
}
