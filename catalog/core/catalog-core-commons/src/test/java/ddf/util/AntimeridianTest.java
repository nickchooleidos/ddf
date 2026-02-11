/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.util.List;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public class AntimeridianTest {

  private static final WKTReader WKT_READER = new WKTReader();

  private Geometry getBoundaryFromWkt(String wkt) {
    try {
      Geometry geo = WKT_READER.read(wkt);
      return geo.getBoundary();
    } catch (ParseException e) {
      throw new IllegalArgumentException("Unable to parse WKT", e);
    }
  }

  @Test
  public void testWktMultipolyToSinglePolygons() {
    String inputWkt =
        "MULTIPOLYGON (((-90 26, -90 70, -44 70, -44 26, -90 26)), ((180 70, 180 26, 162 26, 162 70, 180 70)))";
    String expectedA = "POLYGON ((-90 26, -90 70, -44 70, -44 26, -90 26))";
    String expectedB = "POLYGON ((180 70, 180 26, 162 26, 162 70, 180 70))";

    List<String> resultWkt = Antimeridian.wktMultipolyToSinglePolygons(inputWkt);
    assertThat(resultWkt, hasItem(expectedA));
    assertThat(resultWkt, hasItem(expectedB));
  }

  @Test
  public void testValidCoordinatesAreNotChanged() {
    String inputWkt = "POLYGON ((170 33, 179 44, 101 55, 111 44))";
    String resultWkt = Antimeridian.normalizeWkt(inputWkt);
    assertThat(resultWkt, is(inputWkt));
  }

  @Test
  public void testInvalidCoordinatesAreNormalized() {
    String originalWkt =
        "POLYGON ((182.421875 68.656555, 226.757813 69.037142, 226.757813 26.431228, 194.6875 27.994401, 182.421875 68.656555))";
    String expectedWkt =
        "POLYGON ((-177.578125 68.656555, -133.242187 69.037142, -133.242187 26.431228, -165.3125 27.994401, -177.578125 68.656555))";
    String actualWkt = Antimeridian.normalizeWkt(originalWkt);
    assertThat(actualWkt, is(expectedWkt));
  }

  @Test
  public void testAntimeridianCrossingIsSplit() {
    String originalWkt = "POLYGON ((162 70, 226 70, 226 26, 162 26, 162 70))";
    String expectedWkt =
        "MULTIPOLYGON (((-180 26, -180 70, -134 70, -134 26, -180 26)), ((180 70, 180 26, 162 26, 162 70, 180 70)))";
    String actualWkt = Antimeridian.unwrapAndSplitWkt(originalWkt);
    assertThat(actualWkt, is(expectedWkt));
    Geometry originalBounds = getBoundaryFromWkt(originalWkt);
    Geometry updatedBounds = getBoundaryFromWkt(actualWkt);
    assertThat(originalBounds.getArea(), is(updatedBounds.getArea()));
  }

  @Test
  public void testNonCrossingShapesAreNotSplit() {
    String inputWkt =
        "POLYGON ((-98.085938 42.55308, -113.90625 35.173808, -90 34.307144, -98.085938 42.55308))";
    String outputWkt = Antimeridian.unwrapAndSplitWkt(inputWkt);
    assertThat(outputWkt, is(inputWkt));
  }

  @Test
  public void testAntimeridianCrossingIsSplitWithNegativeLongtitude() {
    String originalWkt =
        "POLYGON ((167.422137 -37.935528, -173.149957 -49.494589, -166.265735 -32.563561, -176.051446 -24.815088, 167.422137 -37.935528))";
    String expectedWkt =
        "MULTIPOLYGON (((180 -27.949873104584977, 167.422137 -37.935528, 180 -45.419004894866745, 180 -27.949873104584977)), ((-180 -45.419004894866745, -173.149957 -49.494589, -166.265735 -32.563561, -176.051446 -24.815088, -180 -27.949873104584977, -180 -45.419004894866745)))";
    String actualWkt = Antimeridian.unwrapAndSplitWkt(originalWkt);
    assertWktTopologicallyEqual(actualWkt, expectedWkt);
    assertCoordinatesWithinBounds(actualWkt);
    Geometry originalBounds = getBoundaryFromWkt(originalWkt);
    Geometry updatedBounds = getBoundaryFromWkt(actualWkt);
    assertThat(originalBounds.getArea(), is(updatedBounds.getArea()));
  }

  @Test(expected = ParseException.class)
  public void testInvalidWktFormatFailsToParse() throws ParseException {
    String invalidWkt =
        "MULTIPOLYGON (((180 10, 170 10, 170 -10, 180 -10, 180 10)), ((-180 -10, -170 -10, -170, 10, -180 10, -180 -10)))";
    WKT_READER.read(invalidWkt);
  }

  private void assertCoordinatesWithinBounds(String wkt) {
    Geometry geometry;
    try {
      geometry = WKT_READER.read(wkt);
    } catch (ParseException e) {
      throw new IllegalArgumentException("Unable to parse WKT", e);
    }
    for (Coordinate coordinate : geometry.getCoordinates()) {
      assertThat(coordinate.x >= -180.0 && coordinate.x <= 180.0, is(true));
      assertThat(coordinate.y >= -90.0 && coordinate.y <= 90.0, is(true));
    }
  }

  private void assertWktTopologicallyEqual(String actualWkt, String expectedWkt) {
    try {
      Geometry actual = WKT_READER.read(actualWkt);
      Geometry expected = WKT_READER.read(expectedWkt);
      assertThat(actual.equalsTopo(expected), is(true));
    } catch (ParseException e) {
      throw new IllegalArgumentException("Unable to parse WKT", e);
    }
  }
}
