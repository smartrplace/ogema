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
package org.ogema.resourcemanager.impl.test;

import org.junit.Assert;
import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.model.sensors.TemperatureSensor;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * Tests value update timestamp when using the {@code setValue(val, timestamp) }
 * methods.
 * 
 * @author jlapp
 */
@ExamReactorStrategy(PerClass.class)
public class UpdateTimeTest extends OsgiTestBase {

	@Test
	public void lastUpdateTimeWorks() throws Exception {
		TemperatureSensor res = resMan.createResource(newResourceName(), TemperatureSensor.class);
		res.reading().create();
		res.activate(true);
		long testTs = getApplicationManager().getFrameworkTime() - 5000;
		assertEquals(-1, res.reading().getLastUpdateTime());
		res.reading().setValue(47.11f, testTs);
		assertEquals(testTs, res.reading().getLastUpdateTime());
		
		RecordedDataConfiguration c = new RecordedDataConfiguration();
		c.setStorageType(RecordedDataConfiguration.StorageType.ON_VALUE_UPDATE);
		res.reading().getHistoricalData().setConfiguration(c);
		
		testTs = getApplicationManager().getFrameworkTime() - 1000;
		res.reading().setValue(12.34f, testTs);
		
		SampledValue sv = res.reading().getHistoricalData().getPreviousValue(Long.MAX_VALUE);
		Assert.assertNotNull(sv);
		assertEquals(testTs, sv.getTimestamp());
	}
    
}
