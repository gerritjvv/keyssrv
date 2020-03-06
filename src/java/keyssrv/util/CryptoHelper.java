package keyssrv.util;

import at.favre.lib.crypto.HKDF;
import crypto.Key;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Collection;


public class CryptoHelper {

    /**
     * Return true of the version field is not set to zero
     * @param bts
     * @return
     */
    public static final boolean oldGenEnc(byte[] bts) {
        return bts[0] != 0;
    }

    public static final byte[] extractKey(byte[] salt, byte[] pass) {
        return HKDF.fromHmacSha512().extract(salt, pass);
    }

    /**
     * @return
     */
    public static final Key.ExpandedKey createKey(byte[] pass) {
        return Key.KeySize.AES_256.genKeysHmacSha(
                 pass
        );
    }

    /**
     * Generates a secure random key
     */
    public static final byte[] genKey(long size) {
        byte[] k = new byte[(int) size];
        crypto.Random.nextBytes(k);

        return k;
    }

    /**
     * Generates a secure random key of 64 bytes
     */
    public static final byte[] genKey() {
        return genKey(64);
    }


    public static final boolean isBcryptHash(byte[] bts) {
        if (bts.length > 7) {
            return bts[0] == 'b'
                    && bts[1] == 'c'
                    && bts[2] == 'r'
                    && bts[3] == 'y'
                    && bts[4] == 'p'
                    && bts[5] == 't'
                    && bts[6] == '+';
        }

        return false;
    }

    public static final String sha256Hex(String salt, Collection<Object> input) {

        StringBuilder buff = new StringBuilder();
        buff.append(salt);

        for(Object obj : input) {
            buff.append(obj == null ? "" : obj.toString());
        }

        return DigestUtils.sha256Hex(buff.toString());
    }

    public static final boolean compareHexHash(String salt, Collection<Object> input, String hash) {
        StringBuilder buff = new StringBuilder();
        buff.append(salt);

        for(Object obj : input) {
            buff.append(obj == null ? "" : obj.toString());
        }

        return DigestUtils.sha256Hex(buff.toString()).equals(hash);
    }
}
