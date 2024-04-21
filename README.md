## NanoSIMS Stabilizer manual

This repository contains the source code for our NanoSIMS stabilizer ImageJ plugin. 

If you prefer running with Python code, which allows batch processing and GPU acceleration, please refer to https://github.com/Luchixiang/NanoSIMS_Stabilizer_Python. 

For more information, please refer to our [project page](https://www.haibojianglab.com/nanosims-stabilizer). 
### Install Plugin

Please note that we don't support macOS before 11.  

1. Download the plugin jar files through the [link](https://connecthkuhk-my.sharepoint.com/:f:/g/personal/u3590540_connect_hku_hk/Ejyw6saUUttCkM6umHp4L5YB7MmQ9e3_fSJ8PNjlZiCgUg?e=Y4krzx) according to your system (Linux, macOS, Windows). 
3. move all the jar files into the Fiji plugins folder and restart the ImageJ finish the installation.

![image-20230822121808072](./img/install.png)

### Run Correction

1. Ensure that you have installed the [OpenMIMS plugin](https://usermanual.wiki/Pdf/OpenMimsManual.682350371.pdf)
2. Open your NanoSIMS file in OpenMIMS. ![image-20240108200032432](./img/openmims.jpg)

3. Click plugin -> NanoSIMS Stabilizer -> stabilize

4. Choose the channel as the reference channel where the transformation map is calculated and then applied to other channels. Strong signals are recommended. 

   ![image-20240108200232561](./img/channel.jpg)

5. Correction Done. 