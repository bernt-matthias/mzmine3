/*
 * Copyright (c) 2004-2022 The MZmine Development Team
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.mzmine.datamodel;

import com.google.common.collect.Range;
import io.github.mzmine.util.ArrayUtils;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class represent one mass spectrum. Typically the implementation will store the m/z and
 * intensity values on disk using a MemoryMapStorage (see AbstractStorableSpectrum). This class is
 * Iterable, but there is an important point - to avoid consuming memory for each DataPoint
 * instance, we will iterate over the stored data points with a single DataPoint instance that is
 * incrementing an internal cursor. That means this code will work:
 * <p>
 * {@code for (DataPoint d : spectrum) System.out.println(d.getMz() + ":" + d.getIntensity();} but
 * this code will NOT work:
 * {@code ArrayList<DataPoint> list = new ArrayList<>(); list.addAll(spectrum);}
 */
public interface MassSpectrum extends Iterable<DataPoint> {

  /**
   * @return Number of m/z and intensity data points. This corresponds to the capacity of the
   * DoubleBuffers returned by getMzValues() and getIntensityValues()
   */
  int getNumberOfDataPoints();

  /**
   * Centroid / profile / thresholded
   *
   * @return Spectrum type
   */
  MassSpectrumType getSpectrumType();

  /**
   * @param dst A buffer the m/z values will be written into. The buffer should ideally have the
   *            size {@link #getNumberOfDataPoints()}. Some implementations of mass spectrum
   *            reallocate a new buffer, if the current buffer is not big enough.
   * @return The buffer the m/z values were written into. Usually the same as the supplied buffer.
   * However, a new buffer will be allocated if the original buffer is not big enough.
   */
  double[] getMzValues(@NotNull double[] dst);


  /**
   * @param dst A buffer the intensity values will be written into. The buffer should ideally have
   *            the size {@link #getNumberOfDataPoints()}. Some implementations of mass spectrum
   *            reallocate a new buffer, if the current buffer is not big enough.
   * @return The buffer the intensity values were written into. Usually the same as the supplied
   * buffer. However, a new buffer will be allocated if the original buffer is not big enough.
   */
  double[] getIntensityValues(@NotNull double[] dst);

  /**
   * @param index The data point index.
   * @return The m/z at the given index.
   */
  double getMzValue(int index);

  /**
   * @param index The data point index.
   * @return The intensity at the given index.
   */
  double getIntensityValue(int index);

  /**
   * @return The m/z value of the highest data point of this spectrum or null if the spectrum has 0
   * data points.
   */
  @Nullable Double getBasePeakMz();

  /**
   * @return The intensity value of the highest data point of this spectrum or null if the spectrum
   * has 0 data points.
   */
  @Nullable Double getBasePeakIntensity();

  /**
   * @return The index of the top intensity data point or null if the spectrum has 0 data points.
   */
  @Nullable Integer getBasePeakIndex();

  /**
   * @return The m/z range of this spectrum or null if the spectrum has 0 data points.
   */
  @Nullable Range<Double> getDataPointMZRange();

  /**
   * @return The sum of intensities of all data points or null if the spectrum has 0 data points.
   */
  @Nullable Double getTIC();


  /**
   * Searches for the given mz value - or the closest available signal in this spectrum. Copied from
   * {@link Arrays#binarySearch(double[], double)}
   *
   * @param mz                 search for this mz value
   * @param defaultToClosestMz return the closest mz value
   * @return this index of the given mz value or the closest available mz if checked. index of the
   * search key, if it is contained in the array; otherwise, (-(insertion point) - 1). The insertion
   * point is defined as the point at which the key would be inserted into the array: the index of
   * the first element greater than the key, or a.length if all elements in the array are less than
   * the specified key. Note that this guarantees that the return value will be >= 0 if and only if
   * the key is found.
   */
  default int binarySearch(double mz, boolean defaultToClosestMz) {
    return binarySearch(mz, defaultToClosestMz, 0, getNumberOfDataPoints());
  }

  /**
   * Searches for the given mz value - or the closest available signal in this spectrum. Copied from
   * {@link Arrays#binarySearch(double[], double)}
   *
   * @param mz                 search for this mz value
   * @param defaultToClosestMz return the closest mz value
   * @param fromIndex          inclusive lower end
   * @param toIndex            exclusive upper end
   * @return this index of the given mz value or the closest available mz if checked. index of the
   * search key, if it is contained in the array; otherwise, (-(insertion point) - 1). The insertion
   * point is defined as the point at which the key would be inserted into the array: the index of
   * the first element greater than the key, or a.length if all elements in the array are less than
   * the specified key. Note that this guarantees that the return value will be >= 0 if and only if
   * the key is found.
   */
  default int binarySearch(double mz, boolean defaultToClosestMz, int fromIndex, int toIndex) {
    if (toIndex == 0) {
      return -1;
    }
    final int numberOfDataPoints = getNumberOfDataPoints();
    ArrayUtils.rangeCheck(numberOfDataPoints, fromIndex, toIndex);

    int low = fromIndex;
    int high = toIndex - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1; // bit shift by 1 for sum of positive integers = / 2
      double midMz = getMzValue(mid);

      if (midMz < mz) {
        low = mid + 1;  // Neither mz is NaN, thisVal is smaller
      } else if (midMz > mz) {
        high = mid - 1; // Neither mz is NaN, thisVal is larger
      } else {
        long midBits = Double.doubleToLongBits(midMz);
        long keyBits = Double.doubleToLongBits(mz);
        if (midBits == keyBits) {
          return mid;  // Key found
        } else if (midBits < keyBits) {
          low = mid + 1;  // (-0.0, 0.0) or (!NaN, NaN)
        } else {
          high = mid - 1;  // (0.0, -0.0) or (NaN, !NaN)
        }
      }
    }
    if (defaultToClosestMz) {
      if (low >= numberOfDataPoints) {
        return numberOfDataPoints - 1;
      }
      // might be higher or lower
      final double adjacentMZ = getMzValue(low);
      // check for closest distance to mz
      if (adjacentMZ <= mz && low + 1 < numberOfDataPoints) {
        final double higherMZ = getMzValue(low + 1);
        return (Math.abs(mz - adjacentMZ) <= Math.abs(higherMZ - mz)) ? low : low + 1;
      } else if (adjacentMZ > mz && low - 1 >= 0) {
        final double lowerMZ = getMzValue(low - 1);
        return (Math.abs(mz - adjacentMZ) <= Math.abs(lowerMZ - mz)) ? low : low - 1;
      } else {
        // there was only one data point
        return low;
      }
    }
    return -(low + 1);  // key not found.
  }

  /**
   * Searches for the given mz value - or the closest available signal in this spectrum. Copied from
   * {@link Arrays#binarySearch(double[], double)}
   *
   * @param mz                 search for this mz value
   * @param defaultToClosestMz return the closest mz value
   * @return this index of the given mz value or the closest available mz if checked. index of the
   * search key, if it is contained in the array; otherwise, (-(insertion point) - 1). The insertion
   * point is defined as the point at which the key would be inserted into the array: the index of
   * the first element greater than the key, or a.length if all elements in the array are less than
   * the specified key. Note that this guarantees that the return value will be >= 0 if and only if
   * the key is found.
   */
  default int indexOf(double mz, boolean defaultToClosestMz) {
    return binarySearch(mz, defaultToClosestMz);
  }

}
