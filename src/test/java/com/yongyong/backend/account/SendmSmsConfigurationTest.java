package com.yongyong.backend.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
        "app.sms.provider=sendm",
        "app.sms.sendm.user-id=test-user",
        "app.sms.sendm.api-key=test-key",
        "app.sms.sendm.sender=01012345678",
        "spring.datasource.url=jdbc:h2:mem:sendm_context;MODE=MySQL"
})
@TestPropertySource(locations = "classpath:account-test.properties")
class SendmSmsConfigurationTest {
    @Autowired SmsSender sms;

    @Test
    void startsWithTheRealSendmSenderSelected() {
        assertThat(sms).isInstanceOf(SendmSmsSender.class);
        assertThat(sms.available()).isTrue();
    }
}
