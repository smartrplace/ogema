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
package org.ogema.resourcemanager.impl.model.units;

import org.ogema.core.model.Resource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.units.EnergyResource;
import org.ogema.core.model.units.PhysicalUnit;
import org.ogema.resourcemanager.impl.ApplicationResourceManager;

import org.ogema.resourcemanager.virtual.VirtualTreeElement;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Timo Fischer, Fraunhofer IWES
 */
public class DefaultEnergyResource extends UnitFloatResource implements EnergyResource {
    
    public static final String UNIT_DECORATOR = "unit";
    private StringResource unitDec;
    PhysicalUnit unit = PhysicalUnit.JOULES;

	public DefaultEnergyResource(VirtualTreeElement el, Class<? extends Resource> unitResourceType, String path,
			ApplicationResourceManager resMan) {
		super(el, unitResourceType, path, resMan);
	}

    private void initUnit() {
        unitDec = getSubResource(UNIT_DECORATOR, StringResource.class);
        if (isActive()) {
            if (unitDec.getValue() != null && !unitDec.getValue().isEmpty()) {
                unit = PhysicalUnit.fromString(unitDec.getValue());
            } else {
                unitDec.create();
                unitDec.setValue(PhysicalUnit.JOULES.toString());
                unitDec.activate(false);
            }
        }
    }

	@Override
	public final PhysicalUnit getUnit() {
        try {
            if (unitDec == null) {
                initUnit();
            }
            if (unitDec.getValue() != null && !unitDec.getValue().isEmpty()) {
                return PhysicalUnit.fromString(unitDec.getValue());
            }
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(DefaultEnergyResource.class)
                    .error("invalid unit value on {}", getPath(), e);
        }
		return PhysicalUnit.JOULES;
	}
    
    @Override
    public final void setUnit(PhysicalUnit u) {
        try {
            if (unitDec == null) {
                initUnit();
            }
            unitDec.create();
            unitDec.setValue(u.toString());
            if (isActive()) {
                unitDec.activate(false);
            }
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(DefaultEnergyResource.class)
                    .error("could not set unit value on {}", getPath(), e);
        }
    }

	@Override
	public float getKWhs() {
		return getValue() / 3600000;
	}

	@Override
	public void setKWhs(float value) {
		setValue(value * 3600000);
	}
}
