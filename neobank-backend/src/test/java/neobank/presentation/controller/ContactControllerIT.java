package neobank.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import neobank.domain.entity.Account;
import neobank.domain.entity.Contact;
import neobank.domain.entity.User;
import neobank.domain.enums.AccountStatus;
import neobank.domain.enums.AccountType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.AccountRepository;
import neobank.domain.repository.ContactRepository;
import neobank.domain.repository.UserRepository;
import neobank.infrastructure.adapter.cognito.CognitoAdapter;
import neobank.infrastructure.adapter.s3.S3Adapter;
import neobank.infrastructure.adapter.ses.SesAdapter;
import neobank.infrastructure.security.JwtTokenProvider;
import neobank.infrastructure.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class ContactControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ContactRepository contactRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CognitoAdapter cognitoAdapter;

    @MockBean
    private S3Adapter s3Adapter;

    @MockBean
    private SesAdapter sesAdapter;

    private User owner;
    private User recipient;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(User.builder()
                .cognitoUserId("cognito-contact-it")
                .email("contact-it@neobank.mx")
                .fullName("Contact IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        recipient = userRepository.save(User.builder()
                .cognitoUserId("cognito-contact-recipient")
                .email("recipient@neobank.mx")
                .fullName("Recipient Person")
                .phone("+525511111111")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());

        accountRepository.save(Account.builder()
                .user(recipient)
                .accountNumber("999988887777666655")
                .accountType(AccountType.CHECKING)
                .balance(new BigDecimal("0.00"))
                .availableBalance(new BigDecimal("0.00"))
                .currency("MXN")
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/contacts"))
                .andExpect(status().isForbidden());
    }

    @Test
    void listsNoContactsInitially() throws Exception {
        mockMvc.perform(get("/api/contacts").with(authentication(authenticatedAs(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void addContact_validAccount_persistsAndReturnsIt() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .with(authentication(authenticatedAs(owner)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("accountNumber", "999988887777666655");
                            put("nickname", "Best friend");
                        }})))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Recipient Person"))
                .andExpect(jsonPath("$.data.nickname").value("Best friend"));

        mockMvc.perform(get("/api/contacts").with(authentication(authenticatedAs(owner))))
                .andExpect(jsonPath("$.data", hasSize(1)));
    }

    @Test
    void addContact_accountThatDoesNotExist_returns404() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .with(authentication(authenticatedAs(owner)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("accountNumber", "000000000000000000");
                        }})))
                .andExpect(status().isNotFound());
    }

    @Test
    void addContact_malformedAccountNumber_returns400ValidationErrorNot500() throws Exception {
        mockMvc.perform(post("/api/contacts")
                        .with(authentication(authenticatedAs(owner)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("accountNumber", "not-18-digits");
                        }})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void addContact_duplicateAccount_returns400BusinessError() throws Exception {
        contactRepository.save(Contact.builder()
                .user(owner)
                .accountNumber("999988887777666655")
                .contactName("Recipient Person")
                .favorite(false)
                .build());

        mockMvc.perform(post("/api/contacts")
                        .with(authentication(authenticatedAs(owner)))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("accountNumber", "999988887777666655");
                        }})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTACT_EXISTS"));
    }

    @Test
    void deleteContact_ownedByCaller_removesIt() throws Exception {
        Contact contact = contactRepository.save(Contact.builder()
                .user(owner)
                .accountNumber("999988887777666655")
                .contactName("Recipient Person")
                .favorite(false)
                .build());

        mockMvc.perform(delete("/api/contacts/{id}", contact.getId()).with(authentication(authenticatedAs(owner))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(contactRepository.findById(contact.getId())).isEmpty();
    }

    @Test
    void deleteContact_belongingToAnotherUser_doesNotDeleteIt() throws Exception {
        Contact contact = contactRepository.save(Contact.builder()
                .user(owner)
                .accountNumber("999988887777666655")
                .contactName("Recipient Person")
                .favorite(false)
                .build());

        mockMvc.perform(delete("/api/contacts/{id}", contact.getId()).with(authentication(authenticatedAs(recipient))))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(contactRepository.findById(contact.getId())).isPresent();
    }

    @Test
    void updateFavorite_marksAContactAsFavorite() throws Exception {
        Contact contact = contactRepository.save(Contact.builder()
                .user(owner)
                .accountNumber("999988887777666655")
                .contactName("Recipient Person")
                .favorite(false)
                .build());

        mockMvc.perform(put("/api/contacts/{id}/favorite", contact.getId())
                        .with(authentication(authenticatedAs(owner)))
                        .param("favorite", "true"))
                .andExpect(status().isOk());

        Contact updated = contactRepository.findById(contact.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(updated.getFavorite()).isTrue();
    }
}
