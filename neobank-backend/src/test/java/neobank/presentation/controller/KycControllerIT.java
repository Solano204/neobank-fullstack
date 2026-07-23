package neobank.presentation.controller;

import neobank.domain.entity.KycDocument;
import neobank.domain.entity.User;
import neobank.domain.enums.DocumentType;
import neobank.domain.enums.KycStatus;
import neobank.domain.repository.KycDocumentRepository;
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

import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class KycControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KycDocumentRepository kycDocumentRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CognitoAdapter cognitoAdapter;

    @MockBean
    private S3Adapter s3Adapter;

    @MockBean
    private SesAdapter sesAdapter;

    private User user;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.builder()
                .cognitoUserId("cognito-kyc-it")
                .email("kyc-it@neobank.mx")
                .fullName("Kyc IT")
                .phone("+525512345678")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
    }

    private UsernamePasswordAuthenticationToken authenticatedAs(User user) {
        return new UsernamePasswordAuthenticationToken(
                UserPrincipal.create(user), null, Collections.emptyList());
    }

    @Test
    void rejectsRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/kyc/status")).andExpect(status().isForbidden());
    }

    @Test
    void getUploadUrl_validRequest_returnsAPresignedUrl() throws Exception {
        when(s3Adapter.generatePresignedUploadUrl(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn("https://s3.example.com/presigned");

        mockMvc.perform(get("/api/kyc/upload-url")
                        .with(authentication(authenticatedAs(user)))
                        .param("fileName", "selfie.jpg")
                        .param("documentType", "SELFIE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").value("https://s3.example.com/presigned"))
                .andExpect(jsonPath("$.data.documentType").value("SELFIE"));
    }

    @Test
    void getUploadUrl_unsupportedExtension_returns400NotA500() throws Exception {
        mockMvc.perform(get("/api/kyc/upload-url")
                        .with(authentication(authenticatedAs(user)))
                        .param("fileName", "selfie.png")
                        .param("documentType", "SELFIE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void verifyDocument_persistsAProcessingDocument() throws Exception {
        mockMvc.perform(post("/api/kyc/verify")
                        .with(authentication(authenticatedAs(user)))
                        .param("documentType", "SELFIE")
                        .param("s3Key", "kyc-docs/some-key.jpg"))
                .andExpect(status().isOk());

        java.util.List<KycDocument> documents = kycDocumentRepository.findByUserId(user.getId());
        org.assertj.core.api.Assertions.assertThat(documents).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(documents.get(0).getStatus()).isEqualTo(KycStatus.PROCESSING);
    }

    @Test
    void getKycStatus_noDocumentsYet_returnsAllFourTypesAsPendingWithZeroProgress() throws Exception {
        mockMvc.perform(get("/api/kyc/status").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.documents", hasSize(4)))
                .andExpect(jsonPath("$.data.verificationProgress").value(0));
    }

    @Test
    void getKycStatus_verifiedSelfieAndIdFront_reportsHundredPercentProgress() throws Exception {
        kycDocumentRepository.save(KycDocument.builder().user(user).documentType(DocumentType.SELFIE).s3Key("k1").status(KycStatus.VERIFIED).build());
        kycDocumentRepository.save(KycDocument.builder().user(user).documentType(DocumentType.ID_FRONT).s3Key("k2").status(KycStatus.VERIFIED).build());

        mockMvc.perform(get("/api/kyc/status").with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verificationProgress").value(100));
    }

    @Test
    void deleteDocument_ownedByCaller_removesItAndTheS3Object() throws Exception {
        KycDocument document = kycDocumentRepository.save(
                KycDocument.builder().user(user).documentType(DocumentType.SELFIE).s3Key("kyc-docs/to-delete.jpg").status(KycStatus.PROCESSING).build());

        mockMvc.perform(delete("/api/kyc/documents/{id}", document.getId()).with(authentication(authenticatedAs(user))))
                .andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(kycDocumentRepository.findById(document.getId())).isEmpty();
        org.mockito.Mockito.verify(s3Adapter).deleteObject("kyc-docs/to-delete.jpg");
    }

    @Test
    void deleteDocument_belongingToAnotherUser_returns404_andIsNotDeleted() throws Exception {
        User stranger = userRepository.save(User.builder()
                .cognitoUserId("cognito-stranger-kyc")
                .email("stranger-kyc@neobank.mx")
                .fullName("Stranger")
                .phone("+525500000000")
                .country("MX")
                .kycStatus(KycStatus.PENDING)
                .build());
        KycDocument document = kycDocumentRepository.save(
                KycDocument.builder().user(user).documentType(DocumentType.SELFIE).s3Key("kyc-docs/protected.jpg").status(KycStatus.PROCESSING).build());

        mockMvc.perform(delete("/api/kyc/documents/{id}", document.getId()).with(authentication(authenticatedAs(stranger))))
                .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(kycDocumentRepository.findById(document.getId())).isPresent();
    }
}
