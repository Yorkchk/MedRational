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
		assertEquals(PreviewKind.NONE, PreviewKind.detect("archive.zip", "application/zip"));
		assertFalse(PreviewKind.detect(null, null).isPreviewable());
	}

	@Test
	void detectsCommonImageFormats() {
		for (String name : new String[]{"a.jpg", "a.JPEG", "a.png", "a.webp", "a.gif", "a.bmp", "a.heic", "a.heif", "a.avif"}) {
			assertEquals(PreviewKind.IMAGE, PreviewKind.detect(name, "application/octet-stream"), name);
		}
		assertEquals(PreviewKind.IMAGE, PreviewKind.detect("noext", "image/tiff"));
		assertTrue(PreviewKind.IMAGE.isPreviewable());
	}

	@Test
	void imageContentTypePrefersStoredTypeAndFallsBackToExtension() {
		assertEquals("image/png", PreviewKind.imageContentType("photo.jpg", "image/png"));
		assertEquals("image/jpeg", PreviewKind.imageContentType("Photo.JPG", "application/octet-stream"));
		assertEquals("image/heic", PreviewKind.imageContentType("scan.heic", null));
		assertEquals("application/octet-stream", PreviewKind.imageContentType("noext", null));
	}

	@Test
	void onlyOfficeFormatsNeedConversion() {
		assertFalse(PreviewKind.IMAGE.needsConversion());
		assertFalse(PreviewKind.PDF.needsConversion());
		assertTrue(PreviewKind.DOCX.needsConversion());
		assertTrue(PreviewKind.PPTX.needsConversion());
		assertTrue(PreviewKind.XLSX.needsConversion());
	}
}
