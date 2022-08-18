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
package org.ogema.util.test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import org.json.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.ogema.core.model.Resource;
import org.ogema.core.model.schedule.AbsoluteSchedule;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.tools.SerializationManager;
import org.ogema.exam.OsgiAppTestBase;
import org.ogema.model.devices.buildingtechnology.AirConditioner;
import org.ogema.model.devices.whitegoods.CoolingDevice;
import org.ogema.model.metering.ElectricityMeter;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

@ExamReactorStrategy(PerClass.class)
public class CollectionsTest extends OsgiAppTestBase {

	static int counter = 0;

	ElectricityMeter meter;
	Schedule sched;
	SerializationManager sman;
	
	CoolingDevice fridge;
	List<Resource> resources;

	@ProbeBuilder
	public TestProbeBuilder build(TestProbeBuilder builder) {
		builder.setHeader("DynamicImport-Package", "*");
		return builder;
	}

	@Before
	public void setup() throws Exception {
		meter = getApplicationManager().getResourceManagement().createResource("meter" + counter++,
				ElectricityMeter.class);
		meter.connection().powerSensor().reading().create();
		sched = meter.connection().powerSensor().reading().addDecorator("sched", AbsoluteSchedule.class);
		meter.connection().powerSensor().reading().setValue(47.11f);
		fridge = getApplicationManager().getResourceManagement().createResource("fridge" + counter++, CoolingDevice.class);
		fridge.name().<StringResource> create().setValue("Fridge");
		sman = getApplicationManager().getSerializationManager();
		resources = new ArrayList<>();
		resources.add(meter);
		resources.add(fridge);
	}
	
	@Test
	public void xmlSerializationWorks() throws Exception {
		String xml = sman.toXml(resources);
		System.out.println("   ~~ xml: " + xml);
		Assert.assertNotNull(xml);
		Collection<Resource> list = sman.createResourcesFromXml(xml);
		// FIXME
		System.out.println("   ~~~ resources created: " + list);
		assertEquals(2, list.size());
	}
	
	@Test
	public void jsonSerializationWorks() throws Exception {
		String json = sman.toJson(resources);
		// System.out.println("   ~~~ json: " + json);
		Assert.assertNotNull(json);
		JSONArray obj = new JSONArray(json);
		assertEquals(2, obj.length());

		Collection<Resource> list = sman.createResourcesFromJson(json); 
		// FIXME
		System.out.println("   ~~~ resources created: " + list);
		assertEquals(2,	list.size());
	}
	
	@Test
	public void collectionRoundTripPreservesLinks() throws Exception {
		String ac1name = newResourceName();
		String ac2name = newResourceName();
		AirConditioner ac1 = getApplicationManager().getResourceManagement().createResource(ac1name, AirConditioner.class);
		AirConditioner ac2 = getApplicationManager().getResourceManagement().createResource(ac2name, AirConditioner.class);
		ac1.onOffSwitch().stateControl().create();
		ac1.onOffSwitch().stateControl().setValue(true);
		
		ac2.onOffSwitch().setAsReference(ac1.onOffSwitch());
		SerializationManager sm = getApplicationManager().getSerializationManager(Integer.MAX_VALUE, false, true);
		String xml = sm.toXml(Arrays.<Resource>asList(ac1, ac2));
		
		Assert.assertTrue(ac1.onOffSwitch().equalsLocation(ac2.onOffSwitch()));
		
		ac1.delete();
		ac2.delete();
		
		Assert.assertFalse(ac1.onOffSwitch().equalsLocation(ac2.onOffSwitch()));
		
		sm.createResourcesFromXml(xml);
		
		ac1 = getApplicationManager().getResourceAccess().getResource(ac1name);
		ac2 = getApplicationManager().getResourceAccess().getResource(ac2name);
		
		Assert.assertTrue(ac1.onOffSwitch().equalsLocation(ac2.onOffSwitch()));
		Assert.assertTrue(ac1.onOffSwitch().stateControl().getValue());
	}
	
	@Test
	public void collectionDeserialisationToSubresourceWithRelativeLinks() throws Exception {
		String ac1name = newResourceName();
		String ac2name = newResourceName();
		AirConditioner ac1 = getApplicationManager().getResourceManagement().createResource(ac1name, AirConditioner.class);
		AirConditioner ac2 = getApplicationManager().getResourceManagement().createResource(ac2name, AirConditioner.class);
		ac1.onOffSwitch().stateControl().create();
		ac1.onOffSwitch().stateControl().setValue(true);
		
		ac2.onOffSwitch().setAsReference(ac1.onOffSwitch());
		SerializationManager sm = getApplicationManager().getSerializationManager(Integer.MAX_VALUE, false, true);
		String xml = sm.toXml(Arrays.<Resource>asList(ac1, ac2));
		
		Assert.assertTrue(ac1.onOffSwitch().equalsLocation(ac2.onOffSwitch()));
		
		Resource newParent = getApplicationManager().getResourceManagement().createResource(newResourceName()+"_CollectionTarget", Resource.class);
		sm.createResourcesFromXml(new StringReader(xml), newParent, newParent);
		
		AirConditioner ac1repl = newParent.getSubResource(ac1name);
		AirConditioner ac2repl = newParent.getSubResource(ac2name);
		
		Assert.assertTrue(ac1repl.onOffSwitch().equalsLocation(ac2repl.onOffSwitch()));
		Assert.assertFalse(ac1.onOffSwitch().equalsLocation(ac1repl.onOffSwitch()));
		Assert.assertFalse(ac2.onOffSwitch().equalsLocation(ac2repl.onOffSwitch()));
		Assert.assertTrue(ac1repl.onOffSwitch().stateControl().getValue());
	}
	
	@Test
	public void collectionDeserialisationToSubresourcePreservesGlobalLinks() throws Exception {
		String ac1name = newResourceName();
		String ac2name = newResourceName();
		AirConditioner ac1 = getApplicationManager().getResourceManagement().createResource(ac1name, AirConditioner.class);
		
		Resource dir = getApplicationManager().getResourceManagement().createResource(newResourceName() + "_ACs", AirConditioner.class);
		AirConditioner ac2 = dir.getSubResource(ac2name, AirConditioner.class);
		ac1.onOffSwitch().stateControl().create();
		ac1.onOffSwitch().stateControl().setValue(true);
		
		ac2.onOffSwitch().setAsReference(ac1.onOffSwitch());
		SerializationManager sm = getApplicationManager().getSerializationManager(Integer.MAX_VALUE, false, true);
		String xml = sm.toXml(Arrays.<Resource>asList(ac1, ac2));
		
		Assert.assertTrue(ac1.onOffSwitch().equalsLocation(ac2.onOffSwitch()));
		
		Resource newParent = getApplicationManager().getResourceManagement().createResource(newResourceName()+"_CollectionTarget", Resource.class);
		sm.createResourcesFromXml(new StringReader(xml), newParent);
		
		AirConditioner ac2repl = newParent.getSubResource(ac2name);

		//System.out.println(ac2repl.onOffSwitch().getPath());
		//System.out.println(ac2repl.onOffSwitch().getLocation());
		Assert.assertTrue(ac1.onOffSwitch().equalsLocation(ac2repl.onOffSwitch()));
	}

}
