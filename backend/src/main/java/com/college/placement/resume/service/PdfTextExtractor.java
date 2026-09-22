package com.college.placement.resume.service;

import com.college.placement.common.exception.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Pure text extraction from in-memory PDF bytes. Only normal text PDFs are
 * supported in this phase; there is no OCR, no page rendering and no external
 * or cloud service. Embedded JavaScript, attachments and macros are never
 * executed — the document is only parsed for text.
 */
@Service
public class PdfTextExtractor {

    private static final int MAX_PAGES_TO_SCAN = 50;

    public record ExtractionResult(String text, int pageCount) {
    }

    public ExtractionResult extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new BadRequestException("This PDF is password protected. Upload an unlocked resume.");
            }
            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pageCount, MAX_PAGES_TO_SCAN));
            String text = stripper.getText(document);
            return new ExtractionResult(text == null ? "" : text, pageCount);
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("This PDF is password protected. Upload an unlocked resume.");
        } catch (IOException e) {
            throw new BadRequestException("Could not read this PDF. It may be corrupt or not a valid PDF file.");
        }
    }
}