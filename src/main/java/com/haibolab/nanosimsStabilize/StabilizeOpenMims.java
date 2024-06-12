/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package com.haibolab.nanosimsStabilize;

import ai.djl.Device;
import ai.djl.MalformedModelException;
import ai.djl.ModelException;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDArrays;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;

import ai.djl.translate.TranslateException;

import com.nrims.MimsPlus;
import com.nrims.ContrastAdjuster;
import ij.gui.GenericDialog;
import io.scif.*;

import io.scif.services.DatasetIOService;
import io.scif.services.FormatService;
import net.imagej.*;

import net.imagej.display.ImageDisplayService;
import net.imagej.ops.OpService;

import net.imglib2.RandomAccessibleInterval;

import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import ij.ImagePlus;


import net.imglib2.view.IntervalView;

import org.scijava.app.StatusService;
import org.scijava.command.Command;
import org.scijava.io.location.LocationService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.ui.DialogPrompt;
import org.scijava.ui.UIService;

import ai.djl.Model;

import java.io.File;
import java.io.IOException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Plugin(type = Command.class, menuPath = "Plugins>NanoSIMS Stabilizer>Stabilize")
public class StabilizeOpenMims<T extends RealType<T>> implements Command {
    //
    // Feel free to add more parameters here...
    //

    @Parameter
    private Dataset currentData;

    @Parameter
    private UIService uiService;

    @Parameter
    private OpService opService;
    @Parameter
    private LogService logService;
    @Parameter
    private ImageJService imageJService;
    @Parameter
    private DatasetService datasetService;
    @Parameter
    private StatusService statusService;
    @Parameter
    private DatasetIOService datasetIOService;
    @Parameter
    private ImageDisplayService imageDisplayService;
    @Parameter
    private FormatService formatService;
    @Parameter
    private LocationService locationService;
    @Parameter

    private MetadataService metadataService;

