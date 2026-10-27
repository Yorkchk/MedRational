package com.example.MedRational;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// No LibreOffice: this test doesn't need it, and on Windows the test JVM can exit without stopping
// soffice, which then locks its profile folder for the next local run of the app
@SpringBootTest(properties = "preview.office-enabled=false")
class MedRationalApplicationTests {

	@Test
	void contextLoads() {
	}

}
