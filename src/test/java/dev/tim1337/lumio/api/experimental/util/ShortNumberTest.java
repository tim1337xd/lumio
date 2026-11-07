package dev.tim1337.lumio.api.experimental.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Big numbers written short: three significant digits, K, M, B, no trailing zeros, and rounding that moves to the next unit. */
class ShortNumberTest {
	@Test
	void theExamples() {
		assertEquals("100M", ShortNumber.of("100,081,263"));
		assertEquals("12.3M", ShortNumber.of("12,345,678"));
		assertEquals("1.25B", ShortNumber.of("1,250,000,000"));
		assertEquals("950K", ShortNumber.of("950,000"));
	}

	@Test
	void underAThousandAsItIs() {
		assertEquals("999", ShortNumber.of("999"));
		assertEquals("0", ShortNumber.of("0"));
		assertEquals("12.5", ShortNumber.of("12.5"));
	}

	@Test
	void theEdges() {
		assertEquals("1K", ShortNumber.of("1,000"));
		assertEquals("1M", ShortNumber.of("999,999"));
		assertEquals("1M", ShortNumber.of("1,000,000"));
		assertEquals("999K", ShortNumber.of("999,499"));
		assertEquals("1M", ShortNumber.of("999,500"));
		assertEquals("1B", ShortNumber.of("999,500,000"));
		assertEquals("100K", ShortNumber.of("99,950"));
		assertEquals("99.9K", ShortNumber.of("99,949"));
		assertEquals("2K", ShortNumber.of("1,995"));
		assertEquals("1.99K", ShortNumber.of("1,994"));
	}

	@Test
	void noTrailingZeros() {
		assertEquals("1.5K", ShortNumber.of("1,500"));
		assertEquals("10K", ShortNumber.of("10,000"));
		assertEquals("100M", ShortNumber.of("100,000,000"));
		assertEquals("2B", ShortNumber.of("2,000,000,000"));
	}

	@Test
	void withoutCommasAndWithDecimals() {
		assertEquals("12.3K", ShortNumber.of("12345"));
		assertEquals("1.23K", ShortNumber.of("1,234.5"));
	}

	@Test
	void veryBigAndNegative() {
		assertEquals("1T", ShortNumber.of("1,000,000,000,000"));
		assertEquals("1000T", ShortNumber.of("1,000,000,000,000,000"));
		assertEquals("-12.3M", ShortNumber.of("-12,345,678"));
	}

	@Test
	void whatIsNoNumberStaysAsItIs() {
		assertEquals("The End", ShortNumber.of("The End"));
		assertEquals("1.2B", ShortNumber.of("1.2B"));
		assertEquals("", ShortNumber.of(""));
	}
}
