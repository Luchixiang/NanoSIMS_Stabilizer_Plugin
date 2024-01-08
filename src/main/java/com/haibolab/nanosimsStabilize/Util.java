package com.haibolab.nanosimsStabilize;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.index.NDIndex;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Collections;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class Util {

    public static Path getResourcePath(String resourceName) throws IOException {
        try (InputStream inputStream = Util.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Resource not found: " + resourceName);
            }

            Path tempFile = Files.createTempFile("temp_raft_model", ".zip");
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        }
    }
    public static <T extends RealType<T>> NDArray intervalViewToNDArray(IntervalView<T> intervalView, NDManager manager) {
        long[] dimensions = new long[intervalView.numDimensions()];
        intervalView.dimensions(dimensions);

        NDArray ndArray = manager.create(new Shape(dimensions));
//        Cursor<T> cursor = intervalView.cursor();
        Cursor<?> cursor = intervalView.cursor();
        while (cursor.hasNext()) {
            cursor.fwd();
            long[] position = new long[intervalView.numDimensions()];
            cursor.localize(position);
            long tmp = position[1];
            position[1] = position[0];
            position[0] = tmp;
            // Convert the value to float, assuming it's a numeric type
            float value = ((FloatType) cursor.get()).getRealFloat();
            ndArray.set(new NDIndex(position), value);
        }
        return ndArray;
    }
    public static RandomAccessibleInterval<FloatType> convertNDArrayToImageJ(NDArray array) {
        long[] dimensions = array.getShape().getShape();
        RandomAccessibleInterval<FloatType> img = ArrayImgs.floats(dimensions);

        Cursor<FloatType> cursor = Views.iterable(img).cursor();;
        long[] intShape = array.getShape().getShape();

        while (cursor.hasNext()) {
            cursor.fwd();
            long[] position = new long[dimensions.length];
            cursor.localize(position);
            float value = array.getFloat(position);
            cursor.get().set(value);
        }
        return img;
    }
    public static ImagePlus convertNDArrayToImagePlus(NDArray array, String tile) {
        int height = (int) array.getShape().get(0);
        int width = (int) array.getShape().get(1);
        int slices = (int) array.getShape().get(2);

        ImageStack stack = new ImageStack(width, height);
        for (int i = 0; i < slices; i++) {
            NDArray sliceArray = array.get(":, :, " + i);
            float[] floatArray = sliceArray.toFloatArray();
            ImageProcessor ip = new FloatProcessor(width, height, floatArray);
            stack.addSlice("Slice " + (i + 1), ip);
        }
        ImagePlus imagePlus = new ImagePlus(tile, stack);
        return imagePlus;
    }
}
