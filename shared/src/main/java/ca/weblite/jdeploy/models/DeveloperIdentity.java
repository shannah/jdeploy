package ca.weblite.jdeploy.models;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DeveloperIdentity {
    private String name;
    private String organization;
    private String city;
    private String countryCode;
    private String identityUrl;
    private byte[] signature;
    private PublicKey publicKey;
    private List<String> aliasUrls = new ArrayList<>();
    private String websiteURL;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentityUrl() {
        return identityUrl;
    }

    public void setIdentityUrl(String identityUrl) {
        this.identityUrl = identityUrl;
    }

    public byte[] getSignature() {
        return signature;
    }

    public void setSignature(byte[] signature) {
        this.signature = signature;
    }


    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(PublicKey publicKey) {
        this.publicKey = publicKey;
    }

    public void addAliasUrl(String url) {
        aliasUrls.add(url);
    }

    public Collection<String> getAliasUrls() {
        ArrayList<String> out = new ArrayList<>();
        out.addAll(aliasUrls);
        return out;
    }

    public boolean hasAlias(String url) {
        return aliasUrls.contains(url);
    }


    public boolean matchesUrl(String url) {
        return url.equals(identityUrl) || aliasUrls.contains(url);
    }

    public String getWebsiteURL() {
        return websiteURL;
    }

    public void setWebsiteURL(String websiteURL) {
        this.websiteURL= websiteURL;
    }


    public String getOrganization() {
        return organization;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public DeveloperIdentity copyTo(DeveloperIdentity identity) {
        if (identity == null) {
            identity = new DeveloperIdentity();
        }
        identity.setOrganization(organization);
        identity.setIdentityUrl(identityUrl);
        identity.setCountryCode(countryCode);
        identity.setCity(city);
        identity.setName(name);
        identity.setPublicKey(publicKey);
        identity.setSignature(signature);
        identity.setWebsiteURL(websiteURL);
        identity.aliasUrls.addAll(aliasUrls);
        return identity;
    }
}
