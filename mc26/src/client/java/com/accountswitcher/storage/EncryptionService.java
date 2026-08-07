package com.accountswitcher.storage;

import com.accountswitcher.AccountSwitcherClient;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for access/refresh tokens.
 * Key material is stored in {@code encryption.dat}, wrapped with a machine-derived key.
 * This protects against casual theft of the config folder — not against malware on the same machine.
 */
public final class EncryptionService {
	private static final String TRANSFORM = "AES/GCM/NoPadding";
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_BYTES = 12;
	private static final int KEY_BYTES = 32;
	private static final byte[] MAGIC = new byte[]{'A', 'S', 'E', '1'};

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

	private static SecretKey loadOrCreateKey() {
		Path path = StoragePaths.encryptionFile();
		try {
			Files.createDirectories(path.getParent());
			if (Files.exists(path)) {
				byte[] data = Files.readAllBytes(path);
				if (data.length < MAGIC.length + KEY_BYTES + 32) {
					throw new IOException("encryption.dat too short");
				}
				if (!Arrays.equals(Arrays.copyOf(data, MAGIC.length), MAGIC)) {
					throw new IOException("encryption.dat magic mismatch");
				}
				byte[] wrapped = Arrays.copyOfRange(data, MAGIC.length, data.length);
				byte[] keyBytes = unwrap(wrapped);
				return new SecretKeySpec(keyBytes, "AES");
			}

			KeyGenerator generator = KeyGenerator.getInstance("AES");
			generator.init(256, new SecureRandom());
			SecretKey generated = generator.generateKey();
			byte[] wrapped = wrap(generated.getEncoded());
			ByteBuffer out = ByteBuffer.allocate(MAGIC.length + wrapped.length);
			out.put(MAGIC);
			out.put(wrapped);
			Files.write(path, out.array(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			return generated;
		} catch (Exception e) {
			throw new IllegalStateException("Unable to initialize encryption key", e);
		}
	}

	private static byte[] wrap(byte[] keyBytes) throws Exception {
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

	private static byte[] unwrap(byte[] wrapped) throws Exception {
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

	/**
	 * Machine-derived material so a stolen encryption.dat alone is not enough
	 * without also matching this environment. Not a TPM / OS keystore.
	 */
	private static byte[] machineKey() throws Exception {
		String material = System.getProperty("user.name", "user")
				+ '|' + System.getProperty("user.home", "home")
				+ '|' + System.getProperty("os.name", "os")
				+ "|account-switcher-v1";
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return digest.digest(material.getBytes(StandardCharsets.UTF_8));
	}
}
