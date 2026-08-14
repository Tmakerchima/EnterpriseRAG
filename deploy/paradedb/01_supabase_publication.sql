-- Apply on the reviewed primary database; never run automatically at application startup.
CREATE PUBLICATION enterprise_rag_publication
    FOR TABLE enterprise_documents, enterprise_chunks, enterprise_corpora;

-- Production replication slots, users and network allowlists are platform-owned changes.
