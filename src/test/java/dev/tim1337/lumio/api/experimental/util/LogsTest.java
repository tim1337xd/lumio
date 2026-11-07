package dev.tim1337.lumio.api.experimental.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Logged exceptions never name the user's home folder: it becomes "~", in the message and in every cause. */
class LogsTest {
	private static final String HOME = "C:\\Users\\Someone";

	@Test
	void theHomeFolderBecomesATilde() {
		assertEquals("~\\AppData\\x.json", Logs.withoutHome("C:\\Users\\Someone\\AppData\\x.json", HOME));
		assertEquals("~/AppData/x.json", Logs.withoutHome("C:/Users/Someone/AppData/x.json", HOME));
		assertEquals("from ~\\a to ~\\b", Logs.withoutHome("from c:\\users\\someone\\a to C:\\USERS\\SOMEONE\\b", HOME));
		assertEquals("D:\\Games\\x.json", Logs.withoutHome("D:\\Games\\x.json", HOME));
		assertNull(Logs.withoutHome(null, HOME));
	}

	@Test
	void exceptionsAndTheirCausesAreCleaned() {
		IOException cause = new AccessDeniedException(HOME + "\\config\\lumio\\config.json");
		RuntimeException error = new RuntimeException("Could not save " + HOME + "\\x", cause);
		Throwable logged = Logs.withoutHome(error, HOME, 8);
		assertEquals("java.lang.RuntimeException: Could not save ~\\x", logged.toString());
		assertEquals("java.nio.file.AccessDeniedException: ~\\config\\lumio\\config.json", logged.getCause().toString());
		assertArrayEquals(error.getStackTrace(), logged.getStackTrace());
	}

	@Test
	void anExceptionWithoutAMessageKeepsItsName() {
		assertEquals("java.lang.IllegalStateException", Logs.withoutHome(new IllegalStateException(), HOME, 8).toString());
	}
}
