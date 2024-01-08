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

class WarpTranslator implements Translator<Pair<Image, NDArray>, float[]> {

    @Override
    public NDList processInput(TranslatorContext ctx, Pair<Image, NDArray> input) {

        NDManager manager = ctx.getNDManager();
        NDList list = new NDList();

        if (input.getKey() == null || input.getValue() == null) {
            throw new IllegalArgumentException("Input images cannot be null.");
        }
        NDArray image1 = input.getKey().toNDArray(manager);
        list.add(0, input.getKey().toNDArray(manager).transpose(2, 0, 1).toType(DataType.FLOAT32, false));
        list.add(1, input.getValue());
        return list;
    }

    @Override
    public float[] processOutput(TranslatorContext ctx, NDList list) {
        NDArray flowArray = list.get(0);
        float[] flow = flowArray.toFloatArray();
//        Image optical_flow = ImageFactory.getInstance().fromNDArray(flow);
        return flow;
    }

    @Override
    public Batchifier getBatchifier() {
        return Batchifier.STACK;
    }
}