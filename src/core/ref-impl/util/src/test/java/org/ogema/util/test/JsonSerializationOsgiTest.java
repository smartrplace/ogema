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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.ogema.core.channelmanager.measurements.FloatValue;
import org.ogema.core.channelmanager.measurements.Quality;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.array.ByteArrayResource;
import org.ogema.core.model.array.StringArrayResource;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.StringResource;
import org.ogema.core.model.units.PowerResource;
import org.ogema.core.resourcemanager.ResourceManagement;
import org.ogema.core.tools.SerializationManager;
import org.ogema.exam.OsgiAppTestBase;
import org.ogema.exam.ResourceAssertions;
import org.ogema.model.actors.OnOffSwitch;
import org.ogema.model.connections.ThermalMixingConnection;
import org.ogema.model.prototypes.Configuration;
import org.ogema.model.prototypes.PhysicalElement;
import org.ogema.model.sensors.TemperatureSensor;
import org.ogema.tools.impl.JsonReaderJackson;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

/**
 * OSGi/OGEMA integrated tests.
 *
 * @author jlapp
 */
@ExamReactorStrategy(PerClass.class)
public class JsonSerializationOsgiTest extends OsgiAppTestBase {

	SerializationManager sman;
	ResourceManagement resman;

	@Before
	public void setup() {
		sman = getApplicationManager().getSerializationManager();
		resman = getApplicationManager().getResourceManagement();
	}

	@Test
	public void arraysOfStringWork() throws IOException {
		Resource arrays = resman.createResource(newResourceName(), Resource.class);

		StringArrayResource strings = arrays.addDecorator("arr", StringArrayResource.class);
		String[] serializedValues = new String[]{"a", "b", "c"};
		strings.setValues(serializedValues);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.writeJson(output, arrays);
		System.out.println(output);

		strings.setValues(new String[]{"x", "y", "z"});
		Assert.assertFalse(Arrays.asList(serializedValues).equals(Arrays.asList(strings.getValues())));
		sman.applyJson(output.toString(), arrays, true);
		Assert.assertTrue(Arrays.asList(serializedValues).equals(Arrays.asList(strings.getValues())));
	}

	@Test
	public void arraysOfByteWork() throws IOException {
		Resource arrays = resman.createResource(newResourceName(), Resource.class);

		ByteArrayResource bytes = arrays.addDecorator("arr", ByteArrayResource.class);
		byte[] serializedValues = "test".getBytes(StandardCharsets.UTF_8);
		bytes.setValues(serializedValues);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.writeJson(output, arrays);
		System.out.println(output);

		bytes.setValues("fnord".getBytes(StandardCharsets.UTF_8));
		Assert.assertFalse(Arrays.equals(serializedValues, "fnord".getBytes(StandardCharsets.UTF_8)));
		sman.applyJson(output.toString(), arrays, true);
		System.out.println(new String(bytes.getValues(), StandardCharsets.UTF_8));
		Assert.assertTrue(Arrays.equals(serializedValues, bytes.getValues()));
	}

	@Test
	public void floatScheduleWorks() throws IOException {
		PowerResource pow = resman.createResource(newResourceName(), PowerResource.class);
		Schedule s = pow.program().create();
		Collection<SampledValue> values = Arrays.asList(
				new SampledValue(new FloatValue(0), 0, Quality.GOOD),
				new SampledValue(new FloatValue(1), 1, Quality.GOOD),
				new SampledValue(new FloatValue(4), 2, Quality.GOOD),
				new SampledValue(new FloatValue(9), 3, Quality.GOOD));
		s.addValues(values);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.setSerializeSchedules(true);
		sman.writeJson(output, pow);
		//System.out.println(output);

		Assert.assertEquals(values, s.getValues(0));
		s.addValue(3, new FloatValue(7));
		Assert.assertNotEquals(values, s.getValues(0));

		sman.applyJson(output.toString(), pow, true);
		Assert.assertEquals(values, s.getValues(0));
	}

