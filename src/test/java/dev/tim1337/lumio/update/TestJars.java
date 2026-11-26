package dev.tim1337.lumio.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small jars for the updater's tests: only a fabric.mod.json. */
final class TestJars {
	private TestJars() {
	}

	static String modJson(String id, String version, String minecraft, String lumioApi) {
		return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"" + version + "\",\"depends\":{\"fabricloader\":\">=0.20.1\",\"minecraft\":\""
			+ minecraft + "\",\"java\":\">=25\"" + (lumioApi == null ? "" : ",\"lumio-api\":\"" + lumioApi + "\"") + "}}";
	}

	static byte[] jar(String modJson) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("fabric.mod.json"));
			zip.write(modJson.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

	static String sha256(byte[] data) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
	}
}
