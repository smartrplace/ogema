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
package org.ogema.impl.apploader;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Dictionary;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import javax.net.ssl.SSLContext;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.ogema.core.administration.CredentialStore;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.useradmin.Group;
import org.osgi.service.useradmin.Role;
import org.osgi.service.useradmin.User;
import org.osgi.service.useradmin.UserAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(specVersion = "1.2", immediate = true)
@Service(CredentialStore.class)
public class CredentialStoreImpl implements CredentialStore {

	private static final String APPSTORE_GROUP_NAME = "appstoreGroup";
	private static final String APPSTORE_PWD_NAME = "appstoreCred";
	private static final String APPSTORE_USER_NAME = "appstoreUsr";
	@Reference
	private UserAdmin userAdmin;
	private final static Logger logger = LoggerFactory.getLogger(CredentialStoreImpl.class);
    
    private final static int PW_ITERATIONS = Integer.getInteger("ogema.apploader.pw_hash_iterations", 100); //very low default for raspi etc.
    private final static int PW_SALT_LEN = 32;
    private final static int PW_KEY_LEN = 256;
    private final static String PW_STORED_PREFIX = "PBKDF2:";
    
    private final static SecureRandom saltPrng;
    
    static {
        try {
            saltPrng = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException ex) {
            logger.error("missing algorithm for PRNG: SHA1PRNG ({})", ex.getMessage());
            throw new RuntimeException("could not initialize salt PRNG", ex);
        }
    }
    