	/* test de-/serialization of float NaN and Infinity values */
	@Test
	public void floatScheduleWithSpecialValuesWork() throws IOException {
		PowerResource pow = resman.createResource(newResourceName(), PowerResource.class);
		Schedule s = pow.program().create();
		Collection<SampledValue> values = Arrays.asList(
				new SampledValue(new FloatValue(Float.NaN), 0, Quality.GOOD),
				new SampledValue(new FloatValue(Float.POSITIVE_INFINITY), 1, Quality.GOOD),
				new SampledValue(new FloatValue(Float.NEGATIVE_INFINITY), 2, Quality.GOOD));
		s.addValues(values);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.setSerializeSchedules(true);
		sman.writeJson(output, pow);
		//System.out.println(output);

		Assert.assertEquals(values, s.getValues(0));
		s.addValue(3, new FloatValue(7));
		Assert.assertNotEquals(values, s.getValues(0));
		s.deleteValues();
		Assert.assertTrue(s.getValues(0).isEmpty());

		sman.applyJson(output.toString(), pow, true);
		Assert.assertEquals(3, s.getValues(0).size());
		Assert.assertTrue(Float.isNaN(s.getValue(0).getValue().getFloatValue()));
		Assert.assertTrue(Float.isInfinite(s.getValue(1).getValue().getFloatValue()));
		Assert.assertTrue(0 < s.getValue(1).getValue().getFloatValue());
		Assert.assertTrue(Float.isInfinite(s.getValue(2).getValue().getFloatValue()));
		Assert.assertTrue(0 > s.getValue(2).getValue().getFloatValue());
	}

	@Test
	public void floatNaNWorks() throws IOException {
		FloatResource f = resman.createResource(newResourceName(), FloatResource.class);
		f.setValue(Float.NaN);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.setSerializeSchedules(true);
		sman.writeJson(output, f);
		System.out.println(output);

		Assert.assertTrue(Float.isNaN(f.getValue()));
		f.setValue(0);
		Assert.assertFalse(Float.isNaN(f.getValue()));

		sman.applyJson(output.toString(), f, true);
		Assert.assertTrue(Float.isNaN(f.getValue()));
	}

	@Test
	public void floatInfinityWorks() throws IOException {
		FloatResource f = resman.createResource(newResourceName(), FloatResource.class);
		f.setValue(Float.POSITIVE_INFINITY);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.setSerializeSchedules(true);
		sman.writeJson(output, f);
		System.out.println(output);

		Assert.assertTrue(Float.isInfinite(f.getValue()));
		f.setValue(0);
		Assert.assertFalse(Float.isInfinite(f.getValue()));

		sman.applyJson(output.toString(), f, true);
		Assert.assertTrue(Float.isInfinite(f.getValue()));
		Assert.assertTrue(0 < f.getValue());
	}

	@Test
	public void floatNegativeInfinityWorks() throws IOException {
		FloatResource f = resman.createResource(newResourceName(), FloatResource.class);
		f.setValue(Float.NEGATIVE_INFINITY);

		StringWriter output = new StringWriter();
		sman.setMaxDepth(100);
		sman.setSerializeSchedules(true);
		sman.writeJson(output, f);
		System.out.println(output);

		Assert.assertTrue(Float.isInfinite(f.getValue()));
		f.setValue(0);
		Assert.assertFalse(Float.isInfinite(f.getValue()));

		sman.applyJson(output.toString(), f, true);
		Assert.assertTrue(Float.isInfinite(f.getValue()));
		Assert.assertTrue(0 > f.getValue());
	}

	//@Ignore
	@Test
	public void listOrderIsPreserved() throws IOException {
		@SuppressWarnings("unchecked")
		ResourceList<StringResource> rl = resman.createResource(newResourceName(), ResourceList.class);
		rl.setElementType(StringResource.class);
		String name = rl.getName();
		List<String> elementOrder = new ArrayList<>();
		int len = 10;
		for (int i = 0; i < len; i++) {
			StringResource s = rl.getSubResource("_" + UUID.randomUUID().toString().replace('-', '_'), StringResource.class);
			s.create();
			elementOrder.add(s.getName());
			System.out.println(s.getName());
		}
		StringWriter output = new StringWriter();
		sman.writeJson(output, rl);
		rl.delete();
		Assert.assertNull(getApplicationManager().getResourceAccess().getResource(name));
		System.out.println(output.toString());
		sman.createFromJson(output.toString());
		ResourceList<StringResource> l2 = getApplicationManager().getResourceAccess().getResource(name);
		Assert.assertNotNull(l2);
		Assert.assertEquals(StringResource.class, l2.getElementType());
		assertEquals(elementOrder.size(), l2.getAllElements().size());
		for (int i = 0; i < elementOrder.size(); i++) {
			assertEquals(elementOrder.get(i), l2.getAllElements().get(i).getName());
		}
	}

