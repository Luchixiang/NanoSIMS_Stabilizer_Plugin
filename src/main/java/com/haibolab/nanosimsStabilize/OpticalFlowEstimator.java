package com.haibolab.nanosimsStabilize;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.translate.TranslateException;
import ai.djl.util.Pair;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class OpticalFlowEstimator {

    public static float[] Warp(NDArray image, NDArray flow, Model model) throws IOException, MalformedModelException, TranslateException {

        WarpTranslatorArray translator = new WarpTranslatorArray();
        Predictor<Pair<NDArray, NDArray>, float[]> predictor = model.newPredictor(translator, Device.cpu());
        try (NDManager manager = NDManager.newBaseManager()) {
            // Predict the optical flow
            float[] warpedImage = predictor.predict(new Pair<>(image, flow));
            return warpedImage;
        }
    }
    public static float[] WarpNearest(NDArray image, NDArray flow, Model model) throws IOException, MalformedModelException, TranslateException {

        WarpTranslatorArray translator = new WarpTranslatorArray();
        Predictor<Pair<NDArray, NDArray>, float[]> predictor = model.newPredictor(translator,Device.cpu());
        try (NDManager manager = NDManager.newBaseManager()) {
            // Predict the optical flow
            float[] warpedImage = predictor.predict(new Pair<>(image, flow));
            return warpedImage;
        }
    }
    public static float[] generateOpticalFlow(NDArray gtImg, NDArray wfImg) throws IOException, ModelException, TranslateException {
        // Load the model
        Path modelPath = Util.getResourcePath("raft_model.zip");
        Model model = Model.newInstance("RAFT");
        model.load(modelPath);
        OpticalFlowTranslatorArray translator = new OpticalFlowTranslatorArray();
        Predictor<Pair<NDArray, NDArray>, float[]> predictor = model.newPredictor(translator, Device.cpu());
        try (NDManager manager = NDManager.newBaseManager()) {
            // Predict the optical flow
            float[] opticalFlow = predictor.predict(new Pair<>(gtImg, wfImg));
            return opticalFlow;
        }
    }
    public static List<float[]> generateOpticalFlowList(List<Pair<NDArray, NDArray>> imgs) throws IOException, ModelException, TranslateException {
        // Load the model
        Path modelPath = Util.getResourcePath("raft_model.zip");
        Model model = Model.newInstance("RAFT", Device.cpu());
        model.load(modelPath);
        OpticalFlowTranslatorArray translator = new OpticalFlowTranslatorArray();
        Predictor<Pair<NDArray, NDArray>, float[]> predictor = model.newPredictor(translator,Device.cpu());

        List<float[]> opticalFlow = predictor.batchPredict(imgs);
        return opticalFlow;

    }
}
