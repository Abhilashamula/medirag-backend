
package com.medirag.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.medirag.backend.dto.DocumentChunk;
import com.medirag.backend.dto.PageText;

@Service
public class SectionChunkingService {

    private static final int TARGET_SIZE = 1800;
    private static final int MAX_SIZE = 2400;

    // Numbered headings: 1 Introduction, 2.3 Diagnosis
    private static final Pattern NUMBERED_HEADING =
            Pattern.compile(
                "^(\\d+(?:\\.\\d+)*\\.?)[ \\t]+(.+)$"
            );

    // Known unnumbered headings
    private static final Pattern KNOWN_HEADING =
            Pattern.compile(
                "^(abstract|introduction|background|"
                + "clinical presentation|symptoms|"
                + "diagnosis|investigations|"
                + "laboratory tests|treatment|"
                + "management|follow.up|"
                + "discussion|conclusion|"
                + "recommendations|references)$",
                Pattern.CASE_INSENSITIVE
            );

    private record TextLine(
            String text,
            int page
    ) {}

    private record Heading(
            String title,
            int level
    ) {}

    private record TextPart(
            String text,
            int page
    ) {}

    private static class Section {

        String title;
        String path;
        int level;

        List<TextPart> parts = new ArrayList<>();

        Section(String title, String path, int level) {
            this.title = title;
            this.path = path;
            this.level = level;
        }
    }

    public List<DocumentChunk> chunkDocument(
            Long documentId,
            List<PageText> pages) {

        List<TextLine> lines = new ArrayList<>();

        for (PageText page : pages) {

            String[] rawLines =
                    page.text().split("\\R");

            for (String raw : rawLines) {

                String line = raw.trim();

                if (!line.isEmpty()) {
                    lines.add(new TextLine(
                            line,
                            page.pageNumber()
                    ));
                }
            }
        }

        List<Section> sections =
                detectSections(lines);

        List<DocumentChunk> chunks =
                new ArrayList<>();

        for (Section section : sections) {
            splitSection(
                    documentId,
                    section,
                    chunks
            );
        }

        return chunks;
    }

    private List<Section> detectSections(
            List<TextLine> lines) {

        List<Section> sections =
                new ArrayList<>();

        List<String> hierarchy =
                new ArrayList<>();

        Section current = new Section(
                "Document Overview",
                "Document Overview",
                0
        );

        sections.add(current);

        for (TextLine line : lines) {

            Heading heading =
                    detectHeading(line.text());

            if (heading != null) {

                int level = heading.level();

                while (hierarchy.size() >= level) {
                    hierarchy.remove(
                            hierarchy.size() - 1
                    );
                }

                while (hierarchy.size() < level - 1) {
                    hierarchy.add("Other");
                }

                hierarchy.add(heading.title());

                String path =
                        String.join(" > ", hierarchy);

                current = new Section(
                        heading.title(),
                        path,
                        level
                );

                sections.add(current);

            } else {
                current.parts.add(
                        new TextPart(
                                line.text(),
                                line.page()
                        )
                );
            }
        }

        return sections;
    }

    private Heading detectHeading(String line) {

        if (line.length() > 120) {
            return null;
        }

        Matcher matcher =
                NUMBERED_HEADING.matcher(line);

        if (matcher.matches()) {

            String number = matcher.group(1)
                    .replaceAll("\\.$", "");

            String title = matcher.group(2).trim();

            if (title.length() > 100) {
                return null;
            }

            int level =
                    number.split("\\.").length;

            if (level <= 4) {
                return new Heading(
                        line,
                        level
                );
            }
        }

        if (KNOWN_HEADING.matcher(line).matches()) {
            return new Heading(line, 1);
        }

        // Uppercase headings
        if (line.length() >= 4
                && line.length() <= 70
                && line.equals(
                    line.toUpperCase(Locale.ROOT)
                )
                && line.matches(".*[A-Z].*")
                && !line.matches(".*[.!?:]$")) {

            return new Heading(line, 1);
        }

        return null;
    }

    private void splitSection(
            Long documentId,
            Section section,
            List<DocumentChunk> result) {

        if (section.parts.isEmpty()) {
            return;
        }

        List<TextPart> units = new ArrayList<>();

        for (TextPart part : section.parts) {
            units.addAll(
                    splitLongText(
                            part.text(),
                            part.page()
                    )
            );
        }

        StringBuilder buffer =
                new StringBuilder();

        int startPage = -1;
        int endPage = -1;

        for (TextPart unit : units) {

            String text = unit.text();

            if (text.isBlank()) {
                continue;
            }

            if (buffer.length() > 0
                    && buffer.length()
                    + text.length() + 1 > TARGET_SIZE) {

                addChunk(
                        documentId,
                        section,
                        buffer.toString(),
                        startPage,
                        endPage,
                        result
                );

                buffer.setLength(0);
                startPage = -1;
            }

            if (startPage == -1) {
                startPage = unit.page();
            }

            endPage = unit.page();

            if (buffer.length() > 0) {
                buffer.append(" ");
            }

            buffer.append(text);
        }

        if (!buffer.isEmpty()) {
            addChunk(
                    documentId,
                    section,
                    buffer.toString(),
                    startPage,
                    endPage,
                    result
            );
        }
    }

    private List<TextPart> splitLongText(
            String text,
            int page) {

        List<TextPart> result =
                new ArrayList<>();

        String normalized =
                text.replaceAll("\\s+", " ").trim();

        if (normalized.length() <= MAX_SIZE) {
            result.add(new TextPart(
                    normalized,
                    page
            ));
            return result;
        }

        // Prefer sentence boundaries
        String[] sentences =
                normalized.split(
                    "(?<=[.!?])\\s+"
                );

        StringBuilder buffer =
                new StringBuilder();

        for (String sentence : sentences) {

            if (sentence.length() > MAX_SIZE) {

                if (!buffer.isEmpty()) {
                    result.add(new TextPart(
                            buffer.toString(),
                            page
                    ));
                    buffer.setLength(0);
                }

                // Fallback: split long sentences
                int start = 0;

                while (start < sentence.length()) {

                    int end = Math.min(
                            start + MAX_SIZE,
                            sentence.length()
                    );

                    if (end < sentence.length()) {
                        int space =
                                sentence.lastIndexOf(
                                        ' ', end
                                );

                        if (space > start) {
                            end = space;
                        }
                    }

                    result.add(new TextPart(
                            sentence.substring(
                                    start, end
                            ).trim(),
                            page
                    ));

                    start = end;

                    while (start < sentence.length()
                            && sentence.charAt(start) == ' ') {
                        start++;
                    }
                }

                continue;
            }

            if (buffer.length() > 0
                    && buffer.length()
                    + sentence.length() + 1 > TARGET_SIZE) {

                result.add(new TextPart(
                        buffer.toString(),
                        page
                ));

                buffer.setLength(0);
            }

            if (buffer.length() > 0) {
                buffer.append(" ");
            }

            buffer.append(sentence);
        }

        if (!buffer.isEmpty()) {
            result.add(new TextPart(
                    buffer.toString(),
                    page
            ));
        }

        return result;
    }

    private void addChunk(
            Long documentId,
            Section section,
            String content,
            int startPage,
            int endPage,
            List<DocumentChunk> result) {

        if (content.isBlank()) {
            return;
        }

        result.add(new DocumentChunk(
                documentId,
                result.size(),
                section.title,
                section.path,
                section.level,
                startPage,
                endPage,
                content
        ));
    }
}
