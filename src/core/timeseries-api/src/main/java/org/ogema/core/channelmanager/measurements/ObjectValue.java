/**
 * Copyright 2011-2018 Fraunhofer-Gesellschaft zur Förderung der angewandten Wissenschaften e.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * This file is part of OGEMA.
 *
 * OGEMA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * OGEMA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * OGEMA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ogema.core.channelmanager.measurements;

import java.lang.reflect.InvocationTargetException;

import org.ogema.core.timeseries.ReadOnlyTimeSeries;

/**
 *
 * Object{@link Value}
 *
 */
public class ObjectValue implements Value {

	private Object value;

	public ObjectValue(Object value) {
		this.value = value;
	}

	@Override
	public float getFloatValue() throws IllegalConversionException {
		if (value instanceof Number)
			return ((Number) value).floatValue();
		throw new IllegalConversionException("Cannot convert a generic object to a float");
	}

	@Override
	public double getDoubleValue() throws IllegalConversionException {
		if (value instanceof Number)
			return ((Number) value).doubleValue();
		throw new IllegalConversionException("Cannot convert a generic object to a double");
	}

	@Override
	public int getIntegerValue() throws IllegalConversionException {
		if (value instanceof Number)
			return ((Number) value).intValue();
		throw new IllegalConversionException("Cannot convert a generic object to an integer");
	}

	@Override
	public long getLongValue() throws IllegalConversionException {
		if (value instanceof Number)
			return ((Number) value).longValue();
		throw new IllegalConversionException("Cannot convert a generic object to a long");
	}

	@Override
	public String getStringValue() throws IllegalConversionException {
		return value.toString();
	}

	@Override
	public byte[] getByteArrayValue() throws IllegalConversionException {
		if (value instanceof byte[])
			return (byte[]) value;
		throw new IllegalConversionException("Cannot convert a generic object to a byte array.");
	}

	@Override
	public boolean getBooleanValue() throws IllegalConversionException {
		if (value instanceof Boolean)
			return (Boolean) value;
		throw new IllegalConversionException("Cannot convert a generic object to a boolean.");
	}

	@Override
	public Object getObjectValue() {
		return value;
	}

        @Override
        public ObjectValue clone() throws CloneNotSupportedException {
                if (value instanceof Cloneable) {
                        ObjectValue v = (ObjectValue) super.clone();
                        try {
                                v.value = value.getClass().getMethod("clone").invoke(value);
                                return v;
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                                | NoSuchMethodException | SecurityException | ClassCastException e) {
                            throw new CloneNotSupportedException("Clone not supported " + e);
                        }
                }
                throw new CloneNotSupportedException(
                        "Value of ObjectValue is not cloneable.");
        }

	@Override
	public String toString() {
		return "ObjectValue[" + value + "]";
	}
	
	@Override
	public ReadOnlyTimeSeries getTimeSeriesValue() throws IllegalConversionException {
		if (value instanceof ReadOnlyTimeSeries)
			return (ReadOnlyTimeSeries) value;
		throw new IllegalConversionException("Cannot convert a generic object to a time series.");
	}
}