	@Test
	public void collectionsWork() throws IOException {
		PowerResource pow = resman.createResource(newResourceName(), PowerResource.class);
		OnOffSwitch sw = resman.createResource(newResourceName(), OnOffSwitch.class);
		pow.setValue(42);
		sw.stateControl().create();
		sw.stateControl().setValue(true);

		StringWriter output = new StringWriter();
		sman.writeJson(output, Arrays.asList(pow, sw));
		System.out.println(output);

		pow.setValue(0);
		pow.delete();
		sw.delete();
		ResourceAssertions.assertDeleted(pow);
		ResourceAssertions.assertDeleted(sw);

		sman.createResourcesFromJson(output.toString());

		ResourceAssertions.assertExists(pow);
		ResourceAssertions.assertExists(sw);
		assertEquals(42, pow.getValue(), 0);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void resourceListRoundtrip() throws Exception {
		ResourceList<?> l = resman.createResource(newResourceName(), ResourceList.class);
		l.setElementType(PhysicalElement.class);
		OnOffSwitch sw1 = l.getSubResource("sw1", OnOffSwitch.class).create();
		OnOffSwitch sw2 = l.getSubResource("sw2", OnOffSwitch.class).create();
		Resource testDecorator = l.getSubResource("_decorator_", Configuration.class).create();
		Resource testDecorator2 = l.getSubResource("_decorator2_", ThermalMixingConnection.class).create();

		StringWriter output = new StringWriter();
		sman.writeJson(output, l);
		System.out.println(output.toString());

		Resource outputRes = resman.createResource(newResourceName(), Resource.class);

		ResourceList<?> restored = sman.createFromJson(output.toString(), outputRes);

		Assert.assertTrue("Not a ResourceList: " + restored,
				restored instanceof ResourceList);
		Assert.assertNotNull("wp1 missing", restored.getSubResource(sw1.getName()));
		Assert.assertNotNull("wp2 missing", restored.getSubResource(sw2.getName()));
		Assert.assertNotNull("decorator 1 missing", restored.getSubResource(testDecorator.getName()));
		Assert.assertNotNull("decorator 2 missing", restored.getSubResource(testDecorator2.getName()));
	}

	@Test
	public void lastUpdateTimeIsSerializedForUnsetNonPersistent() throws IOException {
		TemperatureSensor ts = getApplicationManager().getResourceManagement().createResource(newResourceName(), TemperatureSensor.class);
		ts.reading().create();
		ts.activate(true);
		//ts.reading().setValue(47.11f);
		long time = ts.reading().getLastUpdateTime();
		assertEquals(-1, time);

		StringWriter output = new StringWriter();

		sman.writeJson(output, ts);
		System.out.println("Json:\n" + output.toString());
		TemperatureSensor tsDeserialized = sman.createFromJson(output.toString(),
				ts.getSubResource(newResourceName(), Resource.class).create());
		Assert.assertFalse(ts.equalsLocation(tsDeserialized));
		Assert.assertEquals(time, tsDeserialized.reading().getLastUpdateTime());
	}
	
	@Test
	public void lastUpdateTimeIsSerialized() throws IOException, ClassNotFoundException {
		TemperatureSensor ts = getApplicationManager().getResourceManagement().createResource(newResourceName(), TemperatureSensor.class);
		ts.reading().create();
		ts.activate(true);
		ts.reading().setValue(47.11f);
		long time = ts.reading().getLastUpdateTime();

		StringWriter output = new StringWriter();
		sman.writeJson(output, ts);
		System.out.println("Json:\n" + output.toString());
		
		JsonReaderJackson reader = new JsonReaderJackson();
		org.ogema.serialization.jaxb.Resource res = reader.read(new StringReader(output.toString()));
		org.ogema.serialization.jaxb.FloatResource fres = (org.ogema.serialization.jaxb.FloatResource) res.get("reading");
		assertEquals(time, fres.getLastUpdateTime());
		/*
		// this does not work - lastUpdateTime can not be set
		TemperatureSensor tsDeserialized = sman.createFromJson(output.toString(),
				ts.getSubResource(newResourceName(), Resource.class).create());
		Assert.assertFalse(ts.equalsLocation(tsDeserialized));
		Assert.assertEquals(time, tsDeserialized.reading().getLastUpdateTime());
		*/
	}

}
