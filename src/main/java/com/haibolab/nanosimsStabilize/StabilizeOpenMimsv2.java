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

import ai.djl.ndarray.types.Shape;
import ai.djl.translate.TranslateException;

import ai.djl.util.Pair;
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
public class StabilizeOpenMimsv2<T extends RealType<T>> implements Command {
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
        String[] datasetNames = new String[openMimsImages.length+1];
        for (int index = 0; index < openMimsImages.length; index++) {
            ImagePlus img = openMimsImages[index];
            datasetNames[index] = img.getTitle();
        }
        datasetNames[openMimsImages.length] = "summation";
        GenericDialog dialog = new GenericDialog("Choose your channel");
        dialog.addMessage("Choose one channel as reference (Strong signals are recommended).");
        dialog.addChoice("Selected channel", datasetNames, datasetNames[0]);
        dialog.addCheckbox("Apply bilinear interpolation to all channels",false);
        dialog.showDialog();
        if (dialog.wasOKed()) {
            String basetile = dialog.getNextChoice();
            Boolean forceBilinear = dialog.getNextBoolean();
            DialogPrompt.Result result = uiService.showDialog(
                    "use " + basetile + " to register?",
                    "Confirmation",
                    DialogPrompt.MessageType.QUESTION_MESSAGE,
                    DialogPrompt.OptionType.YES_NO_OPTION);
            if (result == DialogPrompt.Result.YES_OPTION) {
                // User selected 'Yes
                try {
                    warpOnebyOne(basetile, forceBilinear);
                } catch (ModelException | TranslateException | IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void warpOnebyOne(String baseTitle, Boolean forceBilinear) throws ModelException, TranslateException, IOException {
        NDManager manager = NDManager.newBaseManager();
        com.nrims.UI uiinstance = com.nrims.UI.getInstance();
        Model modelBilinear = getBilinearModel();
        Model modelNearest = getNearestModel();
        MimsPlus[] openMimsImages = uiinstance.getOpenMassImages();
        OpticalFlowEstimator opticalFlowEstimator = new OpticalFlowEstimator();
        List<RandomAccessibleInterval<T>> openImgRai = new ArrayList<>();
        List<NDArray> openImgNdarry = new ArrayList<>();

        int baseChannel = 0;
        for (int index = 0; index < openMimsImages.length; index++) {
            ImagePlus img = openMimsImages[index];
            NDArray ndArray = Util.convertMultiFrameImagePlusToNDArray(img, manager);
            openImgNdarry.add(ndArray);
            if (img.getTitle().equals(baseTitle)) {
                baseChannel = index;
            }
            RandomAccessibleInterval<T> rai = ImageJFunctions.wrapReal(img);
            openImgRai.add(rai);
        }
        NDArray summation = manager.zeros(openImgNdarry.get(0).getShape(), openImgNdarry.get(0).getDataType());
        if (baseTitle.equals("summation")){
            System.out.println("using summation as references");
            for (int index = 0; index < openMimsImages.length; index++) {
                summation = summation.add(openImgNdarry.get(index));
            }
        }
        long[] dimensions = openImgRai.get(0).dimensionsAsLongArray();
        statusService.showProgress(0, (int) dimensions[2]);
        NDList outputSequence = new NDList(openMimsImages.length);
        NDList firstSequence = new NDList(openMimsImages.length);
        for (int channelIndex = 0; channelIndex < openMimsImages.length; channelIndex++) {
            NDArray ndArray = openImgNdarry.get(channelIndex);
            firstSequence.add(ndArray.get(":, :, 0"));
        }
        NDArray firstFrame =  NDArrays.stack(firstSequence, 2); // 256, 256, channel_number
        List<Pair<NDArray, NDArray>> opticalFlowInputs = new ArrayList<>();
//            System.out.println("first image initialization" + outputSequence.get(0).getShape() + "index" + outputSequence.get(0).get(61, 87, 3) + "orginal image" + image.getAt(61, 78, 0, 3) +"orginal image transposed " + image.getAt(87, 61, 0, 3));
        for (int sequenceIndex = 1; sequenceIndex < dimensions[2]; sequenceIndex++) {
            int gtIndex = sequenceIndex - 1;
            if (baseTitle.equals("summation")){
                opticalFlowInputs.add(new Pair<>(summation.get(":, :, " + gtIndex), summation.get(":, :, " + sequenceIndex)));
            }
            else {
                opticalFlowInputs.add(new Pair<>(openImgNdarry.get(baseChannel).get(":, :, " + gtIndex), openImgNdarry.get(baseChannel).get(":, :, " + sequenceIndex)));
            }
        }

        List<float[]> opticalFlowListAll = opticalFlowEstimator.generateOpticalFlowList(opticalFlowInputs);
        for (int channelIndex = 0; channelIndex < openMimsImages.length; channelIndex++) {
            statusService.showProgress(channelIndex, openMimsImages.length);
            NDList channelSequence = new NDList((int) dimensions[2]);
            channelSequence.add(firstFrame.get(":, :, "+ channelIndex));
            List<Pair<NDArray, NDArray>> warpInputs = new ArrayList<>();
            for (int sequenceIndex = 1; sequenceIndex < dimensions[2]; sequenceIndex++) {
                float[] opticalFlowList = opticalFlowListAll.get(sequenceIndex - 1);
                NDArray opticalFlowArray = manager.create(opticalFlowList).reshape(2, dimensions[0], dimensions[1]);
                NDArray imageArray = openImgNdarry.get(channelIndex).get(":, :, " + sequenceIndex);
                warpInputs.add(new Pair<>(imageArray, opticalFlowArray));
            }
            NDArray wfImgSingleChannelArray = warpInputs.get(0).getKey();
            List<float[]> imageWarpedList;
            if (wfImgSingleChannelArray.countNonzero().getLong() < 0.3 * dimensions[0] * dimensions[1] && (!forceBilinear)) {
                imageWarpedList = OpticalFlowEstimator.WarpBatch(warpInputs, modelNearest);
                System.out.println(channelIndex + " use nearest to warp");
            } else {
                imageWarpedList = OpticalFlowEstimator.WarpBatch(warpInputs, modelBilinear);
            }
            for (int i = 0; i < imageWarpedList.size(); i++) {
                NDArray imageWarpedArray = manager.create(imageWarpedList.get(i)).reshape(3, dimensions[0], dimensions[1]).get("0, :, :");
                channelSequence.add(imageWarpedArray);
            }
            outputSequence.add(channelIndex, NDArrays.stack(channelSequence, 2)); // 256, 256, t
        }
        NDArray outputStack = NDArrays.stack(outputSequence, 3); // 256, 256, t, c
        for (int i = 0; i < openMimsImages.length; i++) {
            String tile = openMimsImages[i].getTitle();
            ImagePlus imagePlus = Util.convertNDArrayToImagePlus(outputStack.get(":, :, :," + i), tile);
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
            ij.command().run(StabilizeOpenMimsv2.class, true);
        }
    }
}
