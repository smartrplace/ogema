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
//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.4-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.09.13 at 08:01:54 AM CEST 
//
package org.ogema.serialization.jaxb;

import static org.ogema.serialization.JaxbResource.NS_OGEMA_REST;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 *
 * Common basis class for a resource. All resources are either primitive
 * (FloatResource, BooleanResource, ...) or non-primitive (type Resource) or
 * schedules (FloatSchedule, ...). This defines common entries that all
 * resources can have. The same xml structures are used for PUT/POST and GET
 * commands. Therefore, few of the fields are defined as required, allowing
 * PUT/POST commands to be reduced to the minimum amount of information. For GET
 * requests, however, OGEMA should send as complete documents as possible.
 *
 *
 * <p>
 * Java class for Resource complex type.
 *
 * <p>
 * The following schema fragment specifies the expected content contained within
 * this class.
 *
 * &lt;pre&gt;
 * &lt;complexType name="Resources"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;group ref="{http://www.ogema-source.net/REST}CommonResourceElements"/&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * &lt;/pre&gt;
 *
 *
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Resources", namespace = NS_OGEMA_REST)
@XmlSeeAlso( { Resource.class, OpaqueResource.class, FloatResource.class, IntegerResource.class, StringResource.class,
		BooleanResource.class, ScheduleResource.class, TimeResource.class, BooleanSchedule.class, FloatSchedule.class,
		IntegerSchedule.class, StringSchedule.class, TimeSchedule.class, ResourceList.class,
		BooleanArrayResource.class, ByteArrayResource.class, FloatArrayResource.class, IntegerArrayResource.class,
		StringArrayResource.class, TimeArrayResource.class })
@XmlRootElement(name = "resources", namespace = NS_OGEMA_REST)
@JsonSubTypes( { @JsonSubTypes.Type(Resource.class),
		@JsonSubTypes.Type(BooleanResource.class), @JsonSubTypes.Type(FloatResource.class),
		@JsonSubTypes.Type(IntegerResource.class), @JsonSubTypes.Type(OpaqueResource.class),
		@JsonSubTypes.Type(StringResource.class), @JsonSubTypes.Type(TimeResource.class),
		@JsonSubTypes.Type(ScheduleResource.class), @JsonSubTypes.Type(FloatSchedule.class),
		@JsonSubTypes.Type(IntegerSchedule.class), @JsonSubTypes.Type(TimeSchedule.class),
		@JsonSubTypes.Type(StringSchedule.class), @JsonSubTypes.Type(BooleanSchedule.class),
		@JsonSubTypes.Type(ResourceList.class), @JsonSubTypes.Type(BooleanArrayResource.class),
		@JsonSubTypes.Type(ByteArrayResource.class), @JsonSubTypes.Type(FloatArrayResource.class),
		@JsonSubTypes.Type(IntegerArrayResource.class), @JsonSubTypes.Type(StringArrayResource.class),
		@JsonSubTypes.Type(TimeArrayResource.class) })
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
public class ResourceCollection {

	@XmlElements( { @XmlElement(name = "resource", type = Resource.class),
			@XmlElement(name = "resourcelink", type = ResourceLink.class) })
	protected List<Object> subresources;

	/**
	 * Gets the value of the subresources property.
	 *
	 * <p>
	 * This accessor method returns a reference to the live list, not a
	 * snapshot. Therefore any modification you make to the returned list will
	 * be present inside the JAXB object. This is why there is not a
	 * <CODE>set</CODE> method for the subresources property.
	 *
	 * <p>
	 * For example, to add a new item, do as follows:
	 *
	 * <pre>
	 * getSubresources().add(newItem);
	 * </pre>
	 *
	 *
	 * <p>
	 * Objects of the following type(s) are allowed in the list {@link ResourceCollection } {@link ResourceLink
	 * }
	 *
	 *
	 */
	public List<Object> getSubresources() {
		if (subresources == null) {
			subresources = new ArrayList<>();
		}
		return this.subresources;
	}

	public Resource get(String subresource) {
		for (Object o : getSubresources()) {
			if (o instanceof Resource) {
				if (((Resource) o).getName().equals(subresource)) {
					return (Resource) o;
				}
			}
		}
		return null;
	}

}
