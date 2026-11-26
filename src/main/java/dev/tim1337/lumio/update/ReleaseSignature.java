package dev.tim1337.lumio.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * The release signature: every jar of a release has a file "<jar>.sig" beside it, the Ed25519 signature of the jar's bytes made with the key
 * whose private half lies only on the release PC (tools/release in the debug repo). A download is installed only when its signature matches
 * {@link #PUBLIC_KEY}; the JDK's own Ed25519, no library.
 */
final class ReleaseSignature {
	/** Lumio's release key: Ed25519, X.509, base64. A new key needs a release with it first, signed with the old one. */
	static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAnvVA2CqjxHCXt3OBgkEHDxOFZ2ucHz0Y4GkoVE9X2Q8=";
	/** The signature's file name after the jar's. */
	static final String SUFFIX = ".sig";
	/** A signature file is a line of base64 (88 characters for 64 bytes); anything longer is no signature. */
	static final int MOST_FILE_BYTES = 256;
	private static final String ALGORITHM = "Ed25519";
	private static final int BUFFER = 64 * 1024;

	private ReleaseSignature() {
	}

	/**
	 * @param signatureFile the text of the signature file: base64, white space around it allowed
	 * @param publicKey     the key, X.509 in base64
	 * @return whether the file's bytes carry this signature from this key; false for anything malformed
	 */
	static boolean verify(Path file, byte[] signatureFile, String publicKey) {
		try {
			byte[] signature = Base64.getDecoder().decode(new String(signatureFile, StandardCharsets.US_ASCII).strip());
			PublicKey key = KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
			Signature verifier = Signature.getInstance(ALGORITHM);
			verifier.initVerify(key);
			try (InputStream in = Files.newInputStream(file)) {
				byte[] buffer = new byte[BUFFER];
				int read;
				while ((read = in.read(buffer)) > 0) {
					verifier.update(buffer, 0, read);
				}
			}
			return verifier.verify(signature);
		} catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
			return false;
		}
	}
}
