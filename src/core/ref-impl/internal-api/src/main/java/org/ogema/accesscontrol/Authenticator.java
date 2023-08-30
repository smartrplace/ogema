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
package org.ogema.accesscontrol;

import javax.servlet.http.HttpServletRequest;

import org.ogema.accesscontrol.RestAccess.LoginViaNaturalUserChecker;

/**
 * Register as OSGi service with property {@link #AUTHENTICATOR_ID} set.
 */
public interface Authenticator {

	public static final String AUTHENTICATOR_ID = "authenticator.id";

	/**
	 * Id of the default built-in user/pw based authentication.
	 */
	public static final String DEFAULT_USER_PW_ID = "userpw";

	/**
	 * Id of the built-in basic authentication provider
	 */
	public static final String DEFAULT_BASIC_AUTH = "basicauth";

	/**
	 * Validate the login credentials associated to a servlet request, and
	 * determine the associated user.
	 *
	 * @param req
	 * @return null if the request could not be authenticated, the user name
	 * otherwise
	 */
	String authenticate(HttpServletRequest req);

	@FunctionalInterface
	public interface UserTypeChecker {

		boolean isExpectedUserType(String userName);
	}

	/**
	 * Validate the login credentials associated to a servlet request, and
	 * determine the associated user.
	 *
	 * @param req
	 * @param userTypeChecker if not null the initial result will be tested via
	 * isExpectedUserType. If the caller does not confirm, then also the user
	 * "_rest" is tested
	 * @return null if the request could not be authenticated, the user name
	 * otherwise
	 */
	default String authenticate(HttpServletRequest req, UserTypeChecker userTypeChecker) {
		return authenticate(req);
	}
	
	/**
	 * Callback for logged in sessions, used to confirm that the session is still
	 * valid. The default implementation returns true, in case of false, the session
	 * will be invalidated.
	 * 
	 * @param req current request.
	 * @return false iff the current session should be invalidated.
	 */
	default boolean sessionStillValid(HttpServletRequest req) {
		return true;
	}
	
}
