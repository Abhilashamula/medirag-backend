
package com.medirag.backend.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.PageText;
import com.medirag.backend.entity.Document;

@Service
public class PageAwarePdfService {

    private final DocumentService documentService;

    public PageAwarePdfService(
            DocumentService documentService) {
        this.documentService = documentService;
    }

    public List<PageText> extractPages(Long documentId)
            throws IOException {

        Document document =
                documentService.getDocument(documentId);

        byte[] pdfBytes = document.getFileData();

        List<PageText> pages = new ArrayList<>();

        try (PDDocument pdf = Loader.loadPDF(pdfBytes)) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            stripper.setSortByPosition(true);

            int totalPages = pdf.getNumberOfPages();

            for (int page = 1; page <= totalPages; page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String text = stripper.getText(pdf);

                pages.add(new PageText(
                        page,
                        text == null ? "" : text
                ));
            }
        }

        return pages;
    }
}
