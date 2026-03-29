package com.finsight.agent.repository;

import com.finsight.agent.model.AgentResult;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentResultRepository extends MongoRepository<AgentResult, String> {

    Optional<AgentResult> findByDocumentId(String documentId);
}
