package com.terraformers.modernization.analysis;

import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;

@Component
public class GeneratedTerraformContractInspector {

    private static final Pattern DEPLOYABLE = Pattern.compile(
            "(?m)^\\s*(resource|module)\\s+\"([^\"]+)\"(?:\\s+\"[^\"]+\")?\\s*\\{");
    private final AwsProviderSchemaCatalog catalog;

    public GeneratedTerraformContractInspector(@Lazy AwsProviderSchemaCatalog catalog) {
        this.catalog = catalog;
    }

    public void inspect(String terraform, AwsProviderSchemaEvidence evidence) {
        String source = terraform == null ? "" : terraform;
        Matcher matcher = DEPLOYABLE.matcher(source);
        while (matcher.find()) {
            if (matcher.group(1).equals("module")) {
                throw new GeneratedTerraformContractViolation(\n                        GeneratedTerraformContractViolation.Reason.MODULE_BLOCK);
            }
            String type = matcher.group(2);
            if (!type.startsWith("aws_") || !catalog.contains(type)) {
                throw new GeneratedTerraformContractViolation(\n                        GeneratedTerraformContractViolation.Reason.RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT);
            }
            if (!evidence.covers(type)) {
                throw new GeneratedTerraformContractViolation(\n                        GeneratedTerraformContractViolation.Reason.RESOURCE_OUTSIDE_REQUEST_SCHEMA_ENVELOPE);
            }
        }
    }
}
