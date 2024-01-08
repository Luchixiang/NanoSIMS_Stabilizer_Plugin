package com.haibolab.nanosimsStabilize;

import ai.djl.modality.cv.Image;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.translate.Batchifier;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;
import ai.djl.util.Pair;

class WarpTranslatorArray implements Translator<Pair<NDArray, NDArray>, float[]> {

    @Override
    public NDList processInput(TranslatorContext ctx, Pair<NDArray, NDArray> input) {

        NDManager manager = ctx.getNDManager();
        NDList list = new NDList();

        if (input.getKey() == null || input.getValue() == null) {
            throw new IllegalArgumentException("Input images cannot be null.");
        }
        list.add(0, input.getKey().expandDims(2).repeat(2, 3).transpose(2, 0, 1).toType(DataType.FLOAT32, false));
        list.add(1, input.getValue());
        return list;
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        NDArray flowArray = list.get(0);
        float[] flow = flowArray.toFloatArray();

        return flow;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
}