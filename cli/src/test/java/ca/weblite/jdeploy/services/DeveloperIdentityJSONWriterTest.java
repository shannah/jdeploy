package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.DeveloperIdentity;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Base64;

import static ca.weblite.jdeploy.helpers.KeyPairGenerator.generateKeyPair;
import static ca.weblite.jdeploy.tests.helpers.DeveloperIdentityTestHelper.createMockIdentity;
import static org.junit.jupiter.api.Assertions.*;

class DeveloperIdentityJSONWriterTest {

    @Test
    public void testWriteDeveloperIdentity() throws Exception {
        DeveloperIdentity identity = createMockIdentity();
        DeveloperIdentityJSONWriter writer = new DeveloperIdentityJSONWriter();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        KeyPair keyPair = generateKeyPair();
        writer.writeIdentity(identity, keyPair, baos);

        JSONObject json = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
        assertEquals(identity.getName(), json.getString("name"));
        assertEquals(identity.getIdentityUrl(), json.getString("identityUrl"));
        assertEquals(identity.getCity(), json.getString("city"));
        assertEquals(identity.getCountryCode(), json.getString("countryCode"));
        assertEquals(identity.getOrganization(), json.getString("organization"));
        assertArrayEquals(identity.getSignature(), Base64.getDecoder().decode(json.getString("signature")));

    }

    @Test
    public void testWriteThenReadDeveloperIdentity() throws Exception {
        DeveloperIdentity identity = createMockIdentity();
        DeveloperIdentityJSONWriter writer = new DeveloperIdentityJSONWriter();
        DeveloperIdentityJSONReader reader = new DeveloperIdentityJSONReader();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        KeyPair keyPair = generateKeyPair();
        writer.writeIdentity(identity, keyPair, baos);


        JSONObject json = new JSONObject(new String(baos.toByteArray(), StandardCharsets.UTF_8));
        DeveloperIdentity read = new DeveloperIdentity();
        reader.loadIdentityFromJSON(read, json, identity.getIdentityUrl());

        assertEquals(identity.getName(), read.getName());
        assertEquals(identity.getIdentityUrl(), read.getIdentityUrl());
        assertEquals(identity.getCity(), read.getCity());
        assertEquals(identity.getCountryCode(), read.getCountryCode());
        assertEquals(identity.getOrganization(), read.getOrganization());
        assertArrayEquals(identity.getSignature(), read.getSignature());
        assertArrayEquals(identity.getPublicKey().getEncoded(), read.getPublicKey().getEncoded());

    }

}