    static String encodePassword(String password, int iterations) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] salt = new byte[PW_SALT_LEN];
        saltPrng.nextBytes(salt);
        String store = PW_STORED_PREFIX + iterations + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + hashPassword(password, salt, iterations);
        return store;
    }
    
    static String hashPassword(String password, byte[] salt, int iterations) throws NoSuchAlgorithmException, InvalidKeySpecException {
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        long start = System.currentTimeMillis();
        SecretKey key = f.generateSecret(new PBEKeySpec(
            password.toCharArray(), salt, iterations, PW_KEY_LEN));
        long time = System.currentTimeMillis() - start;
        logger.debug("password hashed in {}ms, {} iterations", time, iterations);
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
    
    static boolean checkPassword(Dictionary<String, Object> dict, String password, String stored) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (!stored.startsWith(PW_STORED_PREFIX)) {
            logger.debug("encoding clear text password");
            stored = encodePassword(stored, PW_ITERATIONS);
            dict.put(Constants.PASSWORD_NAME, stored);
        }
        String[] a = stored.split(":");
        if (a.length != 4) {
            logger.error("stored password has incorrect format: {}", stored);
            return false;
        }
        String storedHash = a[3];
        try {
            int iterations = Integer.parseInt(a[1]);
            byte[] salt = Base64.getDecoder().decode(a[2]);
            String givenPasswordHash = hashPassword(password, salt, iterations);
            //logger.debug("checking password hash {} against stored hash: {}", givenPasswordHash, storedHash);
            return givenPasswordHash.length() == storedHash.length()
                    ? equalsConstantTime(givenPasswordHash, storedHash)
                    : equalsConstantTimeBase64(givenPasswordHash, storedHash, PW_SALT_LEN);
        } catch (NumberFormatException nfe) {
            logger.error("stored password has incorrect format: {}", stored);
            return false;
        }
    }
    
    static boolean equalsConstantTimeBase64(String a, String b, int len) {
        byte[] bytesA = Base64.getDecoder().decode(a);
        byte[] bytesB = Base64.getDecoder().decode(b);
        logger.debug("{}, {}, {}", len, bytesA.length, bytesB.length);
        if (bytesA.length < len || bytesB.length < len) {
            return false;
        }
        int c = 0;
        for (int i = 0; i < len; i++) {
            c |= bytesA[i] ^ bytesB[i];
        }
        return c == 0;
    }
    
    static boolean equalsConstantTime(String a, String b) {
        byte[] a1 = a.getBytes(StandardCharsets.US_ASCII);
        byte[] a2 = b.getBytes(StandardCharsets.US_ASCII);
        if (a1.length != a2.length) {
            return false;
        }
        int c = 0;
        for (int i = 0; i < a1.length; i++) {
            c |= a1[i] ^ a2[i];
        }
        return c == 0;
    }

    @Override
	public void setGWPassword(String usrName, String oldPwd, final String newPwd) {
		if (!login(usrName, oldPwd))
			throw new SecurityException("Wrong old password!");
		Role role = findRole(usrName);
		final User usr = (User) role;

		if (role == null) {
			throw new IllegalArgumentException("User does not exist: " + usrName);
		}
		else {
			// Set users default password
			Boolean hasCred = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
				@Override
				public Boolean run() {
					@SuppressWarnings("unchecked")
					Dictionary<String, Object> dict = usr.getCredentials();
                    try {
                        dict.put(Constants.PASSWORD_NAME, encodePassword(newPwd, PW_ITERATIONS));
                    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                        logger.error("cannot use hashed passwords, storing in plain text!", ex);
                        dict.put(Constants.PASSWORD_NAME, newPwd);
                    }
					return true;
				}
			});

			if (hasCred) {
				logger.debug("Set new password succeeded.");
			}
			else {
				logger.debug("Set new password failed.");
			}
		}

	}

	@Override
	public boolean createUser(String accountGW, String passwordGW, String accountStore, String passwordStore)
			throws IOException, IllegalArgumentException, RuntimeException {
		Objects.requireNonNull(accountGW);
		Objects.requireNonNull(passwordGW);
		// Check validity of the parameter
		boolean check = true;
		if (accountGW == null || accountGW.equals(""))
			check = false;
		// else if (accountGW != null && !accountGW.equals("") && passwordGW == null)
		// check = false;
		else if (accountStore != null && accountStore.equals("")) // accountstore may be null, that results in creation
																	// of a local user only
			check = false;
		else if (accountStore != null && passwordStore == null)
			check = false;
		if (!check)
			throw new IllegalArgumentException(); // TODO explain why (move to separate cases above?)
        try {
            // create/update gateway user
            setCredential(accountGW, Constants.PASSWORD_NAME, encodePassword(passwordGW, PW_ITERATIONS));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            logger.error("cannot use hashed passwords, storing in plain text!", ex);
            setCredential(accountGW, Constants.PASSWORD_NAME, passwordGW);
        }
		if (accountStore != null)
			addStoreCredentials(accountGW, accountStore, passwordStore);
		return true;
	}
    
    /* non case sensitive get role operation */
    private Role findRole(String username) {
        Role role = userAdmin.getRole(username);
        if (role != null) {
            return role;
        }
        role = userAdmin.getRole(username.toLowerCase());
        if (role != null) {
            return role;
        }
        try {
            Role[] allRoles = userAdmin.getRoles(null);
            for (Role r: allRoles) {
                if (r.getName().equalsIgnoreCase(username)) {
                    return r;
                }
            }
        } catch (InvalidSyntaxException ex) {
            // not likely with filter=null
        }
        return null;
    }

	@Override
	public boolean login(String usrName, final String pwd) {
		Role role = findRole(usrName);
		if (role == null)
			return false;
		final User admin = (User) role;
		Boolean hasCred = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {
            @Override
			public Boolean run() {
                Object o = admin.getCredentials().get(Constants.PASSWORD_NAME);
                if (o == null || pwd == null) {
                    return false;
                }
                @SuppressWarnings("unchecked")
                Dictionary<String, Object> dict = admin.getCredentials();
                try {
                    return checkPassword(dict, pwd, String.valueOf(dict.get(Constants.PASSWORD_NAME)));
                } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
                    logger.error("password check failed", ex);
                    return admin.hasCredential(Constants.PASSWORD_NAME, pwd);
                }
			}
		});
		return hasCred;
	}

	@Override
	public void logout(String usrName) {
	}

	@Override
	public void removeUser(String usrName) {
	}

	private void setCredential(String user, String credential, String value) {
		User usr;
		Role role = findRole(user);
		if (role == null) {
			throw new IllegalArgumentException();
		}
		usr = (User) role;
		// Set users credential
		@SuppressWarnings("unchecked")
		Dictionary<String, Object> dict = usr.getCredentials();
		dict.put(credential, value);
		if (usr.hasCredential(user, credential))
			logger.debug("User credential is set correctly");
	}

	Boolean hasAccess(String user) {
		User thisUser = (User) findRole(user);

		return thisUser.hasCredential(APPSTORE_USER_NAME, user);
	}

	private Group getAppstoreGroup(String groupName) {
		// Get or create group for the appstore
		if (findRole(groupName) == null) {
			return (Group) userAdmin.createRole(groupName, Role.GROUP);
		}
		return (Group) findRole(groupName);
	}

	private String addStoreCredentials(String gwUser, String storeUser, String storePwd) {
		User thisUser = (User) findRole(gwUser);
		Group appstoreAccessRole;

		if (!hasAccess(gwUser)) {

			appstoreAccessRole = getAppstoreGroup(APPSTORE_GROUP_NAME);

			// Set credentials and assign user to role
			setCredential(gwUser, APPSTORE_PWD_NAME, storePwd);
			setCredential(gwUser, APPSTORE_USER_NAME, storeUser);
			appstoreAccessRole.addMember(thisUser);
			return "The user " + gwUser + " was successfully assigned to the appstore with the name " + storeUser;
		}
		else {
			setCredential(gwUser, APPSTORE_PWD_NAME, storePwd);
			return "The password has successfully been changed";
		}
	}

	@Override
	public String getGWId() {
		String clientID = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
			public String run() {
				String id = FrameworkUtil.getBundle(getClass()).getBundleContext()
						.getProperty("org.ogema.secloader.gatewayidentifier");
				return id;
			}
		});
		if (clientID == null) {
			try {
				clientID = "OGEMA-" + InetAddress.getLocalHost().toString();
			} catch (UnknownHostException e) {
				clientID = "OGEMA-" + System.currentTimeMillis();
			}
		}
		return clientID;
	}

	@Override
	public SSLContext getDISSLContext() {
		return null;
	}
}
