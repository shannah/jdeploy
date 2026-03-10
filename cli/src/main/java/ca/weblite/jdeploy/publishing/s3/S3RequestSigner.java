package ca.weblite.jdeploy.publishing.s3;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Signs S3 requests using AWS Signature Version 4.
 * This avoids a dependency on the AWS SDK.
 */
class S3RequestSigner {

    private final String accessKey;
    private final String secretKey;
    private final String region;

    S3RequestSigner(String accessKey, String secretKey, String region) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
    }

    HttpURLConnection createSignedPutRequest(
            String urlStr, String host, String key, byte[] payload, String contentType
    ) throws IOException {
        try {
            Date now = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateStamp = dateFormat.format(now);

            SimpleDateFormat amzDateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            amzDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String amzDate = amzDateFormat.format(now);

            String payloadHash = sha256Hex(payload);

            String canonicalUri = "/" + key;
            String canonicalQuerystring = "";
            String canonicalHeaders = "content-type:" + contentType + "\n"
                    + "host:" + host + "\n"
                    + "x-amz-content-sha256:" + payloadHash + "\n"
                    + "x-amz-date:" + amzDate + "\n";
            String signedHeaders = "content-type;host;x-amz-content-sha256;x-amz-date";

            String canonicalRequest = "PUT\n" + canonicalUri + "\n" + canonicalQuerystring + "\n"
                    + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;

            String credentialScope = dateStamp + "/" + region + "/s3/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + credentialScope + "\n"
                    + sha256Hex(canonicalRequest.getBytes("UTF-8"));

            byte[] signingKey = getSignatureKey(secretKey, dateStamp, region, "s3");
            String signature = hmacSha256Hex(signingKey, stringToSign);

            String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope
                    + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Host", host);
            conn.setRequestProperty("x-amz-date", amzDate);
            conn.setRequestProperty("x-amz-content-sha256", payloadHash);
            conn.setRequestProperty("Authorization", authorization);
            conn.setRequestProperty("Content-Length", String.valueOf(payload.length));

            return conn;
        } catch (Exception e) {
            throw new IOException("Failed to sign S3 request", e);
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(data));
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes("UTF-8"));
    }

    private static String hmacSha256Hex(byte[] key, String data) throws Exception {
        return bytesToHex(hmacSha256(key, data));
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String region, String service)
            throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes("UTF-8");
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "aws4_request");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }
}
