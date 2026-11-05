package com.yongyong.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@TestPropertySource(locations = "classpath:account-test.properties")
@Transactional
class AccountServiceTest {
    @Autowired AccountService accounts;
    @Autowired PhoneAuthService phoneAuth;
    @Autowired JdbcTemplate db;

    private Jwt token(String subject) {
        return Jwt.withTokenValue(subject).header("alg", "RS256")
                .issuer("https://id.example.test").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(3600)).build();
    }

    @Test
    void phoneCodeRequestFailsClosedWithoutSmsProvider() {
        assertThatThrownBy(() -> phoneAuth.requestCode("01012345678"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM phone_verifications", Integer.class)).isZero();
    }

    @Test
    void ownerAndGuardianAccessRequiresConsentAndActiveLink() {
        Jwt elder = token("elder-1");
        Jwt guardian = token("guardian-1");
        String elderUserId = accounts.login(elder, "ELDER").userId();
        accounts.login(guardian, "GUARDIAN");
        String elderId = accounts.createProfile(elder, "김도영", 1950, "용인시",
                new AccountService.MobilityUpdate("CANE", true, null, null)).elderId();

        String storedName = db.queryForObject("SELECT display_name_enc FROM elder_profiles WHERE elder_id = ?",
                String.class, elderId);
        assertThat(storedName).doesNotContain("김도영");
        assertThat(accounts.profile(elder, elderId).name()).isEqualTo("김도영");
        assertThat(accounts.profile(elder, elderId).mobility().stairsDifficult()).isTrue();
        assertThatThrownBy(() -> accounts.profile(guardian, elderId)).isInstanceOf(ResponseStatusException.class);

        accounts.recordConsent(elder, elderId, "CAREGIVER_SHARING", "v1", true);
        String code = accounts.issueLinkCode(elder, elderId).code();
        String linkId = accounts.connectGuardian(guardian, code, "자녀").linkId();
        assertThat(accounts.profile(guardian, elderId).homeAddress()).isEqualTo("용인시");
        assertThatThrownBy(() -> accounts.connectGuardian(guardian, code, "자녀"))
                .isInstanceOf(ResponseStatusException.class);

        accounts.recordConsent(elder, elderId, "CAREGIVER_SHARING", "v1", false);
        assertThatThrownBy(() -> accounts.profile(guardian, elderId)).isInstanceOf(ResponseStatusException.class);
        accounts.disconnectGuardian(elder, linkId);
        assertThat(db.queryForObject("SELECT status FROM guardian_links WHERE link_id = ?", String.class, linkId))
                .isEqualTo("REVOKED");
    }

    @Test
    void healthReplacementAndNotificationAreScopedToAccount() {
        Jwt elder = token("elder-2");
        Jwt other = token("elder-3");
        String userId = accounts.login(elder, "ELDER").userId();
        accounts.login(other, "ELDER");
        String elderId = accounts.createProfile(elder, "테스트", 1951, null, null).elderId();
        accounts.recordConsent(elder, elderId, "HEALTH", "v1", true);
        accounts.replaceHealth(elder, elderId, List.of(new AccountService.HealthCondition("DIZZINESS", "어지러움")));
        assertThat(accounts.profile(elder, elderId).healthConditions()).hasSize(1);
        accounts.replaceHealth(elder, elderId, List.of());
        assertThat(accounts.profile(elder, elderId).healthConditions()).isEmpty();

        String notificationId = UUID.randomUUID().toString();
        db.update("INSERT INTO notifications(notification_id, trip_id, recipient_user_id, channel, type, status, sent_at) " +
                        "VALUES (?, ?, ?, 'PUSH', 'DEPARTURE', 'SENT', CURRENT_TIMESTAMP(6))",
                notificationId, UUID.randomUUID().toString(), userId);
        assertThat(accounts.notifications(elder)).hasSize(1);
        assertThat(accounts.notifications(other)).isEmpty();
        assertThatThrownBy(() -> accounts.markNotificationRead(other, notificationId))
                .isInstanceOf(ResponseStatusException.class);
        accounts.markNotificationRead(elder, notificationId);
        assertThat(accounts.notifications(elder).get(0).readAt()).isNotNull();
    }
}
