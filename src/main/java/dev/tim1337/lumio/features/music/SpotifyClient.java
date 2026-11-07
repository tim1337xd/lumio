package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.api.experimental.util.HttpLimits;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.ui.Toasts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Reads the playing song from the Spotify Web API. Spotify requires a one-time browser sign-in: a local server reachable only from this computer
 * receives the callback (Authorization Code with PKCE, which needs no client secret), and the resulting tokens are stored in a file so this only
 * happens once.
 * <p>
 * A Client ID is required: the user creates an app at developer.spotify.com (free), adds {@link #REDIRECT} as its redirect URI, and copies its
 * Client ID into the settings.
 */
final class SpotifyClient {
	static final int PORT = 48721;
	static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
	private static final String SCOPE = "user-read-currently-playing user-read-playback-state user-modify-playback-state";
	/** The most browser connections the sign-in answers at once; more are closed right away. */
	private static final int MOST_CONNECTIONS = 4;

	/** Result of a poll. */
	record Playing(MusicService.Track track, boolean playing, long positionMs) {
	}

	private static volatile String accessToken = "";
	private static volatile long accessUntil;
	private static volatile String refreshToken = "";
	private static volatile boolean tokensLoaded;
	private static volatile String clientId = "";
	private static volatile String message = "";
	private static volatile long waitUntil;
	private static ServerSocket server;

	private SpotifyClient() {
	}

	private static Path file() {
		return ModFiles.file("spotify.json");
	}

	static String message() {
		return message;
	}

	static void setClientId(String id) {
		String trimmed = id == null ? "" : id.trim();
		if (!trimmed.equals(clientId)) {
			clientId = trimmed;
			accessToken = "";
			accessUntil = 0;
		}
	}

	static boolean connected() {
		// the saved sign-in is read once, not on every call: the settings page asks every frame
		if (!tokensLoaded) {
			tokensLoaded = true;
			loadTokens();
		}
		return !refreshToken.isEmpty();
	}

	private static void loadTokens() {
		try {
			Path file = file();
			if (Files.exists(file)) {
				JsonObject object = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				refreshToken = object.has("refresh") ? object.get("refresh").getAsString() : "";
			}
		} catch (Exception ignored) {
		}
	}

	private static void saveTokens() {
		try {
			JsonObject object = new JsonObject();
			object.addProperty("refresh", refreshToken);
			ModFiles.writeAtomically(file(), object.toString());
		} catch (IOException ignored) {
		}
	}

	static void disconnect() {
		refreshToken = "";
		accessToken = "";
		accessUntil = 0;
		try {
			Files.deleteIfExists(file());
		} catch (IOException ignored) {
		}
		message = "Spotify is not connected";
	}

	// ------------------------------------------------------------------------------------------------------ sign-in

	/** Opens the Spotify authorization page in the browser and waits for the callback. */
	static synchronized void connect(Consumer<URI> openBrowser) {
		if (clientId.isEmpty()) {
			message = "Put the Client ID of your Spotify app in the settings first";
			return;
		}
		try {
			if (server != null) {
				server.close();
				server = null;
			}
			byte[] random = new byte[48];
			new SecureRandom().nextBytes(random);
			String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
			String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
			byte[] stateBytes = new byte[12];
			new SecureRandom().nextBytes(stateBytes);
			String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);

			// a local server reachable only from this computer waits for the browser redirect with the authorization code
			ServerSocket created = new ServerSocket(PORT, 8, InetAddress.getByName("127.0.0.1"));
			created.setReuseAddress(true);
			created.setSoTimeout(1000);
			server = created;
			long until = System.currentTimeMillis() + 600_000;
			AtomicBoolean finished = new AtomicBoolean();
			AtomicInteger open = new AtomicInteger();
			Thread waiter = new Thread(() -> {
				try (created) {
					while (!created.isClosed() && !finished.get() && System.currentTimeMillis() < until) {
						Socket socket;
						try {
							socket = created.accept();
						} catch (SocketTimeoutException idle) {
							continue;
						}
						// one thread per connection: browsers pre-open an idle second connection that must not block the real one; only a few at once
						if (open.get() >= MOST_CONNECTIONS) {
							socket.close();
							continue;
						}
						open.incrementAndGet();
						Thread handler = new Thread(() -> {
							try {
								if (answer(socket, state, verifier)) {
									finished.set(true);
								}
							} finally {
								open.decrementAndGet();
							}
						}, "Lumio Spotify sign-in connection");
						handler.setDaemon(true);
						handler.start();
					}
					if (finished.get()) {
						Thread.sleep(500); // let the response page finish sending
					}
				} catch (IOException | InterruptedException ignored) {
					// server closed
				}
			}, "Lumio Spotify sign-in");
			waiter.setDaemon(true);
			waiter.start();
			String url = "https://accounts.spotify.com/authorize?response_type=code&client_id=" + enc(clientId) + "&scope=" + enc(SCOPE)
				+ "&redirect_uri=" + enc(REDIRECT) + "&state=" + enc(state) + "&code_challenge_method=S256&code_challenge=" + enc(challenge);
			message = "Sign in to Spotify in the browser that was opened";
			openBrowser.accept(URI.create(url));
		} catch (Exception e) {
			message = "Spotify sign-in could not start: " + e.getMessage();
		}
	}

	/**
	 * Answers one browser request. The redirect target carries the authorization code in its query; any other request gets a page saying the game
	 * is waiting, so opening the link manually shows something instead of a browser error.
	 *
	 * @return whether the sign-in is complete
	 */
	private static boolean answer(Socket socket, String state, String verifier) {
		try (socket) {
			socket.setSoTimeout(5000);
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FilterInputStream(socket.getInputStream()) {
					// read limit per connection, however long a line is
					private int left = 16384;

					@Override
					public int read() throws IOException {
						int value = left > 0 ? super.read() : -1;
						left--;
						return value;
					}

					@Override
					public int read(byte[] buffer, int offset, int length) throws IOException {
						if (left <= 0) {
							return -1;
						}
						int count = super.read(buffer, offset, Math.min(length, left));
						left -= Math.max(count, 0);
						return count;
					}
				}, StandardCharsets.ISO_8859_1));
			String request = reader.readLine();
			if (request == null) {
				return false;
			}
			// drain the rest of the request: closing a connection with unread data resets it and the page is lost
			String line;
			int headers = 0;
			while (headers++ < 100 && (line = reader.readLine()) != null && !line.isEmpty()) {
				// skip headers
			}
			String[] parts = request.split(" ");
			String raw = parts.length > 1 ? parts[1] : "/";
			int mark = raw.indexOf('?');
			String path = mark < 0 ? raw : raw.substring(0, mark);
			Map<String, String> query = parse(mark < 0 ? null : raw.substring(mark + 1));
			String page;
			int status = 200;
			boolean done = false;
			if (!path.equals("/") && !path.equals("/callback")) {
				status = 404;
				page = "Not found";
			} else if (state.equals(query.get("state")) && query.containsKey("code")) {
				done = exchangeCode(query.get("code"), verifier);
				page = done ? "Spotify is connected. You can close this page and go back to the game."
					: "Spotify did not accept it. Check the Client ID and the redirect link in your Spotify app. " + message;
			} else if (state.equals(query.get("state")) && query.containsKey("error")) {
				// only with this sign-in's state: another page sending the browser here cannot change the message
				message = "Spotify said no: " + query.get("error");
				page = "Not connected: " + query.get("error");
			} else {
				page = "Lumio is waiting for the Spotify sign-in. Press Connect in the game and sign in in the page that opens.";
			}
			byte[] body = ("<!doctype html><meta charset=utf-8><title>Lumio</title><body style=\"font-family:sans-serif;text-align:center;margin-top:20vh\"><h2>"
				+ page.replace("&", "&amp;").replace("<", "&lt;") + "</h2>").getBytes(StandardCharsets.UTF_8);
			OutputStream out = socket.getOutputStream();
			out.write(("HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + body.length
				+ "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
			out.write(body);
			out.flush();
			socket.shutdownOutput();
			return done;
		} catch (IOException e) {
			return false; // an idle connection, or one that went away
		}
	}

	private static boolean exchangeCode(String code, String verifier) {
		try {
			JsonObject answer = postForm("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT)
				+ "&client_id=" + enc(clientId) + "&code_verifier=" + enc(verifier));
			if (answer == null || !answer.has("access_token")) {
				message = "Spotify did not accept the sign-in";
				return false;
			}
			takeTokens(answer);
			message = "Spotify is connected";
			return true;
		} catch (IOException e) {
			message = "Spotify could not be reached";
			return false;
		}
	}

	private static void takeTokens(JsonObject answer) {
		accessToken = answer.get("access_token").getAsString();
		accessUntil = System.currentTimeMillis() + Math.max(60, answer.has("expires_in") ? answer.get("expires_in").getAsLong() : 3600) * 1000L - 30_000L;
		if (answer.has("refresh_token")) {
			refreshToken = answer.get("refresh_token").getAsString();
			saveTokens();
		}
	}

	/** Reports a failure: stored as the status message for the menu and shown as a notification. */
	private static void fail(String key, String title, String text) {
		message = text;
		Toasts.error(key, title, text);
	}

	private static boolean ensureAccess() throws IOException {
		if (!accessToken.isEmpty() && System.currentTimeMillis() < accessUntil) {
			return true;
		}
		if (!connected() || clientId.isEmpty()) {
			fail("spotify.setup", "Spotify is not set up", clientId.isEmpty() ? "Put the Client ID of your Spotify app in the settings of the mod" : "Spotify is not connected: press Connect in the settings of the mod");
			return false;
		}
		JsonObject answer = postForm("grant_type=refresh_token&refresh_token=" + enc(refreshToken) + "&client_id=" + enc(clientId));
		if (answer == null || !answer.has("access_token")) {
			fail("spotify.signin", "Spotify needs you to sign in again", "Press Connect in the settings of the mod.");
			return false;
		}
		takeTokens(answer);
		return true;
	}

	// ----------------------------------------------------------------------------------------------------------- API requests

	/** @return what is playing, or null if nothing plays or Spotify cannot be queried */
	static Playing poll() {
		if (System.currentTimeMillis() < waitUntil) {
			return null;
		}
		try {
			if (!ensureAccess()) {
				return null;
			}
			HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.spotify.com/v1/me/player/currently-playing").toURL().openConnection();
			connection.setConnectTimeout(2500);
			connection.setReadTimeout(4000);
			connection.setRequestProperty("Authorization", "Bearer " + accessToken);
			try {
				int code = connection.getResponseCode();
				if (code == 429) {
					String after = connection.getHeaderField("Retry-After");
					waitUntil = System.currentTimeMillis() + (after == null ? 5 : Math.min(60, Long.parseLong(after.trim()))) * 1000L;
					return null;
				}
				if (code == 401) {
					accessToken = "";
					return null;
				}
				if (code != 200) {
					return null; // 204: nothing is playing
				}
				try (InputStream in = connection.getInputStream()) {
					JsonObject object = JsonParser.parseString(HttpLimits.readText(in)).getAsJsonObject();
					if (!object.has("item") || !object.get("item").isJsonObject()) {
						return null; // an ad, or something that is not a track
					}
					JsonObject item = object.getAsJsonObject("item");
					StringBuilder artists = new StringBuilder();
					if (item.has("artists")) {
						for (var artist : item.getAsJsonArray("artists")) {
							if (!artists.isEmpty()) {
								artists.append(", ");
							}
							artists.append(artist.getAsJsonObject().get("name").getAsString());
						}
					}
					String album = "";
					String cover = "";
					if (item.has("album") && item.get("album").isJsonObject()) {
						JsonObject albumObject = item.getAsJsonObject("album");
						album = albumObject.has("name") ? albumObject.get("name").getAsString() : "";
						if (albumObject.has("images")) {
							JsonArray images = albumObject.getAsJsonArray("images");
							// the smallest image that is at least as large as the cover is asked for (640 px usually); they are ordered largest to smallest
							for (var image : images) {
								JsonObject entry = image.getAsJsonObject();
								if (cover.isEmpty() || entry.has("width") && entry.get("width").getAsInt() >= MusicService.COVER_REQUEST) {
									cover = entry.get("url").getAsString();
								}
							}
						}
					}
					long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
					long progress = object.has("progress_ms") && !object.get("progress_ms").isJsonNull() ? object.get("progress_ms").getAsLong() : 0;
					boolean playing = object.has("is_playing") && object.get("is_playing").getAsBoolean();
					String id = item.has("id") && !item.get("id").isJsonNull() ? item.get("id").getAsString() : "";
					message = "Spotify";
					return new Playing(new MusicService.Track(item.get("name").getAsString(), artists.toString(), album, duration, cover, id), playing, progress);
				}
			} finally {
				connection.disconnect();
			}
		} catch (Exception e) {
			fail("spotify.reach", "Spotify could not be reached", "Check the connection to the internet. Spotify is asked again in a moment.");
			return null;
		}
	}

	/**
	 * Skips, goes back, or toggles playback in Spotify. Spotify only allows this for Premium accounts, and only if the sign-in granted the
	 * playback permission, which the first version of this integration did not request.
	 *
	 * @param word    "next", "previous" or "playpause"
	 * @param playing for "playpause": whether to play rather than pause
	 */
	static void command(String word, boolean playing) {
		try {
			if (!ensureAccess()) {
				return;
			}
			String path = switch (word) {
				case "next" -> "next";
				case "previous" -> "previous";
				default -> playing ? "play" : "pause";
			};
			HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.spotify.com/v1/me/player/" + path).toURL().openConnection();
			connection.setConnectTimeout(2500);
			connection.setReadTimeout(4000);
			connection.setRequestMethod(path.equals("play") || path.equals("pause") ? "PUT" : "POST");
			connection.setRequestProperty("Authorization", "Bearer " + accessToken);
			connection.setRequestProperty("Content-Length", "0");
			connection.setDoOutput(true);
			connection.getOutputStream().close();
			try {
				int code = connection.getResponseCode();
				if (code == 403) {
					fail("spotify.control.403", "Spotify did not allow it", "The controls need Premium, and a new sign-in: press Disconnect, then Connect, in the settings of the mod.");
				} else if (code == 404) {
					fail("spotify.control.404", "Spotify is not open", "There is no player that is on. Start Spotify on this computer or on a phone, play something, and try again.");
				} else if (code == 429) {
					fail("spotify.control.429", "Spotify wants a moment", "Too many things were asked at once. Try again in a few seconds.");
				} else if (code >= 400) {
					fail("spotify.control." + code, "Spotify did not take it", "Spotify answered with the error " + code + ".");
				}
			} finally {
				connection.disconnect();
			}
		} catch (IOException e) {
			fail("spotify.reach", "Spotify could not be reached", "Check the connection to the internet, and try again.");
		}
	}

	/** Seeks to a position in milliseconds in the song playing in Spotify. */
	static void seek(long milliseconds) {
		try {
			if (!ensureAccess()) {
				return;
			}
			HttpURLConnection connection = (HttpURLConnection) URI.create("https://api.spotify.com/v1/me/player/seek?position_ms=" + Math.max(0L, milliseconds)).toURL().openConnection();
			connection.setConnectTimeout(2500);
			connection.setReadTimeout(4000);
			connection.setRequestMethod("PUT");
			connection.setRequestProperty("Authorization", "Bearer " + accessToken);
			connection.setRequestProperty("Content-Length", "0");
			connection.setDoOutput(true);
			connection.getOutputStream().close();
			try {
				int code = connection.getResponseCode();
				if (code == 403) {
					fail("spotify.control.403", "Spotify did not allow it", "The controls need Premium, and a new sign-in: press Disconnect, then Connect, in the settings of the mod.");
				} else if (code >= 400 && code != 404) {
					fail("spotify.control." + code, "Spotify did not take it", "Spotify answered with the error " + code + ".");
				}
			} finally {
				connection.disconnect();
			}
		} catch (IOException e) {
			fail("spotify.reach", "Spotify could not be reached", "Check the connection to the internet, and try again.");
		}
	}

	private static JsonObject postForm(String body) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create("https://accounts.spotify.com/api/token").toURL().openConnection();
		connection.setConnectTimeout(4000);
		connection.setReadTimeout(6000);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		try {
			try (OutputStream out = connection.getOutputStream()) {
				out.write(body.getBytes(StandardCharsets.UTF_8));
			}
			if (connection.getResponseCode() != 200) {
				return null;
			}
			try (InputStream in = connection.getInputStream()) {
				return JsonParser.parseString(HttpLimits.readText(in)).getAsJsonObject();
			}
		} finally {
			connection.disconnect();
		}
	}

	private static String enc(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

	private static Map<String, String> parse(String query) {
		Map<String, String> result = new HashMap<>();
		if (query != null) {
			for (String part : query.split("&")) {
				int equals = part.indexOf('=');
				if (equals > 0) {
					result.put(part.substring(0, equals), URLDecoder.decode(part.substring(equals + 1), StandardCharsets.UTF_8));
				}
			}
		}
		return result;
	}
}
