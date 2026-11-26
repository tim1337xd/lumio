package dev.tim1337.lumio.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.util.AddonUpdates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Only GitHub's API and the release files of the known repositories, redirected only to GitHub's file hosts, over HTTPS; only known
 * repositories; a test server never in a public release.
 */
class UpdateSourcesTest {
	@Test
	void onlyGitHubsHosts() {
		assertTrue(UpdateHttp.allowed(URI.create("https://api.github.com/repos/tim1337xd/lumio/releases"), true));
		assertTrue(UpdateHttp.allowed(URI.create("https://github.com/tim1337xd/lumio/releases/download/v1/x.jar"), true));
		assertTrue(UpdateHttp.allowed(URI.create("https://release-assets.githubusercontent.com/x"), false), "a redirect to the file host");
		assertFalse(UpdateHttp.allowed(URI.create("https://release-assets.githubusercontent.com/x"), true), "never asked for directly");
		assertFalse(UpdateHttp.allowed(URI.create("http://github.com/x"), true), "HTTPS only");
		assertFalse(UpdateHttp.allowed(URI.create("https://evil.example/x"), false));
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com.evil.example/x"), false));
		assertFalse(UpdateHttp.allowed(URI.create("https://api.github.com/x"), false), "the API is no redirect target");
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com:8443/tim1337xd/lumio/releases/download/v1/x.jar"), true));
	}

	@Test
	void onlyTheKnownRepositoriesPaths() {
		assertTrue(UpdateHttp.allowed(URI.create("https://github.com/tim1337xd/Lumio-SkyBlock/releases/download/v1/x.jar"), true));
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com/someone/lumio/releases/download/v1/x.jar"), true), "another owner");
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com/tim1337xd/lumio-evil/releases/download/v1/x.jar"), true), "another repository");
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com/tim1337xd/lumio/archive/main.zip"), true), "no release file");
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com/tim1337xd/lumio/releases/download/../../../someone/x/releases/download/x.jar"), true));
		assertFalse(UpdateHttp.allowed(URI.create("https://github.com/tim1337xd/lumio/releases/download/%2e%2e/x.jar"), true));
		assertFalse(UpdateHttp.allowed(URI.create("https://api.github.com/repos/someone/other/releases"), true), "the API only for the known repositories");
		assertFalse(UpdateHttp.allowed(URI.create("https://api.github.com/user"), true));
	}

	@Test
	void aRedirectToAnotherHostIsNeverOpened() {
		List<URI> opened = new ArrayList<>();
		URI start = URI.create("https://github.com/tim1337xd/lumio/releases/download/v1/lumio-1.0.0-26.3.jar");
		UpdateHttp.Opener redirecting = (uri, headers) -> {
			opened.add(uri);
			return new UpdateHttp.Hop(302, "https://evil.example/lumio.jar", null, null);
		};
		assertThrows(IOException.class, () -> UpdateHttp.follow(start, Map.of(), redirecting, null));
		assertEquals(List.of(start), opened, "the other host was never contacted");
		opened.clear();
		UpdateHttp.Opener toHttp = (uri, headers) -> {
			opened.add(uri);
			return new UpdateHttp.Hop(302, "http://release-assets.githubusercontent.com/x", null, null);
		};
		assertThrows(IOException.class, () -> UpdateHttp.follow(start, Map.of(), toHttp, null), "no step down to HTTP");
		assertEquals(1, opened.size());
	}

	@Test
	void aRedirectToGitHubsFileHostIsFollowed() throws Exception {
		List<URI> opened = new ArrayList<>();
		UpdateHttp.Opener files = (uri, headers) -> {
			opened.add(uri);
			return uri.getHost().equals("github.com") ? new UpdateHttp.Hop(302, "https://release-assets.githubusercontent.com/abc?sig=1", null, null)
				: new UpdateHttp.Hop(200, null, "", new ByteArrayInputStream(new byte[] {1}));
		};
		UpdateHttp.Answer answer = UpdateHttp.follow(URI.create("https://github.com/tim1337xd/lumio/releases/download/v1/x.jar"), Map.of(), files, null);
		assertEquals(200, answer.status());
		assertEquals(2, opened.size());
		UpdateHttp.Opener loop = (uri, headers) -> new UpdateHttp.Hop(302, uri.toString(), null, null);
		assertThrows(IOException.class, () -> UpdateHttp.follow(URI.create("https://github.com/tim1337xd/lumio/releases/download/v1/x.jar"), Map.of(), loop, null),
			"at most a few redirects");
	}

	@Test
	void onlyKnownRepositories() {
		List<UpdateSources.Source> sources = UpdateSources.all(List.of(new AddonUpdates.Source("lumio-skyblock", "tim1337xd", "Lumio-SkyBlock", "lumio-skyblock"),
			new AddonUpdates.Source("other", "someone", "other", "other"), new AddonUpdates.Source("lumio-skyblock", "someone", "lumio-skyblock", "lumio-skyblock")));
		assertEquals(List.of(UpdateSources.LUMIO, UpdateSources.KNOWN_ADDONS.getFirst()), sources);
	}

	@Test
	void aReleaseBuildNeverTakesTheTestKey(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
		java.security.KeyPair test = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
		String testKey = java.util.Base64.getEncoder().encodeToString(test.getPublic().getEncoded());
		String server = "http://127.0.0.1:8123";
		assertEquals(ReleaseSignature.PUBLIC_KEY, UpdateSources.releaseKey(true, server, testKey), "a release: its own key alone");
		assertEquals(ReleaseSignature.PUBLIC_KEY, UpdateSources.releaseKey(false, null, testKey), "no test server: the release key");
		assertEquals(ReleaseSignature.PUBLIC_KEY, UpdateSources.releaseKey(false, server, null), "no test key: the release key");
		assertEquals(testKey, UpdateSources.releaseKey(false, server, testKey), "a developer's build with the debug add-on's server and key");
		// a file the test server signed: a developer's build takes it, a release build rejects it
		java.nio.file.Path file = java.nio.file.Files.write(dir.resolve("lumio-9.9.9-26.3.jar"), new byte[] {1, 2, 3});
		java.security.Signature signer = java.security.Signature.getInstance("Ed25519");
		signer.initSign(test.getPrivate());
		signer.update(new byte[] {1, 2, 3});
		byte[] signature = java.util.Base64.getEncoder().encode(signer.sign());
		assertTrue(ReleaseSignature.verify(file, signature, UpdateSources.releaseKey(false, server, testKey)));
		assertFalse(ReleaseSignature.verify(file, signature, UpdateSources.releaseKey(true, server, testKey)), "a release build rejects the test key's signature");
		assertFalse(ReleaseSignature.verify(file, signature, UpdateSources.releaseKey(true, UpdateSources.testServer(true, server), testKey)));
	}

	@Test
	void aTestServerNeverInARelease() {
		assertNull(UpdateSources.testServer(true, "http://127.0.0.1:8123"));
		assertEquals("http://127.0.0.1:8123", UpdateSources.testServer(false, "http://127.0.0.1:8123"));
		assertNull(UpdateSources.testServer(false, "http://example.com:80"), "only this computer");
		assertNull(UpdateSources.testServer(false, null));
		assertTrue(UpdateHttp.allowed(URI.create("https://api.github.com/repos/tim1337xd/lumio/releases?per_page=20"), true));
	}
}
