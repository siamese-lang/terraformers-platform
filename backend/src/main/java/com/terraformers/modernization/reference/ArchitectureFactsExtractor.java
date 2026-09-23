package com.terraformers.modernization.reference;

import com.terraformers.modernization.storage.ObjectContent;

public interface ArchitectureFactsExtractor {

    ArchitectureRetrievalFacts extract(ObjectContent source);
}
