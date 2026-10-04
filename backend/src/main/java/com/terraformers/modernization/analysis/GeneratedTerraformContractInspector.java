package com.terraformers.modernization.analysis;

import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class GeneratedTerraformContractInspector {

    private static final Pattern DEPLOYABLE = Pattern.compile(
            "(?m)^\\s*(resource|module)\\s+\"([^\"]+)\"(?:\\s+\"[^\"]+\")?\\s*\\{");
    private final AwsProviderSchemaCatalog catalog;

    public GeneratedTerraformContractInspector(@Lazy AwsProviderSchemaCatalog catalog) {
        this.catalog = catalog;
    }

    public void inspect(String terraform) {
        for (String type : resourceTypes(terraform)) {
            if (!type.startsWith("aws_") || !catalog.contains(type)) {
                throw new GeneratedTerraformContractViolation(
                        GeneratedTerraformContractViolation.Reason.RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT);
            }
        }
    }

    /**
     * Extracts deployable resource types through the same parser used by the production contract
     * inspector. The method intentionally does not validate provider membership so evidence-quality
     * assessment can distinguish an unknown generated resource from a resource that lacks selected
     * evidence.
     */
    public List<String> resourceTypes(String terraform) {
        String source = terraform == null ? "" : terraform;
        Matcher matcher = DEPLOYABLE.matcher(source);
        Set<String> resourceTypes = new LinkedHashSet<>();
        while (matcher.find()) {
            if (matcher.group(1).equals("module")) {
                throw new GeneratedTerraformContractViolation(
                        GeneratedTerraformContractViolation.Reason.MODULE_BLOCK);
            }
            resourceTypes.add(matcher.group(2));
        }
        return List.copyOf(resourceTypes);
    }
}
