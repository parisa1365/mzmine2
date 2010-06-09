/*
 * Copyright 2006-2010 The MZmine 2 Development Team
 * 
 * This file is part of MZmine 2.
 * 
 * MZmine 2 is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * MZmine 2 is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * MZmine 2; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * St, Fifth Floor, Boston, MA 02110-1301 USA
 */

package net.sf.mzmine.modules.peaklistmethods.deconvolution;

import java.util.Arrays;

import net.sf.mzmine.data.ChromatographicPeak;
import net.sf.mzmine.data.DataPoint;
import net.sf.mzmine.data.IsotopePattern;
import net.sf.mzmine.data.PeakStatus;
import net.sf.mzmine.data.RawDataFile;
import net.sf.mzmine.data.Scan;
import net.sf.mzmine.data.impl.SimpleDataPoint;
import net.sf.mzmine.util.MathUtils;
import net.sf.mzmine.util.PeakUtils;
import net.sf.mzmine.util.Range;
import net.sf.mzmine.util.ScanUtils;

/**
 * ResolvedPeak
 * 
 */
public class ResolvedPeak implements ChromatographicPeak {

	// Data file of this chromatogram
	private RawDataFile dataFile;

	// Chromatogram m/z, RT, height, area
	private double mz, rt, height, area;

	// Scan numbers
	private int scanNumbers[];

	// We store the values of data points as double[] arrays in order to save
	// memory, which would be wasted by keeping a lot of instances of
	// SimpleDataPoint (each instance takes 16 or 32 bytes of extra memory)
	private double dataPointMZValues[], dataPointIntensityValues[];

	// Top intensity scan, fragment scan
	private int representativeScan, fragmentScan;

	// Ranges of raw data points
	private Range rawDataPointsIntensityRange, rawDataPointsMZRange,
			rawDataPointsRTRange;

	// Isotope pattern. Null by default but can be set later by deisotoping
	// method.
	private IsotopePattern isotopePattern;
	private int charge = 0;

	/**
	 * Initializes this peak using data points from a given chromatogram -
	 * regionStart marks the index of the first data point (inclusive),
	 * regionEnd marks the index of the last data point (inclusive)
	 */
	public ResolvedPeak(ChromatographicPeak chromatogram, int regionStart,
			int regionEnd) {

		this.dataFile = chromatogram.getDataFile();

		// Make an array of scan numbers of this peak
		scanNumbers = new int[regionEnd - regionStart + 1];
		System.arraycopy(dataFile.getScanNumbers(1), regionStart, scanNumbers,
				0, regionEnd - regionStart + 1);

		dataPointMZValues = new double[regionEnd - regionStart + 1];
		dataPointIntensityValues = new double[regionEnd - regionStart + 1];

		// We keep the m/z range specified by the chromatogram, instead of
		// determining it from the m/z data points. The reason is that in
		// continuous raw data, each m/z peak has a width. That width is
		// remembered in chromatogram.getRawDataPointsMZRange(), the width of
		// detected m/z values may be smaller
		rawDataPointsMZRange = chromatogram.getRawDataPointsMZRange();

		// Set raw data point ranges, height, rt and representative scan
		height = Double.MIN_VALUE;
		for (int i = 0; i < scanNumbers.length; i++) {

			DataPoint dp = chromatogram.getDataPoint(scanNumbers[i]);
			if (dp == null)
				continue;

			dataPointMZValues[i] = dp.getMZ();
			dataPointIntensityValues[i] = dp.getIntensity();

			if (rawDataPointsIntensityRange == null) {
				rawDataPointsIntensityRange = new Range(dp.getIntensity());
				rawDataPointsRTRange = new Range(dataFile.getScan(
						scanNumbers[i]).getRetentionTime());
			} else {
				rawDataPointsRTRange.extendRange(dataFile.getScan(
						scanNumbers[i]).getRetentionTime());
				rawDataPointsIntensityRange.extendRange(dp.getIntensity());
			}

			if (height < dp.getIntensity()) {
				height = dp.getIntensity();
				rt = dataFile.getScan(scanNumbers[i]).getRetentionTime();
				representativeScan = scanNumbers[i];
			}
		}

		// Calculate median m/z
		mz = MathUtils.calcQuantile(dataPointMZValues, 0.5f);

		// Update area
		area = 0;
		for (int i = 1; i < scanNumbers.length; i++) {
			double previousRT = dataFile.getScan(scanNumbers[i - 1])
					.getRetentionTime();
			double currentRT = dataFile.getScan(scanNumbers[i])
					.getRetentionTime();
			double previousHeight = dataPointIntensityValues[i - 1];
			double currentHeight = dataPointIntensityValues[i];
			area += (currentRT - previousRT) * (currentHeight + previousHeight)
					/ 2;
		}

		// Update fragment scan
		fragmentScan = ScanUtils.findBestFragmentScan(dataFile,
				rawDataPointsRTRange, rawDataPointsMZRange);

		if (fragmentScan > 0) {
			Scan fragmentScanObject = dataFile.getScan(fragmentScan);
			int precursorCharge = fragmentScanObject.getPrecursorCharge();
			if (precursorCharge > 0)
				this.charge = precursorCharge;
		}

	}

	/**
	 * This method returns a representative datapoint of this peak in a given
	 * scan
	 */
	public DataPoint getDataPoint(int scanNumber) {
		int index = Arrays.binarySearch(scanNumbers, scanNumber);
		if (index < 0)
			return null;
		SimpleDataPoint dp = new SimpleDataPoint(dataPointMZValues[index],
				dataPointIntensityValues[index]);
		return dp;
	}

	/**
	 * This method returns m/z value of the chromatogram
	 */
	public double getMZ() {
		return mz;
	}

	/**
	 * This method returns a string with the basic information that defines this
	 * peak
	 * 
	 * @return String information
	 */
	public String toString() {
		return PeakUtils.peakToString(this);
	}

	public double getArea() {
		return area;
	}

	public double getHeight() {
		return height;
	}

	public int getMostIntenseFragmentScanNumber() {
		return fragmentScan;
	}

	public PeakStatus getPeakStatus() {
		return PeakStatus.DETECTED;
	}

	public double getRT() {
		return rt;
	}

	public Range getRawDataPointsIntensityRange() {
		return rawDataPointsIntensityRange;
	}

	public Range getRawDataPointsMZRange() {
		return rawDataPointsMZRange;
	}

	public Range getRawDataPointsRTRange() {
		return rawDataPointsRTRange;
	}

	public int getRepresentativeScanNumber() {
		return representativeScan;
	}

	public int[] getScanNumbers() {
		return scanNumbers;
	}

	public RawDataFile getDataFile() {
		return dataFile;
	}

	public IsotopePattern getIsotopePattern() {
		return isotopePattern;
	}

	public void setIsotopePattern(IsotopePattern isotopePattern) {
		this.isotopePattern = isotopePattern;
	}

	public int getCharge() {
		return charge;
	}

	public void setCharge(int charge) {
		this.charge = charge;
	}

}
