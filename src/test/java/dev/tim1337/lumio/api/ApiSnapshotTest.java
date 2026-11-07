package dev.tim1337.lumio.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the stable add-on API stable: its public classes, methods and fields are compared with api-snapshot.txt. Only the stable part counts:
 * the experimental packages ({@code api.experimental}) and the internal bridge ({@code api.spi}) change without a new version, and nothing of
 * the stable part may use them in its signatures. The test fails when the API changed
 * and the file was not updated, when something was changed or removed without a new major version, and when something was added without at
 * least a new minor version. {@code ./gradlew test -PupdateApiSnapshot} writes the file anew, after the version was raised to fit the change.
 */
class ApiSnapshotTest {
	private static final Path SNAPSHOT = Path.of("api/api-snapshot.txt");
	private static final Path SOURCES = Path.of("api/src/main/java/dev/tim1337/lumio/api");
	private static final String PACKAGE = "dev.tim1337.lumio.api.";
	/** The packages outside the stable part, which the snapshot leaves out: experimental, and the internal bridge. */
	private static final String EXPERIMENTAL = PACKAGE + "experimental.";
	private static final String INTERNAL = PACKAGE + "spi.";
	private static final String VERSION_LINE = "version ";
	private static final String HEADER = "# The public add-on API of Lumio, written by ApiSnapshotTest; see API.md. Update it with ./gradlew test "
		+ "-PupdateApiSnapshot, never by hand.";
	private static final boolean UPDATE = Boolean.getBoolean("lumio.updateApiSnapshot");

	@Test
	void theApiMatchesItsSnapshot() throws IOException {
		Set<String> current = currentApi();
		ApiVersion version = ApiVersion.parse(LumioApi.VERSION);
		if (!Files.exists(SNAPSHOT)) {
			if (UPDATE) {
				write(version, current);
				return;
			}
			fail("api/api-snapshot.txt is missing: write it with ./gradlew test -PupdateApiSnapshot");
		}
		List<String> lines = Files.readAllLines(SNAPSHOT, StandardCharsets.UTF_8);
		ApiVersion saved = savedVersion(lines);
		Set<String> before = new TreeSet<>();
		for (String line : lines) {
			if (!line.isBlank() && !line.startsWith("#") && !line.startsWith(VERSION_LINE)) {
				before.add(line);
			}
		}
		Set<String> removed = new TreeSet<>(before);
		removed.removeAll(current);
		Set<String> added = new TreeSet<>(current);
		added.removeAll(before);
		String problem = bumpProblem(saved, version, removed, added);
		if (problem != null) {
			fail(problem + "\n" + diff(removed, added));
		}
		if (removed.isEmpty() && added.isEmpty() && saved.equals(version)) {
			return;
		}
		if (UPDATE) {
			write(version, current);
			return;
		}
		fail("The add-on API changed (or its version did) but api/api-snapshot.txt was not updated. Run ./gradlew test -PupdateApiSnapshot.\n"
			+ diff(removed, added));
	}

	@Test
	void theStablePartUsesNothingExperimental() {
		List<String> wrong = currentApi().stream().filter(line -> line.contains(EXPERIMENTAL) || line.contains(INTERNAL)).toList();
		assertTrue(wrong.isEmpty(), "The stable API must not use experimental or internal types in its signatures:\n" + String.join("\n", wrong));
	}

	@Test
	void theVersionIsTheRunningOne() {
		assertEquals(ApiVersion.parse(LumioApi.VERSION), LumioApi.version());
	}

