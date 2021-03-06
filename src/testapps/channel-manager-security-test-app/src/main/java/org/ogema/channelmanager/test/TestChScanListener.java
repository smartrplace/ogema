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
package org.ogema.channelmanager.test;

import java.util.LinkedList;
import java.util.List;

import org.ogema.core.channelmanager.driverspi.ChannelLocator;
import org.ogema.core.channelmanager.driverspi.ChannelScanListener;

class TestChScanListener implements ChannelScanListener {

	public boolean finished = false;
	public boolean success = false;
	public float progress = 0.f;
	public List<ChannelLocator> foundChannels = null;

	@Override
	public void channelFound(ChannelLocator channel) {
		if (foundChannels == null)
			foundChannels = new LinkedList<ChannelLocator>();

		foundChannels.add(channel);
	}

	@Override
	public void finished(boolean success) {
		finished = true;
		this.success = success;
	}

	@Override
	public void progress(float ratio) {
		progress = ratio;
	}

}
