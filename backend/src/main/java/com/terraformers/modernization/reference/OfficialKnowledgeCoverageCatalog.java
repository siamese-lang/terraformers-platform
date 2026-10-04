package com.terraformers.modernization.reference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Versioned A7-0 coverage metadata; this is deliberately independent of retrieval results. */
@Component
public final class OfficialKnowledgeCoverageCatalog {
    public static final String RESOURCE = "quality/aws-provider-5.100.0-official-knowledge-coverage.json";
    private static final String CONTRACT = "aws-provider-official-knowledge-coverage-v1";
    private static final String COMMIT = "f7a3b98da589ab1d52756b0dcee0dbf2de83d635";
    private final Set<String> gaps;

    public OfficialKnowledgeCoverageCatalog(ObjectMapper mapper) {
        try (InputStream input = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = mapper.readTree(input);
            JsonNode registry = root.path("managedResourceRegistry");
            JsonNode docs = root.path("officialResourceDocumentation");
            TreeSet<String> loaded = new TreeSet<>();
            root.path("missingOfficialDocumentationResourceTypes").forEach(value -> loaded.add(value.asText()));
            if (!CONTRACT.equals(root.path("contractVersion").asText())
                    || !"5.100.0".equals(root.path("providerVersion").asText())
                    || !COMMIT.equals(root.path("providerSourceCommit").asText())
                    || registry.path("managedResourceCount").asInt() != 1526
                    || docs.path("documentedManagedResourceCount").asInt() != 1514
                    || docs.path("missingManagedResourceCount").asInt() != 12
                    || loaded.size() != 12) {
                throw new IllegalStateException("AWS provider official-knowledge coverage manifest is invalid");
            }
            gaps = Set.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("AWS provider official-knowledge coverage manifest cannot be loaded", exception);
        }
    }

    public boolean isAvailable(String resourceType, AwsProviderSchemaCatalog schemaCatalog) {
        return schemaCatalog.contains(resourceType) && !gaps.contains(resourceType);
    }

    public Set<String> availableFor(Collection<String> resourceTypes, AwsProviderSchemaCatalog schemaCatalog) {
        TreeSet<String> available = new TreeSet<>();
        if (resourceTypes != null) {
            resourceTypes.stream().filter(type -> isAvailable(type, schemaCatalog)).forEach(available::add);
        }
        return Set.copyOf(available);
    }

    public Set<String> gaps() {
        return gaps;
    }
}
