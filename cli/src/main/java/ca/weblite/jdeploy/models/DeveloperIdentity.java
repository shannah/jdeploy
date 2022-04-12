package ca.weblite.jdeploy.models;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DeveloperIdentity {
    private String name;
    private String identityUrl;
    private byte[] signature;
    private PublicKey publicKey;
    private List<String> aliasUrls = new ArrayList<>();

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

}
