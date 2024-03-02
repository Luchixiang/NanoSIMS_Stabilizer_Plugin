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

    private Model optical_model;

    public OpticalFlowEstimator() throws IOException, MalformedModelException {
        Path modelPath = Util.getResourcePath("raft_modelv2.zip");
        this.optical_model= Model.newInstance("RAFT");
        this.optical_model.load(modelPath);
    }


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
    public float[] generateOpticalFlow(NDArray gtImg, NDArray wfImg) throws IOException, ModelException, TranslateException {
        // Load the model
        OpticalFlowTranslatorArray translator = new OpticalFlowTranslatorArray();
        Predictor<Pair<NDArray, NDArray>, float[]> predictor = this.optical_model.newPredictor(translator, Device.cpu());
        try (NDManager manager = NDManager.newBaseManager()) {
            // Predict the optical flow
            float[] opticalFlow = predictor.predict(new Pair<>(gtImg, wfImg));
            return opticalFlow;
        }
    }
}
