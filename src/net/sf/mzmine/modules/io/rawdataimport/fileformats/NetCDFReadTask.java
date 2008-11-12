/*
 * Copyright 2006-2008 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine; if not, write to the Free Software Foundation, Inc., 51 Franklin St,
 * Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.io.rawdataimport.fileformats;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.mzmine.data.MzDataPoint;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.RawDataFileWriter;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.data.impl.SimpleScan;
import net.sf.mzmine.main.MZmineCore;
import net.sf.mzmine.taskcontrol.Task;
import ucar.ma2.Array;
import ucar.ma2.IndexIterator;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

/**
 * 
 */
public class NetCDFReadTask implements Task {

    private Logger logger = Logger.getLogger(this.getClass().getName());

    private File originalFile;
    private NetcdfFile inputFile;
    private TaskStatus status = TaskStatus.WAITING;
    private String errorMessage;

    private int parsedScans;
    private int totalScans = -1, numberOfGoodScans, scanNum = 0;

    private Hashtable<Integer, Integer[]> scansIndex;
    private Hashtable<Integer, Double> scansRetentionTimes;

    private RawDataFileWriter newMZmineFile;

    private Variable massValueVariable;
    private Variable intensityValueVariable;

    /**
     * 
     */
    public NetCDFReadTask(File fileToOpen) {
        originalFile = fileToOpen;
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getTaskDescription()
     */
    public String getTaskDescription() {
        return "Opening file " + originalFile;
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getFinishedPercentage()
     */
    public double getFinishedPercentage() {
        return totalScans == 0 ? 0 : (double) parsedScans / totalScans;
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getStatus()
     */
    public TaskStatus getStatus() {
        return status;
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#getErrorMessage()
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {

        // Update task status
        status = TaskStatus.PROCESSING;
        logger.info("Started parsing file " + originalFile);

        try {
            // Create new RawDataFile instance
            newMZmineFile = MZmineCore.createNewFile(originalFile.getName());

            // Open file
            this.startReading();

            // Parse scans
            Scan buildingScan;
            while ((buildingScan = this.readNextScan()) != null) {

                // Check if cancel is requested
                if (status == TaskStatus.CANCELED) {
                    return;
                }
                // buildingFile.addScan(scan);
                newMZmineFile.addScan(buildingScan);
                parsedScans++;

            }

            // Close file
            this.finishReading();
            RawDataFile finalRawDataFile = newMZmineFile.finishWriting();
            MZmineCore.getCurrentProject().addFile(finalRawDataFile);

        } catch (Throwable e) {
            // e.printStackTrace();
            logger.log(Level.SEVERE, "Could not open file "
                    + originalFile.getPath(), e);
            errorMessage = e.toString();
            status = TaskStatus.ERROR;
            return;
        }

        logger.info("Finished parsing " + originalFile + ", parsed "
                + parsedScans + " scans");

        // Update task status
        status = TaskStatus.FINISHED;

    }

    public void startReading() throws IOException {

        // Open NetCDF-file
        try {
            inputFile = new NetcdfFile(originalFile.getPath());
        } catch (Exception e) {
            logger.severe(e.toString());
            throw (new IOException("Couldn't open input file" + originalFile));
        }

        // Find mass_values and intensity_values variables
        massValueVariable = inputFile.findVariable("mass_values");
        if (massValueVariable == null) {
            logger.severe("Could not find variable mass_values");
            throw (new IOException("Could not find variable mass_values"));
        }

        intensityValueVariable = inputFile.findVariable("intensity_values");
        if (intensityValueVariable == null) {
            logger.severe("Could not find variable intensity_values");
            throw (new IOException("Could not find variable intensity_values"));
        }

        // Read number of scans
        Variable scanIndexVariable = inputFile.findVariable("scan_index");
        if (scanIndexVariable == null) {
            logger.severe("Could not find variable scan_index from file "
                    + originalFile);
            throw (new IOException(
                    "Could not find variable scan_index from file "
                            + originalFile));
        }
        totalScans = scanIndexVariable.getShape()[0];

        // Read scan start positions
        int[] scanStartPositions = new int[totalScans + 1]; // Extra element is
        // required, because
        // element
        // totalScans+1 is
        // used to find the
        // stop position for
        // last scan

        Array scanIndexArray = null;
        try {
            scanIndexArray = scanIndexVariable.read();
        } catch (Exception e) {
            logger.severe(e.toString());
            throw (new IOException(
                    "Could not read from variable scan_index from file "
                            + originalFile));
        }

        IndexIterator scanIndexIterator = scanIndexArray.getIndexIterator();
        int ind = 0;
        while (scanIndexIterator.hasNext()) {
            scanStartPositions[ind] = ((Integer) scanIndexIterator.next()).intValue();
            ind++;
        }
        scanIndexIterator = null;
        scanIndexArray = null;
        scanIndexVariable = null;

        // Calc stop position for the last scan
        scanStartPositions[totalScans] = (int) massValueVariable.getSize(); // This
        // defines
        // the
        // end
        // index
        // of
        // the
        // last
        // scan

        // Read retention times
        double[] retentionTimes = new double[totalScans];

        Variable scanTimeVariable = inputFile.findVariable("scan_acquisition_time");
        if (scanTimeVariable == null) {
            logger.severe("Could not find variable scan_acquisition_time from file "
                    + originalFile);
            throw (new IOException(
                    "Could not find variable scan_acquisition_time from file "
                            + originalFile));
        }
        Array scanTimeArray = null;
        try {
            scanTimeArray = scanTimeVariable.read();
        } catch (Exception e) {
            logger.severe(e.toString());
            throw (new IOException(
                    "Could not read from variable scan_acquisition_time from file "
                            + originalFile));
        }

        IndexIterator scanTimeIterator = scanTimeArray.getIndexIterator();
        ind = 0;
        while (scanTimeIterator.hasNext()) {
            if (scanTimeVariable.getDataType().getPrimitiveClassType() == float.class) {
                retentionTimes[ind] = ((Double) scanTimeIterator.next()).floatValue();
            }
            if (scanTimeVariable.getDataType().getPrimitiveClassType() == double.class) {
                retentionTimes[ind] = ((Double) scanTimeIterator.next()).doubleValue();
            }
            ind++;
        }

        scanTimeIterator = null;
        scanTimeArray = null;
        scanTimeVariable = null;

        // Fix problems caused by new QStar data converter
        // assume scan is missing when scan_index[i]<0
        // for these scans, fix variables:
        // - scan_acquisition_time: interpolate/extrapolate using times of
        // present scans
        // - scan_index: fill with following good value

        // Calculate number of good scans
        numberOfGoodScans = 0;
        for (int i = 0; i < totalScans; i++) {
            if (scanStartPositions[i] >= 0) {
                numberOfGoodScans++;
            }
        }

        // Is there need to fix something?
        if (numberOfGoodScans < totalScans) {

            // Fix scan_acquisition_time
            // - calculate average delta time between present scans
            double sumDelta = 0;
            int n = 0;
            for (int i = 0; i < totalScans; i++) {
                // Is this a present scan?
                if (scanStartPositions[i] >= 0) {
                    // Yes, find next present scan
                    for (int j = i + 1; j < totalScans; j++) {
                        if (scanStartPositions[j] >= 0) {
                            sumDelta += (retentionTimes[j] - retentionTimes[i])
                                    / ((double) (j - i));
                            n++;
                            break;
                        }
                    }
                }
            }
            double avgDelta = sumDelta / (double) n;
            // - fill missing scan times using nearest good scan and avgDelta
            for (int i = 0; i < totalScans; i++) {
                // Is this a missing scan?
                if (scanStartPositions[i] < 0) {
                    // Yes, find nearest present scan
                    int nearestI = Integer.MAX_VALUE;
                    for (int j = 1; 1 < 2; j++) {
                        if ((i + j) < totalScans) {
                            if (scanStartPositions[i + j] >= 0) {
                                nearestI = i + j;
                                break;
                            }
                        }
                        if ((i - j) >= 0) {
                            if (scanStartPositions[i - j] >= 0) {
                                nearestI = i + j;
                                break;
                            }
                        }

                        // Out of bounds?
                        if (((i + j) >= totalScans) && ((i - j) < 0)) {
                            break;
                        }
                    }

                    if (nearestI != Integer.MAX_VALUE) {

                        retentionTimes[i] = retentionTimes[nearestI]
                                + (i - nearestI) * avgDelta;

                    } else {
                        if (i > 0) {
                            retentionTimes[i] = retentionTimes[i - 1];
                        } else {
                            retentionTimes[i] = 0;
                        }
                        logger.severe("ERROR: Could not fix incorrect QStar scan times.");
                    }
                }
            }

            // Fix scanStartPositions by filling gaps with next good value
            for (int i = 0; i < totalScans; i++) {
                if (scanStartPositions[i] < 0) {
                    for (int j = i + 1; j < (totalScans + 1); j++) {
                        if (scanStartPositions[j] >= 0) {
                            scanStartPositions[i] = scanStartPositions[j];
                            break;
                        }
                    }
                }
            }
        }

        // Collect information about retention times, start positions and
        // lengths for scans
        scansRetentionTimes = new Hashtable<Integer, Double>();
        scansIndex = new Hashtable<Integer, Integer[]>();
        for (int i = 0; i < totalScans; i++) {

            Integer scanNum = new Integer(i);

            Integer[] startAndLength = new Integer[2];
            startAndLength[0] = scanStartPositions[i];
            startAndLength[1] = scanStartPositions[i + 1]
                    - scanStartPositions[i];

            scansRetentionTimes.put(scanNum, new Double(retentionTimes[i]));
            scansIndex.put(scanNum, startAndLength);

        }

        scanStartPositions = null;
        retentionTimes = null;

    }

    /**
     * @see net.sf.mzmine.io.RawDataFileReader#finishReading()
     */
    public void finishReading() throws IOException {
        inputFile.close();
    }

    /**
     * Reads one scan from the file. Requires that general information has
     * already been read.
     */
    public Scan readNextScan() throws IOException {

        // Get scan starting position and length
        int[] scanStartPosition = new int[1];
        int[] scanLength = new int[1];
        Integer[] startAndLength = scansIndex.get(scanNum);

        // End of file
        if (startAndLength == null) {
            return null;
        }
        scanStartPosition[0] = startAndLength[0];
        scanLength[0] = startAndLength[1];

        // Get retention time of the scan
        Double retentionTime = scansRetentionTimes.get(new Integer(scanNum));
        if (retentionTime == null) {
            logger.severe("Could not find retention time for scan " + scanNum);
            throw (new IOException("Could not find retention time for scan "
                    + scanNum));
        }

        // An empty scan needs some special attention..
        if (scanLength[0] == 0) {
            scanNum++;
            return new SimpleScan(scanNum, 1, retentionTime.doubleValue(), -1,
                    0, null, new MzDataPoint[0], false);
        }

        // Read mass and intensity values
        Array massValueArray;
        Array intensityValueArray;
        try {
            massValueArray = massValueVariable.read(scanStartPosition,
                    scanLength);
            intensityValueArray = intensityValueVariable.read(
                    scanStartPosition, scanLength);
        } catch (Exception e) {
            logger.log(
                    Level.SEVERE,
                    "Could not read from variables mass_values and/or intensity_values.",
                    e);
            throw (new IOException(
                    "Could not read from variables mass_values and/or intensity_values."));
        }

        // Translate values to plain Java arrays
        double[] massValues = null;

        if (massValueVariable.getDataType().getPrimitiveClassType() == double.class) {
            massValues = (double[]) massValueArray.copyTo1DJavaArray();
        }
        if (massValueVariable.getDataType().getPrimitiveClassType() == float.class) {
            float[] floatMassValues = (float[]) massValueArray.copyTo1DJavaArray();
            massValues = new double[floatMassValues.length];
            for (int j = 0; j < massValues.length; j++) {
                massValues[j] = (double) (floatMassValues[j]);
            }
            floatMassValues = null;
        }

        double[] intensityValues = null;

        if (intensityValueVariable.getDataType().getPrimitiveClassType() == int.class) {
            int[] intIntensityValues = (int[]) intensityValueArray.copyTo1DJavaArray();
            intensityValues = new double[intIntensityValues.length];
            for (int j = 0; j < intensityValues.length; j++) {
                intensityValues[j] = (double) (intIntensityValues[j]);
            }
            intIntensityValues = null;
        }
        if (intensityValueVariable.getDataType().getPrimitiveClassType() == double.class) {
            intensityValues = (double[]) intensityValueArray.copyTo1DJavaArray();
        }
        if (intensityValueVariable.getDataType().getPrimitiveClassType() == float.class) {
            float[] floatIntensityValues = (float[]) intensityValueArray.copyTo1DJavaArray();
            intensityValues = new double[floatIntensityValues.length];
            for (int j = 0; j < intensityValues.length; j++) {
                intensityValues[j] = (double) (floatIntensityValues[j]);
            }
            floatIntensityValues = null;
        }

        MzDataPoint completeDataPoints[] = new MzDataPoint[massValues.length];
        MzDataPoint tempDataPoints[] = new MzDataPoint[massValues.length];
        for (int i = 0; i < massValues.length; i++) {
            completeDataPoints[i] = new SimpleDataPoint(massValues[i],
                    intensityValues[i]);
        }

        /*
         * This section verifies DataPoints with intensity="0" and exclude them
         * from tempDataPoints array. Only accept some of these points because
         * they are part the left/right part of the peak.
         */

        int i, j;
        for (i = 0, j = 0; i < completeDataPoints.length; i++) {
            double intensity = completeDataPoints[i].getIntensity();
            double mz = completeDataPoints[i].getMZ();
            if (completeDataPoints[i].getIntensity() > 0) {
                tempDataPoints[j] = new SimpleDataPoint(mz, intensity);
                j++;
                continue;
            }
            if ((i > 0) && (completeDataPoints[i - 1].getIntensity() > 0)) {
                tempDataPoints[j] = new SimpleDataPoint(mz, intensity);
                j++;
                continue;
            }
            if ((i < completeDataPoints.length - 1)
                    && (completeDataPoints[i + 1].getIntensity() > 0)) {
                tempDataPoints[j] = new SimpleDataPoint(mz, intensity);
                j++;
                continue;
            }
        }

        scanNum++;

        SimpleScan buildingScan = new SimpleScan(scanNum, 1,
                retentionTime.doubleValue(), -1, 0, null, new MzDataPoint[0],
                false);

        if (i == j) {
            buildingScan.setCentroided(true);
            buildingScan.setDataPoints(tempDataPoints);
        } else {
            int sizeArray = j;
            MzDataPoint[] dataPoints = new MzDataPoint[j];

            System.arraycopy(tempDataPoints, 0, dataPoints, 0, sizeArray);
            buildingScan.setDataPoints(dataPoints);
        }

        // return new SimpleScan(scanNum, 1, retentionTime.doubleValue(), -1, 0,
        // null, dataPoints, false);
        return buildingScan;
    }

    /**
     * @see net.sf.mzmine.taskcontrol.Task#cancel()
     */
    public void cancel() {
        logger.info("Cancelling opening of NETCDF file " + originalFile);
        status = TaskStatus.CANCELED;
    }

}
