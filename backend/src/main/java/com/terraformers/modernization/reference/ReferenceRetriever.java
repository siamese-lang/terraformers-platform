package com.terraformers.modernization.reference;

import java.util.List;

public interface ReferenceRetriever {

    List<ReferenceDocument> retrieve(ReferenceQuery query);

    /** Final-draft documentary support, separate from the bounded generation context. */
    default List<ReferenceDocument> retrieveOfficialDocumentation(String resourceType) {
        return retrieve(new ReferenceQuery("Official AWS provider documentation for " + resourceType,
                List.of(resourceType), 1, true));
    }
}
