
package com.medirag.backend.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;

import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import org.springframework.stereotype.Service;

import com.medirag.backend.config.OpenCvConfig.OpenCvRuntime;

@Service
public class ImagePreprocessingService {

    public ImagePreprocessingService(
            OpenCvRuntime openCvRuntime) {
        // Constructor dependency ensures OpenCV is loaded.
    }

    public Mat toMat(BufferedImage image)
            throws IOException {

        if (image == null) {

            throw new IOException(
                    "Input image is empty"
            );
        }

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        if (!ImageIO.write(image, "png", output)) {

            throw new IOException(
                    "Unable to encode input image"
            );
        }

        MatOfByte encoded =
                new MatOfByte(output.toByteArray());

        try {

            Mat result = Imgcodecs.imdecode(
                    encoded,
                    Imgcodecs.IMREAD_COLOR
            );

            if (result.empty()) {

                result.release();

                throw new IOException(
                        "Unable to decode image using OpenCV"
                );
            }

            return result;

        } finally {

            encoded.release();
        }
    }

    public Mat preprocess(Mat source) {

        if (source == null || source.empty()) {

            throw new IllegalArgumentException(
                    "Source image is empty"
            );
        }

        if (source.rows() < 3 || source.cols() < 3) {

            throw new IllegalArgumentException(
                    "Source image is too small"
            );
        }

        Mat gray = new Mat();

        Mat binary = new Mat();

        try {

            if (source.channels() == 1) {

                source.copyTo(gray);

            } else if (source.channels() == 4) {

                Imgproc.cvtColor(
                        source,
                        gray,
                        Imgproc.COLOR_BGRA2GRAY
                );

            } else {

                Imgproc.cvtColor(
                        source,
                        gray,
                        Imgproc.COLOR_BGR2GRAY
                );
            }

            Imgproc.GaussianBlur(
                    gray,
                    gray,
                    new Size(3, 3),
                    0
            );

            int blockSize = Math.min(
                    31,
                    Math.min(gray.rows(), gray.cols())
            );

            if (blockSize % 2 == 0) {
                blockSize--;
            }

            Imgproc.adaptiveThreshold(

                    gray,

                    binary,

                    255,

                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,

                    Imgproc.THRESH_BINARY_INV,

                    blockSize,

                    10
            );

            return binary;

        } catch (RuntimeException e) {

            binary.release();

            throw e;

        } finally {

            gray.release();
        }
    }
}
