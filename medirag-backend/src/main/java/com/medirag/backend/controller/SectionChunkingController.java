
package com.medirag.backend.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.medirag.backend.dto.ChunkingResponse;
import com.medirag.backend.dto.DocumentChunk;
import com.medirag.backend.dto.PageText;
import com.medirag.backend.service.PageAwarePdfService;
import com.medirag.backend.service.SectionChunkingService;

@RestController
@RequestMapping("/api/documents")
public class SectionChunkingController {

    private final PageAwarePdfService pdfService;
    private final SectionChunkingService chunkingService;

    public SectionChunkingController(
            PageAwarePdfService pdfService,
            SectionChunkingService chunkingService) {

        this.pdfService = pdfService;
        this.chunkingService = chunkingService;
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<ChunkingResponse> getChunks(
            @PathVariable Long id) throws IOException {

        List<PageText> pages =
                pdfService.extractPages(id);

        List<DocumentChunk> chunks =
                chunkingService.chunkDocument(
                        id,
                        pages
                );

        ChunkingResponse response =
                new ChunkingResponse(
                        id,
                        pages.size(),
                        chunks.size(),
                        chunks
                );

        return ResponseEntity.ok(response);
    }
}
