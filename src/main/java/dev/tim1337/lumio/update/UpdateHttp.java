package dev.tim1337.lumio.update;

import java.util.function.Supplier;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The only way the updater reaches the network: GitHub's API for the lists of releases of the known repositories, and their release files from
 * github.com, followed only to GitHub's own hosts for release files. HTTPS only, short timeouts, no redirect anywhere else. Behind an interface
 * so the checks are tested without a network.
 */
interface UpdateHttp {
	/** An answer: its status, its ETag (or ""), and its body, which the caller closes. */
	record Answer(int status, String etag, InputStream body) {
	}

	/** One request without following its redirect: the status, the redirect's place (or null), the ETag and the body. */
	record Hop(int status, String location, String etag, InputStream body) {
	}

	/** Opens one request; {@link #follow} decides where it may go. */
	interface Opener {
		Hop open(URI uri, Map<String, String> headers) throws IOException;
	}

	/** The API's host and where release files are served from: the download address on github.com and the file hosts it sends to. */
	String API_HOST = "api.github.com";
	String DOWNLOAD_HOST = "github.com";
	Set<String> FILE_HOSTS = Set.of("objects.githubusercontent.com", "release-assets.githubusercontent.com");
	int TIMEOUT_MILLIS = 5_000;
	/** A download may take longer to read than a list. */
	int DOWNLOAD_READ_MILLIS = 30_000;
	int MOST_REDIRECTS = 4;
	int HTTPS_PORT = 443;

	/** @return the answer to a GET, following redirects only where {@link #allowed} lets it */
	Answer get(URI uri, Map<String, String> headers) throws IOException;

	/**
	 * @param first whether it is the address asked for (not a redirect): only the API's lists and the release files of the known repositories may
	 *              be asked for
	 * @return whether the updater may connect to an address: HTTPS to GitHub's API (the known repositories' releases), to their release files on
	 *         github.com, and on redirects to GitHub's file hosts
	 */
	static boolean allowed(URI uri, boolean first) {
		if (uri == null || !"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
			return false;
		}
		if (uri.getPort() != -1 && uri.getPort() != HTTPS_PORT) {
			return false;
		}
		String host = uri.getHost().toLowerCase(Locale.ROOT);
		String path = uri.getRawPath() == null ? "" : uri.getRawPath().toLowerCase(Locale.ROOT);
		if (host.equals(API_HOST)) {
			return first && UpdateSources.knownPath(path, UpdateSources.Source::releasesPrefix);
		}
		if (host.equals(DOWNLOAD_HOST)) {
			return UpdateSources.knownPath(path, UpdateSources.Source::downloadPrefix);
		}
		return !first && FILE_HOSTS.contains(host);
	}

	/**
	 * Follows a request through at most {@link #MOST_REDIRECTS} redirects, each place checked before it is opened.
	 *
	 * @param testServer the local test server of a jar that is no public release, or null: its addresses skip the check
	 */
	static Answer follow(URI uri, Map<String, String> headers, Opener opener, String testServer) throws IOException {
		URI at = uri;
		for (int hop = 0; hop <= MOST_REDIRECTS; hop++) {
			boolean test = testServer != null && at.toString().startsWith(testServer + "/");
			if (!test && !allowed(at, hop == 0)) {
				throw new IOException("Not allowed: " + at.getHost());
			}
			Hop answer = opener.open(at, headers);
			if (answer.status() >= 300 && answer.status() < 400 && answer.status() != HttpURLConnection.HTTP_NOT_MODIFIED) {
				if (answer.body() != null) {
					answer.body().close();
				}
				if (answer.location() == null) {
					throw new IOException("A redirect without a place");
				}
				at = at.resolve(answer.location());
				continue;
			}
			return new Answer(answer.status(), answer.etag() == null ? "" : answer.etag(), answer.body());
		}
		throw new IOException("Too many redirects");
	}

	/** The real network, through the JDK's connection, each redirect checked before it is followed. */
	final class Network implements UpdateHttp {
		/** The test server's address, honored only by a jar that is no public release (see {@link UpdateSources#testServer}); read per request. */
		private final Supplier<String> testServer;

		Network(Supplier<String> testServer) {
			this.testServer = testServer;
		}

		@Override
		public Answer get(URI uri, Map<String, String> headers) throws IOException {
			return follow(uri, headers, Network::open, testServer.get());
		}

		private static Hop open(URI at, Map<String, String> headers) throws IOException {
			HttpURLConnection connection = (HttpURLConnection) at.toURL().openConnection();
			connection.setInstanceFollowRedirects(false);
			connection.setConnectTimeout(TIMEOUT_MILLIS);
			connection.setReadTimeout(API_HOST.equals(at.getHost()) ? TIMEOUT_MILLIS : DOWNLOAD_READ_MILLIS);
			headers.forEach(connection::setRequestProperty);
			int status = connection.getResponseCode();
			if (status >= 300 && status < 400 && status != HttpURLConnection.HTTP_NOT_MODIFIED) {
				String location = connection.getHeaderField("Location");
				connection.disconnect();
				return new Hop(status, location, null, null);
			}
			InputStream body = status >= 400 ? connection.getErrorStream() : status == HttpURLConnection.HTTP_NOT_MODIFIED ? null : connection.getInputStream();
			return new Hop(status, null, connection.getHeaderField("ETag"), body);
		}
	}
}