	@Test
	void theBumpRules() {
		ApiVersion one = new ApiVersion(1, 0, 0);
		Set<String> none = Set.of();
		Set<String> some = Set.of("x");
		assertNull(bumpProblem(one, one, none, none));
		assertNull(bumpProblem(one, new ApiVersion(1, 0, 1), none, none), "a patch changes no signature");
		assertNotNull(bumpProblem(one, one, none, some), "adding needs a new minor version");
		assertNotNull(bumpProblem(one, new ApiVersion(1, 0, 1), none, some));
		assertNull(bumpProblem(one, new ApiVersion(1, 1, 0), none, some));
		assertNotNull(bumpProblem(one, new ApiVersion(1, 1, 0), some, none), "removing needs a new major version");
		assertNotNull(bumpProblem(one, new ApiVersion(1, 1, 0), some, some), "a change is a removal and an addition");
		assertNull(bumpProblem(one, new ApiVersion(2, 0, 0), some, some));
		assertNotNull(bumpProblem(new ApiVersion(1, 2, 0), new ApiVersion(1, 1, 0), none, none), "a version never goes down");
	}

	/** @return what is wrong with the version for the change, or null when it fits */
	static String bumpProblem(ApiVersion saved, ApiVersion version, Set<String> removed, Set<String> added) {
		if (version.compareTo(saved) < 0) {
			return "The API version went down from " + saved + " to " + version + ".";
		}
		if (!removed.isEmpty() && version.major() <= saved.major()) {
			return "Something of the API was changed or removed: that needs a new major version (" + (saved.major() + 1) + ".0.0), "
				+ "and should be avoided: deprecate first, remove in the next major version.";
		}
		if (!added.isEmpty() && version.major() == saved.major() && version.minor() <= saved.minor()) {
			return "Something was added to the API: that needs at least a new minor version (" + saved.major() + "." + (saved.minor() + 1) + ".0).";
		}
		return null;
	}

	private static ApiVersion savedVersion(List<String> lines) {
		for (String line : lines) {
			if (line.startsWith(VERSION_LINE)) {
				return ApiVersion.parse(line.substring(VERSION_LINE.length()));
			}
		}
		throw new IllegalStateException("api/api-snapshot.txt has no version line");
	}

