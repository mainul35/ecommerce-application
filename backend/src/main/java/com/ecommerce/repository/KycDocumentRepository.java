package com.ecommerce.repository;

import com.ecommerce.model.KycDocument;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface KycDocumentRepository extends ReactiveCrudRepository<KycDocument, UUID> {

    Flux<KycDocument> findByCaseId(UUID caseId);

    Mono<KycDocument> findByCaseIdAndDocType(UUID caseId, KycDocument.KycDocType docType);

    Mono<Long> countByCaseId(UUID caseId);

    Mono<Void> deleteByCaseId(UUID caseId);
}
