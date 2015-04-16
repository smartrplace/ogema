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
package org.ogema.core.hardwaremanager;

/**
 * The {@link HardwareDescriptor} subclass for Serial Ports (UARTs). This class is intended for UARTs of the controller
 * that are not connected via USB.
 */
public interface SerialHardwareDescriptor extends HardwareDescriptor {

	/**
	 * Returns the port name (COMx) or device node (/dev/ttyx) of the device.
	 */
	String getPortName();
}
