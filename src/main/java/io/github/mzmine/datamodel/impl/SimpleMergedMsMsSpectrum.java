/*
 * Copyright 2006-2021 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */

package io.github.mzmine.datamodel.impl;

import io.github.mzmine.datamodel.IMSRawDataFile;
import io.github.mzmine.datamodel.MassSpectrum;
import io.github.mzmine.datamodel.MergedMsMsSpectrum;
import io.github.mzmine.datamodel.MobilityScan;
import io.github.mzmine.modules.io.projectload.version_3_0.CONST;
import io.github.mzmine.util.MemoryMapStorage;
import io.github.mzmine.util.ParsingUtils;
import io.github.mzmine.util.maths.CenterFunction;
import io.github.mzmine.util.scans.ScanUtils;
import io.github.mzmine.util.scans.SpectraMerging;
import io.github.mzmine.util.scans.SpectraMerging.MergingType;
import java.util.List;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a merged spectrum from scans of the same raw data file. If a merged spectrum across
 * multiple raw data files is needed, implementations have to check for compatibility. {@link
 * SimpleMergedMsMsSpectrum#getScanNumber()} will return -1 to represent the artificial state of
 * this spectrum.
 *
 * @author https://github.com/SteffenHeu
 */
public class SimpleMergedMsMsSpectrum extends SimpleMergedMassSpectrum implements
    MergedMsMsSpectrum {

  public static final String XML_ELEMENT = "simplemergedmsmssprectrum";
  protected static final String XML_MZS_ELEMENT = "mzvalues";
  protected static final String XML_INTENSITIES_ELEMENT = "intensities";
  protected static final String XML_SCANS_ELEMENT = "scans";

  private static final Logger logger = Logger.getLogger(SimpleMergedMsMsSpectrum.class.getName());

  protected final float collisionEnergy;
  protected final double precursorMz;
  protected final int precursorCharge;

  public SimpleMergedMsMsSpectrum(@Nullable MemoryMapStorage storage, @NotNull double[] mzValues,
      @NotNull double[] intensityValues, double precursorMz, int precursorCharge,
      float collisionEnergy, int msLevel, @NotNull List<? extends MassSpectrum> sourceSpectra,
      @NotNull MergingType mergingType, @NotNull CenterFunction centerFunction) {
    super(storage, mzValues, intensityValues, msLevel, sourceSpectra, mergingType, centerFunction);

    this.precursorMz = precursorMz;
    this.collisionEnergy = collisionEnergy;
    this.precursorCharge = precursorCharge;
    this.scanDefinition = ScanUtils.scanToString(this, true);
  }

  @Override
  public float getCollisionEnergy() {
    return collisionEnergy;
  }

  @Override
  public double getPrecursorMZ() {
    return precursorMz;
  }

  @Override
  public int getPrecursorCharge() {
    return precursorCharge;
  }

  protected static final String XML_MSLEVEL_ATTR = "mslevel";
  protected static final String XML_CE_ATTR = "ce";
  protected static final String XML_PRECURSOR_MZ_ATTR = "precursormz";
  protected static final String XML_PRECURSOR_CHARGE_ATTR = "precursorcharge";
  protected static final String XML_MERGING_TYPE_ATTR = "mergingtype";

  public static SimpleMergedMsMsSpectrum loadFromXML(XMLStreamReader reader, IMSRawDataFile file)
      throws XMLStreamException {
    final int mslevel = Integer.parseInt(reader.getAttributeValue(null, XML_MSLEVEL_ATTR));
    final float ce = Float.parseFloat(reader.getAttributeValue(null, XML_CE_ATTR));
    final double precursorMz = Double
        .parseDouble(reader.getAttributeValue(null, XML_PRECURSOR_MZ_ATTR));
    final int charge = Integer.parseInt(reader.getAttributeValue(null, XML_PRECURSOR_CHARGE_ATTR));
    final MergingType type = MergingType
        .valueOf(reader.getAttributeValue(null, XML_MERGING_TYPE_ATTR));
    assert file.getName().equals(reader.getAttributeValue(null, CONST.XML_RAW_FILE_ELEMENT));

    double[] mzs = null;
    double[] intensties = null;
    List<MobilityScan> scans = null;
    while (reader.hasNext()) {
      int next = reader.next();
      if (next == XMLEvent.END_ELEMENT && reader.getLocalName().equals(XML_ELEMENT)) {
        break;
      }
      if (next != XMLEvent.START_ELEMENT) {
        continue;
      }
      switch (reader.getLocalName()) {
        case XML_MZS_ELEMENT -> mzs = ParsingUtils.stringToDoubleArray(reader.getElementText());
        case XML_INTENSITIES_ELEMENT -> intensties = ParsingUtils
            .stringToDoubleArray(reader.getElementText());
        case XML_SCANS_ELEMENT -> scans = ParsingUtils
            .stringToMobilityScanList(reader.getElementText(), file);
      }
    }

    assert mzs != null && intensties != null && scans != null;
    return new SimpleMergedMsMsSpectrum(file.getMemoryMapStorage(), mzs, intensties, precursorMz,
        charge, ce, mslevel, scans, type, SpectraMerging.DEFAULT_CENTER_FUNCTION);
  }

  @Override
  public void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
    writer.writeStartElement(XML_ELEMENT);

    writer.writeAttribute(XML_MSLEVEL_ATTR, String.valueOf(getMSLevel()));
    writer.writeAttribute(XML_CE_ATTR, String.valueOf(getCollisionEnergy()));
    writer.writeAttribute(XML_PRECURSOR_MZ_ATTR, String.valueOf(getPrecursorMZ()));
    writer.writeAttribute(XML_PRECURSOR_CHARGE_ATTR, String.valueOf(getPrecursorCharge()));
    writer.writeAttribute(XML_MERGING_TYPE_ATTR, getMergingType().name());
    writer.writeAttribute(CONST.XML_RAW_FILE_ELEMENT, getDataFile().getName());

    writer.writeStartElement(XML_MZS_ELEMENT);
    writer.writeCharacters(ParsingUtils.doubleBufferToString(getMzValues()));
    writer.writeEndElement();

    writer.writeStartElement(XML_INTENSITIES_ELEMENT);
    writer.writeCharacters(ParsingUtils.doubleBufferToString(getIntensityValues()));
    writer.writeEndElement();

    List<MobilityScan> mobilityScans = getSourceSpectra().stream()
        .<MobilityScan>mapMulti((s, c) -> {
          if (s instanceof MobilityScan) {
            c.accept((MobilityScan) s);
          }
        }).toList();

    writer.writeStartElement(XML_SCANS_ELEMENT);
    writer.writeCharacters(ParsingUtils.mobilityScanListToString(mobilityScans));
    writer.writeEndElement();

    writer.writeEndElement();
  }
}
