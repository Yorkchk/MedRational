package com.example.MedRational.Preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PreviewKindTest {

	@Test
	void detectsByExtensionCaseInsensitively() {
		assertEquals(PreviewKind.PDF, PreviewKind.detect("Notes.PDF", null));
		assertEquals(PreviewKind.DOCX, PreviewKind.detect("cours.docx", "application/octet-stream"));
		assertEquals(PreviewKind.PPTX, PreviewKind.detect("slides.pptx", null));
		assertEquals(PreviewKind.XLSX, PreviewKind.detect("table.xlsx", null));
	}

	@Test
	void fallsBackToContentType() {
		assertEquals(PreviewKind.PDF, PreviewKind.detect("noext", "application/pdf"));
		assertEquals(PreviewKind.DOCX, PreviewKind.detect(null,
				"application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
	}

	@Test
	void unsupportedTypesAreNotPreviewable() {
		assertEquals(PreviewKind.NONE, PreviewKind.detect("old.ppt", "application/vnd.ms-powerpoint"));
		assertEquals(PreviewKind.NONE, PreviewKind.detect("image.png", "image/png"));
		assertFalse(PreviewKind.detect(null, null).isPreviewable());
	}

	@Test
	void onlyOfficeFormatsNeedConversion() {
		assertFalse(PreviewKind.PDF.needsConversion());
		assertTrue(PreviewKind.DOCX.needsConversion());
		assertTrue(PreviewKind.PPTX.needsConversion());
		assertTrue(PreviewKind.XLSX.needsConversion());
	}
}
