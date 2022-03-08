package de.fhg.iee.bacnet.tags;

import de.fhg.iee.bacnet.api.EdeInformation;
import java.util.Collection;

public interface CompositeTagBase extends EdeInformation {
	int getOidInstanceNumber();
	int getOidType();
	<T extends CompositeTagBase> Collection<T> getSubTags();
}