    @Override
    public void run() {
        com.nrims.UI uiinstance = com.nrims.UI.getInstance();

        MimsPlus[] openMimsImages = uiinstance.getOpenMassImages();

        if (openMimsImages.length == 0) {
            uiService.showDialog("No images open");
            return;
        }
        String[] datasetNames = new String[openMimsImages.length];
        for (int index = 0; index < openMimsImages.length; index++) {
            ImagePlus img = openMimsImages[index];
            datasetNames[index] = img.getTitle();
        }
        GenericDialog dialog = new GenericDialog("Choose your channel");
        dialog.addMessage("Choose one channel as reference (Strong signals are recommended).");
        dialog.addChoice("Selected channel", datasetNames, datasetNames[0]);
        dialog.showDialog();
        if (dialog.wasOKed()) {
            String basetile = dialog.getNextChoice();
            DialogPrompt.Result result = uiService.showDialog(
                    "use " + basetile + " to register?",
                    "Confirmation",
                    DialogPrompt.MessageType.QUESTION_MESSAGE,
                    DialogPrompt.OptionType.YES_NO_OPTION);
            if (result == DialogPrompt.Result.YES_OPTION) {
                // User selected 'Yes
                try {
                    warpOnebyOne(basetile);
                } catch (ModelException | TranslateException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void warpOnebyOne(String baseTitle) throws ModelException, TranslateException, IOException {
        NDManager manager = NDManager.newBaseManager();
        com.nrims.UI uiinstance = com.nrims.UI.getInstance();
        Model modelBilinear = getBilinearModel();
        Model modelNearest = getNearestModel();
        MimsPlus[] openMimsImages = uiinstance.getOpenMassImages();

        OpticalFlowEstimator opticalFlowEstimator = new OpticalFlowEstimator();
        List<RandomAccessibleInterval<T>> openImgRai = new ArrayList<>();
        int baseChannel = 0;
        for (int index = 0; index < openMimsImages.length; index++) {
            ImagePlus img = openMimsImages[index];
            if (img.getTitle().equals(baseTitle)) {
                baseChannel = index;
            }
            RandomAccessibleInterval<T> rai = ImageJFunctions.wrapReal(img);
            openImgRai.add(rai);
        }

        long[] dimensions = openImgRai.get(0).dimensionsAsLongArray();
        statusService.showProgress(0, (int) dimensions[2]);
        NDList outputSequence = new NDList((int) dimensions[2]);
        NDList channelSequence = new NDList(openMimsImages.length);
        for (int channelIndex = 0; channelIndex < openMimsImages.length; channelIndex++) {

            IntervalView<T> wfImgSingleChannel = opService.transform().hyperSliceView(openImgRai.get(channelIndex), 2, 0);
            NDArray wfImgSingleChannelArray = Util.intervalViewToNDArray(wfImgSingleChannel, manager);
            channelSequence.add(wfImgSingleChannelArray);
        }
        outputSequence.add(0, NDArrays.stack(channelSequence, 2));
        for (int sequenceIndex = 1; sequenceIndex < dimensions[2]; sequenceIndex++) {
            System.out.println("start processing sequence index" + sequenceIndex);
            IntervalView<T> wfImg = opService.transform().hyperSliceView(openImgRai.get(baseChannel), 2, sequenceIndex);
            statusService.showProgress(sequenceIndex, (int) dimensions[2]);

            NDArray wfImgArray = Util.intervalViewToNDArray(wfImg, manager);
            NDArray gtImgArray = outputSequence.get(outputSequence.size() - 1).get(":, :," + baseChannel);
            try {
                float[] opticalFlowList = opticalFlowEstimator.generateOpticalFlow(gtImgArray, wfImgArray);
                NDArray opticalFlowArray = manager.create(opticalFlowList).reshape(2, dimensions[0], dimensions[1]);
                channelSequence.clear();
                for (int channelIndex = 0; channelIndex < openMimsImages.length; channelIndex++) {
                    IntervalView<T> wfImgSingleChannel = opService.transform().hyperSliceView(openImgRai.get(channelIndex), 2, sequenceIndex);
                    NDArray wfImgSingleChannelArray = Util.intervalViewToNDArray(wfImgSingleChannel, manager);
                    float[] imageWarpedList;
                    if (wfImgSingleChannelArray.countNonzero().getLong() < 0.3 * dimensions[0] * dimensions[1]) {
                        imageWarpedList = OpticalFlowEstimator.WarpNearest(wfImgSingleChannelArray, opticalFlowArray, modelNearest);
                        System.out.println(channelIndex + " use nearest to warp");
                    } else {
                        System.out.println(channelIndex + " use bilinear to warp");
                        imageWarpedList = OpticalFlowEstimator.Warp(wfImgSingleChannelArray, opticalFlowArray, modelBilinear);
                    }
                    NDArray imageWarpedArray = manager.create(imageWarpedList).reshape(3, dimensions[0], dimensions[1]).get("0, :, :");
                    channelSequence.add(channelIndex, imageWarpedArray);
                }
                outputSequence.add(sequenceIndex, NDArrays.stack(channelSequence, 2));
            } catch (IOException | ModelException | TranslateException e) {
                e.printStackTrace();
            }
        }
        NDArray outputStack = NDArrays.stack(outputSequence, 2);
        for (int i = 0; i < openMimsImages.length; i++) {
            String tile = openMimsImages[i].getTitle();
            ImagePlus imagePlus = Util.convertNDArrayToImagePlus(outputStack.get(":, :,:," + i), tile);
            Util.autoAdjust(imagePlus);
            openMimsImages[i].setImage(imagePlus);
        }
        statusService.showProgress((int) dimensions[2], (int) dimensions[2]);
        uiService.showDialog("stabilization finished");
    }


    public Model getBilinearModel() throws IOException, MalformedModelException {

        Path warpModelPath = Util.getResourcePath("warp_bilinearv2.zip");
        Model model = Model.newInstance("warp", Device.cpu());
        model.load(warpModelPath);
        return model;
    }

    public Model getNearestModel() throws IOException, MalformedModelException {
        Path warpModelPath = Util.getResourcePath("warp_nearestv2.zip");
        Model model = Model.newInstance("warp", Device.cpu());
        model.load(warpModelPath);
        return model;
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        System.setProperty("DJL_ENGINE", "PyTorch");
        System.setProperty("ai.djl.pytorch.disableGpu", "true");
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // ask the user for a file to open
        final File file = ij.ui().chooseFile(null, "open");

        if (file != null) {
            // load the dataset
            final Dataset dataset = ij.scifio().datasetIO().open(file.getPath());

            // show the image
            ij.ui().show(dataset);

            // invoke the plugin
            ij.command().run(StabilizeOpenMims.class, true);
        }
    }
}
