
package com.medirag.backend.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.medirag.backend.dto.GridCell;

@Service
public class CellImagePreprocessingService {

    @Value("${medirag.ocr.cell.border-inset:5}")
    private int borderInset;

    @Value("${medirag.ocr.cell.padding:15}")
    private int padding;

    @Value("${medirag.ocr.cell.upscale:2}")
    private int upscale;

    @Value("${medirag.ocr.cell.blank-threshold:0.001}")
    private double blankThreshold;

    public BufferedImage prepareCell(
            BufferedImage source,
            GridCell cell) {

        int insetX = Math.min(
                Math.max(0, borderInset),
                cell.width() / 4
        );

        int insetY = Math.min(
                Math.max(0, borderInset),
                cell.height() / 4
        );

        int x = cell.left() + insetX;

        int y = cell.top() + insetY;

        int width = cell.width() - 2 * insetX;

        int height = cell.height() - 2 * insetY;

        if (width <= 0 || height <= 0
                || x < 0 || y < 0
                || x + width > source.getWidth()
                || y + height > source.getHeight()) {

            throw new IllegalArgumentException(
                    "Invalid cell crop dimensions"
            );
        }

        BufferedImage cropped =
                source.getSubimage(
                        x, y, width, height
                );

        int scale = Math.max(
                1, Math.min(upscale, 3)
        );

        int safePadding = Math.max(
                0, Math.min(padding, 100)
        );

        int outputWidth =
                width * scale + safePadding * 2;

        int outputHeight =
                height * scale + safePadding * 2;

        BufferedImage result = new BufferedImage(

                outputWidth,

                outputHeight,

                BufferedImage.TYPE_BYTE_GRAY
        );

        Graphics2D graphics =
                result.createGraphics();

        try {

            graphics.setColor(Color.WHITE);

            graphics.fillRect(
                    0, 0, outputWidth, outputHeight
            );

            graphics.setRenderingHint(

                    RenderingHints.KEY_INTERPOLATION,

                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );

            graphics.drawImage(

                    cropped,

                    safePadding,

                    safePadding,

                    width * scale,

                    height * scale,

                    null
            );

        } finally {

            graphics.dispose();
        }

        return result;
    }

    public boolean isVisuallyBlank(
            BufferedImage image) {

        long darkPixels = 0;

        long totalPixels =
                (long) image.getWidth()
                * image.getHeight();

        for (int y = 0; y < image.getHeight(); y++) {

            for (int x = 0;
                    x < image.getWidth(); x++) {

                int gray = image.getRGB(x, y) & 0xff;

                if (gray < 180) {

                    darkPixels++;
                }
            }
        }

        // Conservative heuristic, not proof of emptiness.
        return darkPixels == 0
                || ((double) darkPixels / totalPixels
                        < blankThreshold
                    && darkPixels < 5);
    }
}