	private static void write(ApiVersion version, Set<String> api) throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add(HEADER);
		lines.add(VERSION_LINE + version);
		lines.addAll(api);
		Files.writeString(SNAPSHOT, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	private static String diff(Set<String> removed, Set<String> added) {
		StringBuilder text = new StringBuilder();
		removed.forEach(line -> text.append("- ").append(line).append('\n'));
		added.forEach(line -> text.append("+ ").append(line).append('\n'));
		return text.toString();
	}

	/** @return one line for every public class, constructor, method and field of the API package, sorted */
	private static Set<String> currentApi() {
		Set<String> api = new TreeSet<>();
		for (Class<?> type : apiClasses()) {
			describe(type, api);
		}
		assertTrue(api.size() > 0, "the API package has public classes");
		return api;
	}

	private static List<Class<?>> apiClasses() {
		try (Stream<Path> files = Files.walk(SOURCES)) {
			List<Class<?>> classes = new ArrayList<>();
			// every stable package of the API, also its sub-packages (features, settings, drawing); not the experimental part or the bridge
			for (Path file : files.filter(path -> path.toString().endsWith(".java") && !path.endsWith("package-info.java")).sorted().toList()) {
				String name = SOURCES.relativize(file).toString().replace(java.io.File.separatorChar, '.');
				if ((PACKAGE + name).startsWith(EXPERIMENTAL) || (PACKAGE + name).startsWith(INTERNAL)) {
					continue;
				}
				classes.add(Class.forName(PACKAGE + name.substring(0, name.length() - ".java".length()), false, ApiSnapshotTest.class.getClassLoader()));
			}
			return classes;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void describe(Class<?> type, Set<String> api) {
		if (!Modifier.isPublic(type.getModifiers())) {
			return;
		}
		String owner = type.getName().substring(PACKAGE.length()) + ": ";
		api.add(owner + kind(type) + typeParameters(type.getTypeParameters()) + supertypes(type));
		boolean extendable = !type.isInterface() && !Modifier.isFinal(type.getModifiers());
		for (Constructor<?> constructor : type.getDeclaredConstructors()) {
			if (visible(constructor.getModifiers(), extendable) && !constructor.isSynthetic()) {
				api.add(owner + "constructor(" + names(constructor.getGenericParameterTypes()) + ")");
			}
		}
		for (Method method : type.getDeclaredMethods()) {
			if (visible(method.getModifiers(), extendable) && !method.isSynthetic() && !method.isBridge()) {
				api.add(owner + "method " + methodModifiers(method) + typeParameters(method.getTypeParameters()) + method.getGenericReturnType().getTypeName() + " " + method.getName()
					+ "(" + names(method.getGenericParameterTypes()) + ")");
			}
		}
		for (Field field : type.getDeclaredFields()) {
			if (visible(field.getModifiers(), extendable) && !field.isSynthetic()) {
				api.add(owner + "field " + fieldModifiers(field) + field.getGenericType().getTypeName() + " " + field.getName()
					+ constantValue(field));
			}
		}
		for (Class<?> nested : type.getDeclaredClasses()) {
			describe(nested, api);
		}
	}

	private static boolean visible(int modifiers, boolean extendable) {
		return Modifier.isPublic(modifiers) || extendable && Modifier.isProtected(modifiers);
	}

	private static String kind(Class<?> type) {
		String kind = type.isAnnotation() ? "annotation" : type.isInterface() ? "interface" : type.isRecord() ? "record" : type.isEnum() ? "enum" : "class";
		int modifiers = type.getModifiers();
		String shape = Modifier.isFinal(modifiers) && !type.isRecord() && !type.isEnum() ? "final "
			: Modifier.isAbstract(modifiers) && !type.isInterface() ? "abstract " : "";
		return shape + kind;
	}

	private static String supertypes(Class<?> type) {
		List<String> supers = new ArrayList<>();
		Type superclass = type.getGenericSuperclass();
		if (superclass != null && superclass != Object.class && superclass != Record.class && !type.isEnum()) {
			supers.add(superclass.getTypeName());
		}
		for (Type face : type.getGenericInterfaces()) {
			supers.add(face.getTypeName());
		}
		return supers.isEmpty() ? "" : " : " + String.join(", ", supers);
	}

	private static String methodModifiers(Method method) {
		int modifiers = method.getModifiers();
		StringBuilder text = new StringBuilder();
		if (Modifier.isStatic(modifiers)) {
			text.append("static ");
		}
		if (method.isDefault()) {
			text.append("default ");
		} else if (Modifier.isAbstract(modifiers)) {
			text.append("abstract ");
		}
		if (Modifier.isProtected(modifiers)) {
			text.append("protected ");
		}
		return text.toString();
	}

	private static String fieldModifiers(Field field) {
		int modifiers = field.getModifiers();
		return (Modifier.isStatic(modifiers) ? "static " : "") + (Modifier.isFinal(modifiers) ? "final " : "");
	}

	/** @return " = value" for a constant of a simple type, so a changed constant (such as an id) counts as a change; the version is left out */
	private static String constantValue(Field field) {
		int modifiers = field.getModifiers();
		if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers) || field.getDeclaringClass() == LumioApi.class
			&& field.getName().equals("VERSION")) {
			return "";
		}
		if (field.getType() != String.class && !field.getType().isPrimitive()) {
			return "";
		}
		try {
			Object value = field.get(null);
			return value instanceof String text ? " = \"" + text + "\"" : " = " + value;
		} catch (IllegalAccessException e) {
			return "";
		}
	}

	/** @return "<F extends Bound> " for generic classes and methods, so a changed bound counts as a change */
	private static String typeParameters(TypeVariable<?>[] variables) {
		if (variables.length == 0) {
			return "";
		}
		return Arrays.stream(variables).map(variable -> variable.getName() + " extends " + names(variable.getBounds()))
			.collect(Collectors.joining(", ", "<", "> "));
	}

	private static String names(Type[] types) {
		return Arrays.stream(types).map(Type::getTypeName).collect(Collectors.joining(", "));
	}
}
