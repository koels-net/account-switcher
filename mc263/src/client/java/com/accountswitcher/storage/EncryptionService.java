package com.accountswitcher.storage;

import com.accountswitcher.AccountSwitcherClient;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for access/refresh tokens.
 *
 * <p>The random data key lives in {@code encryption.dat}, itself wrapped so a copied file is not
 * usable on its own:
 * <ul>
 *   <li><b>Windows (preferred):</b> DPAPI ({@code CryptProtectData}) ties the wrap to the current
 *       Windows login account — copying the file to another user or machine makes it undecryptable.</li>
 *   <li><b>Fallback (non-Windows / DPAPI unavailable):</b> XOR against a machine-derived key. Weaker,
 *       but keeps a copied {@code accounts.json} alone unusable.</li>
 * </ul>
 * Neither protects against malware running as the same user — that can read the key alongside the
 * data, exactly as it could read the vanilla launcher's session.
 */
public final class EncryptionService {
	private static final String TRANSFORM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;
	private static final int KEY_BYTES = 32;

	/** Legacy container magic (XOR-only, no scheme byte). Still read for migration. */
	private static final byte[] MAGIC_V1 = {'A', 'S', 'E', '1'};
	/** Current container magic: MAGIC_V2 + scheme byte + payload. */
	private static final byte[] MAGIC_V2 = {'A', 'S', 'E', '2'};
	private static final byte SCHEME_XOR = 1;
	private static final byte SCHEME_DPAPI = 2;

	private static final String CRYPT32_UTIL = "com.sun.jna.platform.win32.Crypt32Util";

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public EncryptionService() {
		this.key = loadOrCreateKey();
	}

