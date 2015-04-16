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
package org.ogema.driver.knxdriver;

import java.util.List;
import java.util.Map;

import org.ogema.core.application.Application;

public interface KNXdriverI extends Application {

	public int addConnection(final String connectionString);

	public boolean disconnectResource(String groupInput);

	public List<ConnectionInfo> getConnectionSorted();

	public void searchInterface();

	public Map<String, String> getInterfaces();

}
