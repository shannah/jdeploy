package ca.weblite.jdeploy.services;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import ove.crypto.digest.Blake2b;
import com.iwebpp.crypto.TweetNaclFast;

import javax.inject.Singleton;


/**
 * Example how to open sealed boxes in pure java (libsodium sealed boxes according to
 * https://download.libsodium.org/doc/public-key_cryptography/sealed_boxes.html)
 *
 * Has a dependency on TweetNaclFast and Blake2B, for example
 *
 * https://github.com/alphazero/Blake2b
 * and
 * https://github.com/InstantWebP2P/tweetnacl-java
 *
 */
@Singleton
public class SealedBoxUtility {


    public static final int crypto_box_NONCEBYTES = 24;
    public static final int crypto_box_PUBLICKEYBYTES = 32;
    public static final int crypto_box_MACBYTES = 16;
    public static final int crypto_box_SEALBYTES = (crypto_box_PUBLICKEYBYTES + crypto_box_MACBYTES);

//  libsodium
//  int crypto_box_seal(unsigned char *c, const unsigned char *m,
//            unsigned long long mlen, const unsigned char *pk);


    /**
     * Encrypt in  a sealed box
     *
     * @param clearText clear text
     * @param receiverPubKey receiver public key
     * @return encrypted message
     * @throws GeneralSecurityException
     */
    public byte[] crypto_box_seal(byte[] clearText, byte[] receiverPubKey) throws GeneralSecurityException {

        // create ephemeral keypair for sender
        TweetNaclFast.Box.KeyPair ephkeypair = TweetNaclFast.Box.keyPair();
        // create nonce
        byte[] nonce = crypto_box_seal_nonce(ephkeypair.getPublicKey(), receiverPubKey);
        TweetNaclFast.Box box = new TweetNaclFast.Box(receiverPubKey, ephkeypair.getSecretKey());
        byte[] ciphertext = box.box(clearText, nonce);
        if (ciphertext == null) throw new GeneralSecurityException("could not create box");

        byte[] sealedbox = new byte[ciphertext.length + crypto_box_PUBLICKEYBYTES];
        byte[] ephpubkey = ephkeypair.getPublicKey();
        for (int i = 0; i < crypto_box_PUBLICKEYBYTES; i ++)
            sealedbox[i] = ephpubkey[i];

        for(int i = 0; i < ciphertext.length; i ++)
            sealedbox[i+crypto_box_PUBLICKEYBYTES]=ciphertext[i];

        return sealedbox;
    }

//  libsodium:
//      int
//      crypto_box_seal_open(unsigned char *m, const unsigned char *c,
//                           unsigned long long clen,
//                           const unsigned char *pk, const unsigned char *sk)

    /**
     * Decrypt a sealed box
     *
     * @param c ciphertext
     * @param pk receiver public key
     * @param sk receiver secret key
     * @return decrypted message
     * @throws GeneralSecurityException
     */
    public byte[] crypto_box_seal_open( byte[]c, byte[] pk, byte[]sk ) throws GeneralSecurityException{
        if ( c.length < crypto_box_SEALBYTES) throw new IllegalArgumentException("Ciphertext too short");

        byte[] pksender = Arrays.copyOfRange(c, 0, crypto_box_PUBLICKEYBYTES);
        byte[] ciphertextwithmac = Arrays.copyOfRange(c, crypto_box_PUBLICKEYBYTES , c.length);
        byte[] nonce = crypto_box_seal_nonce(pksender,pk);

        TweetNaclFast.Box box = new TweetNaclFast.Box(pksender, sk);
        byte[] cleartext = box.open(ciphertextwithmac, nonce);
        if (cleartext == null) throw new GeneralSecurityException("could not open box");
        return cleartext;
    }


    /**
     *  hash the combination of senderpk + mypk into nonce using blake2b hash
     * @param senderpk the senders public key
     * @param mypk my own public key
     * @return the nonce computed using Blake2b generic hash
     */
    public byte[] crypto_box_seal_nonce(byte[] senderpk, byte[] mypk){
// C source ported from libsodium
//      crypto_generichash_state st;
//
//      crypto_generichash_init(&st, NULL, 0U, crypto_box_NONCEBYTES);
//      crypto_generichash_update(&st, pk1, crypto_box_PUBLICKEYBYTES);
//      crypto_generichash_update(&st, pk2, crypto_box_PUBLICKEYBYTES);
//      crypto_generichash_final(&st, nonce, crypto_box_NONCEBYTES);
//
//      return 0;
        final Blake2b blake2b = Blake2b.Digest.newInstance( crypto_box_NONCEBYTES );
        blake2b.update(senderpk);
        blake2b.update(mypk);
        byte[] nonce = blake2b.digest();
        if (nonce == null || nonce.length!=crypto_box_NONCEBYTES) throw new IllegalArgumentException("Blake2b hashing failed");
        return nonce;


    }

}