	public String encrypt(String plaintext) {
		if (plaintext == null || plaintext.isEmpty()) {
			return "";
		}
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORM);
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			ByteBuffer buffer = ByteBuffer.allocate(IV_BYTES + ciphertext.length);
			buffer.put(iv);
			buffer.put(ciphertext);
			return Base64.getEncoder().encodeToString(buffer.array());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt secret", e);
		}
	}

	public String decrypt(String encoded) {
		if (encoded == null || encoded.isEmpty()) {
			return "";
		}
		try {
			byte[] raw = Base64.getDecoder().decode(encoded);
			ByteBuffer buffer = ByteBuffer.wrap(raw);
			byte[] iv = new byte[IV_BYTES];
			buffer.get(iv);
			byte[] ciphertext = new byte[buffer.remaining()];
			buffer.get(ciphertext);
			Cipher cipher = Cipher.getInstance(TRANSFORM);
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		} catch (Exception e) {
			AccountSwitcherClient.LOGGER.warn("Failed to decrypt secret (corrupted or wrong key)");
			return "";
		}
	}

	private SecretKey loadOrCreateKey() {
		Path path = StoragePaths.encryptionFile();
		try {
			Files.createDirectories(path.getParent());
			if (Files.exists(path)) {
				byte[] data = Files.readAllBytes(path);
				byte scheme = schemeOf(data);
				byte[] keyBytes = readKey(data, scheme);
				// Upgrade legacy/XOR containers to DPAPI once it's available on this machine.
				if (scheme != SCHEME_DPAPI && dpapiAvailable()) {
					try {
						writeKey(path, keyBytes);
						AccountSwitcherClient.LOGGER.info("Upgraded encryption.dat key wrapping to Windows DPAPI");
					} catch (Exception e) {
						AccountSwitcherClient.LOGGER.warn("Could not upgrade encryption.dat to DPAPI; keeping existing wrap", e);
					}
				}
				return new SecretKeySpec(keyBytes, "AES");
			}

			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(256, new SecureRandom());
			SecretKey generated = generator.generateKey();
			writeKey(path, generated.getEncoded());
			return generated;
		} catch (Exception e) {
			throw new IllegalStateException(
					"Unable to initialize encryption key. If encryption.dat was copied from another "
							+ "Windows user/machine it cannot be decrypted; delete it and log in again.", e);
		}
	}

	private static byte schemeOf(byte[] data) throws IOException {
		if (startsWith(data, MAGIC_V2) && data.length > MAGIC_V2.length) {
			return data[MAGIC_V2.length];
		}
		if (startsWith(data, MAGIC_V1)) {
			return SCHEME_XOR;
		}
		throw new IOException("encryption.dat magic mismatch");
	}

	private static byte[] readKey(byte[] data, byte scheme) throws Exception {
		byte[] payload = startsWith(data, MAGIC_V2)
				? Arrays.copyOfRange(data, MAGIC_V2.length + 1, data.length)
				: Arrays.copyOfRange(data, MAGIC_V1.length, data.length);

		byte[] keyBytes = switch (scheme) {
			case SCHEME_DPAPI -> dpapiUnprotect(payload);
			case SCHEME_XOR -> xorUnwrap(payload);
			default -> throw new IOException("Unknown encryption.dat scheme " + scheme);
		};
		if (keyBytes == null || keyBytes.length != KEY_BYTES) {
			throw new IOException("Recovered key has wrong length");
		}
		return keyBytes;
	}

	private static void writeKey(Path path, byte[] keyBytes) throws Exception {
		byte scheme;
		byte[] payload;
		if (dpapiAvailable()) {
			byte[] blob = dpapiProtect(keyBytes);
			if (blob == null || blob.length == 0) {
				throw new IOException("DPAPI returned empty blob");
			}
			scheme = SCHEME_DPAPI;
			payload = blob;
		} else {
			scheme = SCHEME_XOR;
			payload = xorWrap(keyBytes);
		}

		ByteBuffer out = ByteBuffer.allocate(MAGIC_V2.length + 1 + payload.length);
		out.put(MAGIC_V2);
		out.put(scheme);
		out.put(payload);

		Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
		Files.write(tmp, out.array());
		try {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (Exception atomicFailed) {
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	// --- XOR machine-key wrap (fallback) -------------------------------------------------------

	private static byte[] xorWrap(byte[] keyBytes) throws Exception {
		byte[] wrapper = machineKey();
		byte[] out = new byte[keyBytes.length];
		for (int i = 0; i < keyBytes.length; i++) {
			out[i] = (byte) (keyBytes[i] ^ wrapper[i % wrapper.length]);
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] checksum = digest.digest(keyBytes);
		ByteBuffer buffer = ByteBuffer.allocate(out.length + checksum.length);
		buffer.put(out);
		buffer.put(checksum);
		return buffer.array();
	}

	private static byte[] xorUnwrap(byte[] wrapped) throws Exception {
		if (wrapped.length < KEY_BYTES + 32) {
			throw new IOException("Wrapped key truncated");
		}
		byte[] obfuscated = Arrays.copyOfRange(wrapped, 0, KEY_BYTES);
		byte[] checksum = Arrays.copyOfRange(wrapped, KEY_BYTES, KEY_BYTES + 32);
		byte[] wrapper = machineKey();
		byte[] keyBytes = new byte[KEY_BYTES];
		for (int i = 0; i < KEY_BYTES; i++) {
			keyBytes[i] = (byte) (obfuscated[i] ^ wrapper[i % wrapper.length]);
		}
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		byte[] expected = digest.digest(keyBytes);
		if (!MessageDigest.isEqual(expected, checksum)) {
			throw new IOException("encryption.dat integrity check failed");
		}
		return keyBytes;
	}

	private static byte[] machineKey() throws Exception {
		String material = System.getProperty("user.name", "user")
				+ '|' + System.getProperty("user.home", "home")
				+ '|' + System.getProperty("os.name", "os")
				+ "|account-switcher-v1";
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return digest.digest(material.getBytes(StandardCharsets.UTF_8));
	}

	// --- Windows DPAPI via JNA (already on the classpath through Minecraft's oshi dep) ----------

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase().contains("win");
	}

	private static boolean dpapiAvailable() {
		if (!isWindows()) {
			return false;
		}
		try {
			byte[] probe = dpapiProtect(new byte[]{0x41, 0x53, 0x00, 0x01});
			return probe != null && probe.length > 0;
		} catch (Throwable t) {
			return false;
		}
	}

	private static byte[] dpapiProtect(byte[] data) throws Exception {
		Method m = Class.forName(CRYPT32_UTIL).getMethod("cryptProtectData", byte[].class);
		return (byte[]) m.invoke(null, (Object) data);
	}

	private static byte[] dpapiUnprotect(byte[] data) throws Exception {
		Method m = Class.forName(CRYPT32_UTIL).getMethod("cryptUnprotectData", byte[].class);
		return (byte[]) m.invoke(null, (Object) data);
	}

	private static boolean startsWith(byte[] data, byte[] prefix) {
		if (data.length < prefix.length) {
			return false;
		}
		return Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
	}
}
