package neobank.domain.repository;

import neobank.domain.entity.KycDocument;
import neobank.domain.entity.User;
import neobank.domain.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {
    List<KycDocument> findByUser(User user);
    List<KycDocument> findByUserId(UUID userId);
    Optional<KycDocument> findByUserAndDocumentType(User user, DocumentType documentType);
    Optional<KycDocument> findByIdAndUserId(UUID id, UUID userId);
}