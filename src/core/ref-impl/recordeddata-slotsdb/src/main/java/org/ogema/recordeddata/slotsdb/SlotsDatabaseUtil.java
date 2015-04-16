/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3
 * as published by the Free Software Foundation.
 *
 * OGEMA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ogema.recordeddata.slotsdb;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SlotsDatabaseUtil {

	private final static Logger logger = LoggerFactory.getLogger(SlotsDatabaseUtil.class);

	public static void printWholeFile(File file) throws IOException {
		if (!file.getName().contains(SlotsDb.FILE_EXTENSION)) {
			System.err.println(file.getName() + " is not a \"" + SlotsDb.FILE_EXTENSION + "\" file.");
			return;
		}
		else {
			DataInputStream dis = new DataInputStream(new FileInputStream(file));
			try {
				if (file.length() >= 16) {
					logger.debug("StartTimestamp: " + dis.readLong() + "  -  StepIntervall: " + dis.readLong());
					while (dis.available() >= 9) {
						logger.debug(dis.readDouble() + "  -\t  Flag: " + dis.readByte());
					}
				}
			} finally {
				dis.close();
			}
		}
	}

	public static void printWholeFile(String filename) throws IOException {
		printWholeFile(new File(filename));
	}
}
