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
package org.ogema.frameworkadministration.json;

import java.io.Serializable;

/**
 *
 * @author tgries
 */
public class AppsJsonAppConditions implements Serializable {

	private static final long serialVersionUID = 2460841383020766499L;

	private String type;
	private String arg1;
	private String arg2;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getArg1() {
		return arg1;
	}

	public void setArg1(String arg1) {
		this.arg1 = arg1;
	}

	public String getArg2() {
		return arg2;
	}

	public void setArg2(String arg2) {
		this.arg2 = arg2;
	}

}
