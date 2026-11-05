package com.yongyong.backend.account;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@Primary
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "ncloud")
public class NcloudSmsSender extends SmsSender {
    @Value("${app.sms.ncloud.service-id:}") private String serviceId;
    @Value("${app.sms.ncloud.access-key:}") private String accessKey;
    @Value("${app.sms.ncloud.secret-key:}") private String secretKey;
    @Value("${app.sms.ncloud.sender:}") private String sender;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override
    public boolean available() {
        return serviceId.matches("[A-Za-z0-9:_-]+") && !accessKey.isBlank()
                && !secretKey.isBlank() && sender.matches("0[0-9]{9,10}");
    }

    @Override
    public void send(String phoneNumber, String message) {
        if (!available()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is not configured");
        String path = "/sms/v2/services/" + serviceId + "/messages";
        String timestamp = Long.toString(System.currentTimeMillis());
        String localNumber = "0" + phoneNumber.substring(3);
        String body = "{\"type\":\"SMS\",\"contentType\":\"COMM\",\"countryCode\":\"82\"," +
                "\"from\":\"" + sender + "\",\"content\":\"" + message +
                "\",\"messages\":[{\"to\":\"" + localNumber + "\"}]}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://sens.apigw.ntruss.com" + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("x-ncp-apigw-timestamp", timestamp)
                    .header("x-ncp-iam-access-key", accessKey)
                    .header("x-ncp-apigw-signature-v2", signature(path, timestamp))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 202)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider rejected the request");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMS provider is unavailable");
        }
    }

    private String signature(String path, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String value = "POST " + path + "\n" + timestamp + "\n" + accessKey;
        return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
