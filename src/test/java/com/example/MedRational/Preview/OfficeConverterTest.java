package com.example.MedRational.Preview;

import com.example.MedRational.Exceptions.PreviewUnavailableException;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficeConverterTest {

	// Disabled, LibreOffice is never started, even where it's installed: Office conversions answer
	// 503 like when it's missing, and shutting down has nothing to stop
	@Test
	void disabledConverterNeverStartsLibreOffice() {
		OfficeConverter converter = new OfficeConverter(false, "", 2002, 60000);

		converter.afterPropertiesSet();

		assertThrows(PreviewUnavailableException.class,
				() -> converter.convertToPdf(new byte[]{1, 2, 3}, DefaultDocumentFormatRegistry.DOCX));
		assertDoesNotThrow(converter::destroy);
	}
}
