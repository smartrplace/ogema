package org.ogema.impl.apploader;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jlapp
 */
public class CredentialStoreImplTest {
    
    public CredentialStoreImplTest() {
    }

    @Test
    public void testEncodePassword() throws Exception {
        String password = UUID.randomUUID().toString();
        String enc = CredentialStoreImpl.encodePassword(password, 50_000);
        assertNotNull(enc);
        assertNotSame(password, enc);
    }

    @Test
    public void testCheckCorrectPassword() throws Exception {
        String password = UUID.randomUUID().toString();
        String stored = CredentialStoreImpl.encodePassword(password, 50_000);
        System.out.println(stored);
        assertTrue(CredentialStoreImpl.checkPassword(null, password, stored));
    }
    
    @Test
    public void testCheckIncorrectPassword() throws Exception {
        String password = UUID.randomUUID().toString();
        String stored = CredentialStoreImpl.encodePassword(password, 50_000);
        System.out.println(stored);
        assertFalse(CredentialStoreImpl.checkPassword(null, "nope!", stored));
    }

}
